import hmac
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta

from pydantic import BaseModel

import numpy as np
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

from app import analysis, monitor, render, report, water, weather
from app.geometry import CountryIndex, check_area, parse_polygon, to_utm
from app.schemas import (AnalyzeRequest, AnalyzeResponse, Period, Point, Recommendation, Rejected,
                         Upstream, Zones)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [%(name)s] %(message)s")
logging.getLogger("rasterio.session").setLevel(logging.WARNING)   # har COG uchun "boto3 not available"
logging.getLogger("httpx").setLevel(logging.WARNING)
log = logging.getLogger("satellite")

INTERNAL_KEY = os.environ["INTERNAL_KEY"]           # yo'q bo'lsa xizmat ishga tushmasin
if len(INTERNAL_KEY) < 16:
    raise RuntimeError("INTERNAL_KEY kamida 16 belgi")

app = FastAPI(title="agro satellite-service", docs_url=None, redoc_url=None, openapi_url=None)
countries = CountryIndex()
_loader = ThreadPoolExecutor(max_workers=4, thread_name_prefix="prev-year")


@app.middleware("http")
async def internal_key(request: Request, call_next):
    # Middleware — body validatsiyasidan OLDIN. Endpoint parametrida bo'lsa FastAPI avval
    # tanani tekshiradi: kalitsiz so'rov 401 emas 422 oladi va API tuzilishini bilib oladi
    if request.url.path != "/health":
        key = request.headers.get("x-internal-key")
        if key is None or not hmac.compare_digest(key.encode(), INTERNAL_KEY.encode()):
            return JSONResponse(status_code=401, content={"code": "UNAUTHORIZED"})
    return await call_next(request)


@app.exception_handler(Rejected)
def rejected(_: Request, e: Rejected):
    return JSONResponse(status_code=e.status, content={"code": e.code, "params": e.params})


@app.exception_handler(Upstream)
def upstream(_: Request, e: Upstream):
    log.warning("upstream: %s", e)
    return JSONResponse(status_code=502, content={"code": "UPSTREAM"})


@app.get("/health")
def health():
    return {"status": "ok", "countries": countries.tree is not None}


@app.post("/analyze", response_model=AnalyzeResponse, response_model_by_alias=True)
def analyze(req: AnalyzeRequest):
    # def, async EMAS: raster ishi bloklovchi — FastAPI thread pool'da ishlatadi
    t0 = time.monotonic()
    if req.season_end <= req.season_start:
        raise Rejected(422, "BAD_SEASON")

    poly = parse_polygon(req.geometry)
    poly_utm, epsg = to_utm(poly)
    area_ha = poly_utm.area / 10_000
    check_area(area_ha)
    c = poly.centroid

    # Joriy va o'tgan yil parallel: ikkalasi ham tarmoq (COG o'qish) — vaqt deyarli ikki barobar qisqaradi
    year = timedelta(days=365)
    prev_future = (_loader.submit(analysis.load_series, poly, poly_utm, epsg,
                                  req.season_start - year, req.season_end - year)
                   if req.compare_previous_year else None)
    cur = analysis.load_series(poly, poly_utm, epsg, req.season_start, req.season_end)
    if cur is None or len(cur.dates) < 3:
        if prev_future:
            prev_future.cancel()
        raise Rejected(404, "NO_SCENES")
    ndvi, ndmi = analysis.medians(cur)
    rel, z = analysis.zones(cur)
    season = analysis.growing_season(cur.dates, ndvi)
    stress = analysis.stress_periods(cur.dates, ndvi, ndmi, season)
    t_cur = time.monotonic()

    prev, prev_ndvi = None, None
    if prev_future:
        try:
            prev = prev_future.result()
        except Upstream as e:                        # o'tgan yil — qo'shimcha; usiz ham hisobot bor
            log.warning("o'tgan yil yuklanmadi: %s", e)
        if prev is not None and len(prev.dates) >= 3:
            prev_ndvi, _ = analysis.medians(prev)
        else:
            prev = None
    t_prev = time.monotonic()

    water = None
    if season:
        days, et0, rain = weather.daily(c.y, c.x, req.season_start, req.season_end)
        water = weather.water_need(cur.dates, ndvi, season, days, et0, rain)

    peak_date, peak = analysis.season_peak(cur.dates, ndvi, season)
    prev_peak = None
    if prev_ndvi is not None:
        # O'sha mavsum bir yil oldin (±30 kun) — 2 hosilli dalada boshqa ekin bilan solishtirmaslik
        ref = season or Period(start=req.season_start, end=req.season_end)
        win = Period(start=ref.start - year - timedelta(days=30), end=ref.end - year + timedelta(days=30))
        vals = [float(v) for d, v in zip(prev.dates, prev_ndvi) if win.start <= d <= win.end and not np.isnan(v)]
        prev_peak = max(vals) if vals else None

    resp = AnalyzeResponse(
        field_id=req.field_id,
        area_ha=round(area_ha, 2),
        country_code=countries.lookup(c.x, c.y),
        centroid_lat=round(c.y, 6), centroid_lon=round(c.x, 6),
        scenes_used=len(cur.dates), scenes_rejected_cloud=cur.rejected,
        zones=Zones(**z),
        peak_ndvi=round(peak, 3), peak_date=peak_date,
        prev_year_peak_ndvi=round(prev_peak, 3) if prev_peak is not None else None,
        growing_season=season, stress_periods=stress, water=water,
        timeseries=[Point(date=d, ndvi=round(float(v), 3), ndmi=round(float(m), 3))
                    for d, v, m in zip(cur.dates, ndvi, ndmi) if not np.isnan(v)],
        recommendations=recommend(z, season, stress, peak, prev_peak, len(cur.dates)),
        map_png_base64=render.zone_map(rel, cur.x, cur.y, poly_utm),
        chart_png_base64=render.season_chart(cur.dates, ndvi, prev.dates if prev else None,
                                             prev_ndvi, season, stress),
    )
    log.info("field=%s epsg=%s area=%.2fha scenes=%d/%d prev=%s load=%.1fs prev_wait=%.1fs total=%.1fs",
             req.field_id, epsg, area_ha, len(cur.dates), cur.rejected,
             len(prev.dates) if prev else "-", t_cur - t0, t_prev - t_cur, time.monotonic() - t0)
    return resp


@app.post("/report/pdf")
def report_pdf(req: report.PdfRequest):
    """Tayyor matn + rasmlar → PDF (A4). Kalit middleware'da tekshiriladi."""
    return Response(content=report.render_pdf(req), media_type="application/pdf")


@app.post("/water/balance", response_model=water.BalanceResponse)
def water_balance(req: water.BalanceRequest):
    return water.balance(req)


@app.post("/soil", response_model=water.SoilResponse)
def soil(req: water.SoilRequest):
    return water.soil(req)


class LatestNdviRequest(BaseModel):
    geometry: dict


class LatestNdviResponse(BaseModel):
    ndvi: float
    date: date
    scenes: int


@app.post("/ndvi/latest", response_model=LatestNdviResponse)
def ndvi_latest(req: LatestNdviRequest):
    """So'nggi 30 kundagi oxirgi ochiq sana — sug'orish modeli uchun Kc (har hafta yangilanadi)."""
    poly = parse_polygon(req.geometry)
    poly_utm, epsg = to_utm(poly)
    check_area(poly_utm.area / 10_000)
    end = date.today()
    s = analysis.load_series(poly, poly_utm, epsg, end - timedelta(days=30), end)
    if s is None or not s.dates:
        raise Rejected(404, "NO_SCENES")
    ndvi, _ = analysis.medians(s)
    return LatestNdviResponse(ndvi=round(float(ndvi[-1]), 3), date=s.dates[-1], scenes=len(s.dates))


@app.post("/monitor", response_model=monitor.MonitorResponse)
def monitor_check(req: monitor.MonitorRequest):
    """C3: since dan keyin yangi ochiq surat bo'lsa — oldingisi bilan piksel bo'yicha solishtirish."""
    poly = parse_polygon(req.geometry)
    poly_utm, epsg = to_utm(poly)
    check_area(poly_utm.area / 10_000)
    return monitor.check(req, poly, poly_utm, epsg)


def recommend(z: dict, season, stress, peak: float, prev_peak: float | None, scenes: int) -> list[Recommendation]:
    """Kod + parametr; matn Java'da fermer tilida. Muhimlik tartibida, ko'pi bilan 3 ta."""
    out: list[Recommendation] = []
    if season is None:
        return [Recommendation(code="NO_CROP_DETECTED")]
    for p in stress[:2]:
        out.append(Recommendation(code="WATER_STRESS", params={"from": p.start.isoformat(), "to": p.end.isoformat()}))
    if z["weak_pct"] >= 10:
        out.append(Recommendation(code="WEAK_ZONE", params={"weak_pct": z["weak_pct"], "side": z["weak_side"]}))
    if prev_peak and peak < prev_peak * 0.95:
        out.append(Recommendation(code="BELOW_LAST_YEAR", params={"pct": round((1 - peak / prev_peak) * 100)}))
    if z["weak_pct"] < 10 and z["good_pct"] >= 70:
        out.append(Recommendation(code="FIELD_UNIFORM"))
    if scenes < 8:
        out.append(Recommendation(code="LOW_CONFIDENCE", params={"scenes": scenes}))
    return out[:3]
