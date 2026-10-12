"""Sug'orish eslatmasi uchun kunlik tuproq namligi balansi (FAO-56, 8-bob, soddalashtirilgan).

    Dr(i) = Dr(i-1) + ETc(i) − P_eff(i),  0 ≤ Dr ≤ TAW,  sug'orilgan kun Dr = 0
    ETc = ET0 × Kc;  Kc — dalaning so'nggi NDVI'sidan (weather.kc_from_ndvi)
    Sug'orish kerak: Dr ≥ RAW (= p × TAW)

Stateless: har chaqiruvda oxirgi sug'orishdan bugungacha qayta hisoblanadi (Open-Meteo forecast
API: past_days ≤ 92 + 7 kun prognoz). Holat Java'da faqat "oxirgi sug'orish sanasi".
"""
from datetime import date, timedelta

import httpx
from pydantic import BaseModel, Field

from app.schemas import Rejected, Upstream

FORECAST = "https://api.open-meteo.com/v1/forecast"
SOILGRIDS = "https://rest.isric.org/soilgrids/v2.0/properties/query"
MAX_PAST = 92                         # Open-Meteo forecast API chegarasi
FORECAST_DAYS = 7


class BalanceRequest(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)
    last_irrigated_on: date
    kc: float = Field(ge=0.1, le=1.4)
    taw_mm: float = Field(gt=0, le=600)
    raw_mm: float = Field(gt=0, le=600)


class Day(BaseModel):
    date: date
    et0_mm: float
    rain_mm: float
    depletion_mm: float
    forecast: bool


class BalanceResponse(BaseModel):
    local_date: date
    utc_offset_seconds: int
    timezone: str
    depletion_mm: float               # bugungi tanqislik (0 — tuproq to'la)
    depletion_pct: float              # RAW'ga nisbatan, %: 100+ — sug'orish vaqti
    raw_mm: float
    taw_mm: float
    due: bool                         # bugun yoki kechikkan
    days_until: int | None            # prognoz 7 kunida RAW'ga yetmasa — None
    irrigate_on: date | None
    rain_next_3d_mm: float
    etc_today_mm: float
    days: list[Day]


def effective_rain(p: float) -> float:
    # weather.water_need bilan bir xil: kunlik 5 mm dan kami bug'lanadi, qolganining 80% i
    return max(p - 5, 0) * 0.8


def balance(req: BalanceRequest) -> BalanceResponse:
    try:
        r = httpx.get(FORECAST, timeout=20, params={
            "latitude": round(req.lat, 4), "longitude": round(req.lon, 4),
            "daily": "et0_fao_evapotranspiration,precipitation_sum",
            "past_days": MAX_PAST, "forecast_days": FORECAST_DAYS, "timezone": "auto",
        })
        r.raise_for_status()
        d = r.json()
    except (httpx.HTTPError, ValueError) as e:
        raise Upstream(f"Open-Meteo forecast: {e}") from e

    days_raw = d["daily"]
    # Dala mahalliy vaqtidagi "bugun" — forecast'ning birinchi prognoz kuni
    local_today = date.fromisoformat(days_raw["time"][MAX_PAST])
    start = max(req.last_irrigated_on, local_today - timedelta(days=MAX_PAST))
    if req.last_irrigated_on > local_today:
        raise Rejected(422, "IRRIGATION_IN_FUTURE")

    dr, out = 0.0, []
    for t, et0, p in zip(days_raw["time"], days_raw["et0_fao_evapotranspiration"], days_raw["precipitation_sum"]):
        day = date.fromisoformat(t)
        if day < start:
            continue
        et0, p = float(et0 or 0), float(p or 0)
        if day > start:                                  # sug'orilgan kuni tuproq to'la (Dr = 0)
            dr = min(max(dr + et0 * req.kc - effective_rain(p), 0.0), req.taw_mm)
        out.append(Day(date=day, et0_mm=round(et0, 2), rain_mm=round(p, 1),
                       depletion_mm=round(dr, 1), forecast=day > local_today))

    today = next(x for x in out if x.date == local_today)
    due = today.depletion_mm >= req.raw_mm
    upcoming = next((x for x in out if x.date >= local_today and x.depletion_mm >= req.raw_mm), None)
    rain3 = sum(x.rain_mm for x in out if local_today < x.date <= local_today + timedelta(days=3))
    return BalanceResponse(
        local_date=local_today, utc_offset_seconds=int(d.get("utc_offset_seconds", 0)), timezone=d.get("timezone", "UTC"),
        depletion_mm=today.depletion_mm, depletion_pct=round(today.depletion_mm / req.raw_mm * 100, 1),
        raw_mm=req.raw_mm, taw_mm=req.taw_mm, due=due,
        days_until=(upcoming.date - local_today).days if upcoming else None,
        irrigate_on=upcoming.date if upcoming else None,
        rain_next_3d_mm=round(rain3, 1), etc_today_mm=round(today.et0_mm * req.kc, 2),
        days=out[-(FORECAST_DAYS + 14):],
    )


class SoilRequest(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)


class SoilResponse(BaseModel):
    texture: str                      # SAND | LOAM | CLAY
    sand_pct: float
    clay_pct: float


def soil(req: SoilRequest) -> SoilResponse:
    """SoilGrids 2.0 (ISRIC, 250 m). ⚠️ Faqat standart chuqurliklar: 0-30cm — bo'sh javob."""
    try:
        r = httpx.get(SOILGRIDS, timeout=30, params=[
            ("lon", round(req.lon, 4)), ("lat", round(req.lat, 4)),
            ("property", "sand"), ("property", "clay"),
            ("depth", "0-5cm"), ("depth", "5-15cm"), ("depth", "15-30cm"), ("value", "mean")])
        r.raise_for_status()
        layers = {l["name"]: l for l in r.json()["properties"]["layers"]}
    except (httpx.HTTPError, ValueError, KeyError) as e:
        raise Upstream(f"SoilGrids: {e}") from e

    def pct(name: str) -> float:
        l = layers[name]
        vals = [x["values"]["mean"] for x in l["depths"] if x["values"]["mean"] is not None]
        if not vals:                                     # suv, shahar, muz — ma'lumot yo'q
            raise Rejected(404, "NO_SOIL_DATA")
        thick = [5, 10, 15][:len(vals)]                  # qatlam qalinligi bo'yicha o'rtacha
        return sum(v * t for v, t in zip(vals, thick)) / sum(thick) / l["unit_measure"]["d_factor"]

    sand, clay = pct("sand"), pct("clay")
    # USDA uchburchagining soddalashtirilgani: 3 sinf (TAW farqi uchun yetarli)
    texture = "SAND" if sand >= 65 and clay < 15 else "CLAY" if clay >= 35 else "LOAM"
    return SoilResponse(texture=texture, sand_pct=round(sand, 1), clay_pct=round(clay, 1))
