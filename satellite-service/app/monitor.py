"""C3: avtomatik monitoring — yangi Sentinel-2 surati chiqqanda dala oldingi ochiq surat bilan solishtiriladi.

Arzon yo'l birinchi: STAC qidiruvi (metadata) — `since` dan keyin surat yo'q bo'lsa COG o'qilmaydi.
Yangi surat bo'lsa: oxirgi ochiq sana (L) va undan ≥ MIN_GAP kun oldingi ochiq sana (R) — piksel bo'yicha ΔNDVI.
Qaror (ogohlantirish yuborish) — Java'da (chegaralar admin sozlamasida); bu yer faqat o'lchaydi.
"""
from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
from pydantic import BaseModel
from pystac_client import Client
from pystac_client.exceptions import APIError
from shapely.geometry import Polygon, mapping

from app import analysis, render
from app.schemas import Upstream

LOOKBACK_DAYS = 45            # R shu oyna ichidan qidiriladi (bulutli davr — 2–3 surat tushib qolishi mumkin)
MIN_GAP_DAYS = 4              # Sentinel-2 qaytish davri ~5 kun: ketma-ket kunlar (bir orbita) — o'zgarish emas


class MonitorRequest(BaseModel):
    geometry: dict
    since: date | None = None             # oxirgi ko'rilgan surat sanasi; null — birinchi tekshiruv
    drop: float = 0.10                    # piksel "pasaygan": ΔNDVI ≤ −drop
    map_min_pct: float = 10               # pasaygan qism shundan ko'p bo'lsa — o'zgarish xaritasi (PNG)


class MonitorResponse(BaseModel):
    new_scene: bool
    scene_date: date | None = None        # L — yangi ochiq surat (maydon nomi `date` bo'lsa tipni soya qiladi)
    ref_date: date | None = None          # R — solishtirilgan surat
    ndvi: float | None = None             # L dala medianasi
    ndvi_ref: float | None = None
    decline_pct: float | None = None      # ΔNDVI ≤ −drop bo'lgan piksellar ulushi, %
    decline_side: str | None = None       # N, NE, … CENTER
    improve_pct: float | None = None      # ΔNDVI ≥ +drop
    map_png_base64: str | None = None
    # Metadata bo'yicha eng yangi surat (dala ustida bulutli bo'lsa ham) — Java buni keyingi `since` qiladi,
    # aks holda bulutli surat har kuni "yangi" bo'lib, og'ir yo'l keyingi ochiq suratgacha takrorlanardi
    latest_seen: date | None = None


@dataclass
class _Pair:
    last: date
    ref: date
    d: np.ndarray                         # ΔNDVI (y, x), NaN — dala tashqarisi/bulut


def latest_scene(poly_wgs: Polygon, after: date) -> date | None:
    """Faqat metadata: `after` dan keyingi eng yangi surat sanasi (bulut ≤ 60% sahna bo'yicha)."""
    if after >= date.today():                         # bo'sh oraliq — Earth Search xato beradi (502 emas, "yangi yo'q")
        return None
    try:
        items = list(Client.open(analysis.EARTH_SEARCH).search(
            collections=[analysis.COLLECTION], intersects=mapping(poly_wgs),
            datetime=f"{(after + timedelta(days=1)).isoformat()}/{date.today().isoformat()}",
            query={"eo:cloud_cover": {"lt": 60}}, max_items=50,
        ).items())
    except APIError as e:
        raise Upstream(f"Earth Search: {e}") from e
    dates = [i.datetime.date() for i in items if i.datetime]
    return max(dates) if dates else None


def check(req: MonitorRequest, poly: Polygon, poly_utm: Polygon, epsg: int) -> MonitorResponse:
    today = date.today()
    seen = latest_scene(poly, req.since) if req.since is not None else None
    if req.since is not None and seen is None:
        return MonitorResponse(new_scene=False)
    s = analysis.load_series(poly, poly_utm, epsg, today - timedelta(days=LOOKBACK_DAYS), today)
    if s is None or not s.dates:
        return MonitorResponse(new_scene=False, latest_seen=seen)
    last_i = len(s.dates) - 1
    if req.since is not None and s.dates[last_i] <= req.since:
        return MonitorResponse(new_scene=False, latest_seen=seen)   # yangi surat bor, lekin dala ustida bulutli (SCL)
    ref_i = next((i for i in range(last_i - 1, -1, -1) if (s.dates[last_i] - s.dates[i]).days >= MIN_GAP_DAYS), None)
    last = s.ndvi.isel(time=last_i).values
    ndvi_last = float(np.nanmedian(last))
    if ref_i is None:                                 # solishtiradigan surat yo'q — faqat holat
        return MonitorResponse(new_scene=True, scene_date=s.dates[last_i], ndvi=round(ndvi_last, 3), latest_seen=seen)
    ref = s.ndvi.isel(time=ref_i).values
    d = last - ref
    valid = ~np.isnan(d)
    total = int(valid.sum())
    if total == 0:
        return MonitorResponse(new_scene=True, scene_date=s.dates[last_i], ndvi=round(ndvi_last, 3), latest_seen=seen)
    decline = valid & (d <= -req.drop)
    improve = valid & (d >= req.drop)
    decline_pct = round(float(decline.sum()) / total * 100, 1)
    png = render.change_map(d, s.x, s.y, poly_utm, req.drop) if decline_pct >= req.map_min_pct else None
    return MonitorResponse(
        new_scene=True, scene_date=s.dates[last_i], ref_date=s.dates[ref_i],
        ndvi=round(ndvi_last, 3), ndvi_ref=round(float(np.nanmedian(ref)), 3),
        decline_pct=decline_pct, decline_side=analysis.weak_side(s, valid, decline),
        improve_pct=round(float(improve.sum()) / total * 100, 1), map_png_base64=png, latest_seen=seen,
    )
