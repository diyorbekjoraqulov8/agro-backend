"""Open-Meteo (ERA5) — kunlik ET0 va yog'in. Kalitsiz, global."""
from datetime import date, timedelta

import httpx
import numpy as np

from app.schemas import Period, Upstream, Water

ARCHIVE = "https://archive-api.open-meteo.com/v1/archive"
ARCHIVE_LAG_DAYS = 6                  # arxiv real vaqtdan ~5 kun orqada
UNCERTAINTY = 0.15                    # fermerga ±15% oraliq


def daily(lat: float, lon: float, start: date, end: date) -> tuple[list[date], np.ndarray, np.ndarray]:
    end = min(end, date.today() - timedelta(days=ARCHIVE_LAG_DAYS))
    try:
        r = httpx.get(ARCHIVE, timeout=30, params={
            "latitude": round(lat, 4), "longitude": round(lon, 4),
            "start_date": start.isoformat(), "end_date": end.isoformat(),
            "daily": "et0_fao_evapotranspiration,precipitation_sum", "timezone": "auto",
        })
        r.raise_for_status()
        d = r.json()["daily"]
    except (httpx.HTTPError, KeyError, ValueError) as e:
        raise Upstream(f"Open-Meteo: {e}") from e
    days = [date.fromisoformat(t) for t in d["time"]]
    et0 = np.array([x if x is not None else np.nan for x in d["et0_fao_evapotranspiration"]], float)
    rain = np.array([x if x is not None else 0.0 for x in d["precipitation_sum"]], float)
    return days, et0, rain


def kc_from_ndvi(ndvi: np.ndarray) -> np.ndarray:
    """Kc = 1.457·NDVI − 0.1725 (Kamble va boshq., 2013, Kc–NDVI chiziqli bog'lanishi).
    Jadvaldagi Kc dan yaxshiroq: dalaning haqiqiy rivojlanishini hisobga oladi.
    Koeffitsiyentlar — pilot ma'lumotida tekshiriladigan boshlang'ich qiymat."""
    return np.clip(1.457 * ndvi - 0.1725, 0.15, 1.20)


def water_need(obs_dates: list[date], ndvi: np.ndarray, season: Period,
               days: list[date], et0: np.ndarray, rain: np.ndarray) -> Water:
    ok = ~np.isnan(ndvi)
    xp = np.array([d.toordinal() for d, k in zip(obs_dates, ok) if k])
    xd = np.array([d.toordinal() for d in days])
    ndvi_daily = np.interp(xd, xp, ndvi[ok])          # sanalar orasida chiziqli
    in_season = (xd >= season.start.toordinal()) & (xd <= season.end.toordinal()) & ~np.isnan(et0)
    etc = float((et0 * kc_from_ndvi(ndvi_daily))[in_season].sum())
    et0_sum = float(et0[in_season].sum())
    # Samarali yog'in: kunlik 5 mm dan kami bug'lanib ketadi, qolganining 80% i
    eff_rain = float((np.maximum(rain - 5, 0) * 0.8)[in_season].sum())
    need = max(etc - eff_rain, 0) * 10                # 1 mm = 10 m³/ga
    return Water(
        et0_sum_mm=round(et0_sum, 1), etc_sum_mm=round(etc, 1), effective_rain_mm=round(eff_rain, 1),
        irrigation_need_m3_ha=int(round(need, -1)),
        irrigation_need_low_m3_ha=int(round(need * (1 - UNCERTAINTY), -2)),
        irrigation_need_high_m3_ha=int(round(need * (1 + UNCERTAINTY), -2)),
    )
