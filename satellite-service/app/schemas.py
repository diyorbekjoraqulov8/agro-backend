"""Java ↔ Python shartnomasi. O'zgartirsangiz — Java'dagi AnalyzeRequest/AnalyzeResponse ham."""
from datetime import date
from typing import Any

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    field_id: int
    geometry: dict[str, Any]          # GeoJSON Polygon, WGS84
    crop: str
    season_start: date                # Java: oxirgi 12 oy
    season_end: date
    compare_previous_year: bool = True


class Zones(BaseModel):
    good_pct: float
    mid_pct: float
    weak_pct: float
    weak_side: str | None             # N, NE, E, SE, S, SW, W, NW, CENTER; zaif zona yo'q bo'lsa None


class Period(BaseModel):
    start: date = Field(serialization_alias="from")
    end: date = Field(serialization_alias="to")


class Point(BaseModel):
    date: date
    ndvi: float
    ndmi: float


class Water(BaseModel):
    et0_sum_mm: float
    etc_sum_mm: float
    effective_rain_mm: float
    irrigation_need_m3_ha: int        # taxminiy o'rtacha
    irrigation_need_low_m3_ha: int    # fermerga oraliq ko'rsatiladi
    irrigation_need_high_m3_ha: int


class Recommendation(BaseModel):
    code: str                         # Matn Java'da, fermer tilida
    params: dict[str, Any] = {}


class Scene(BaseModel):
    date: date
    cloud_pct: float | None           # sahna bo'yicha (STAC); dala ustidagisi — clear_pct
    clear_pct: float                  # dala ichidagi ochiq piksellar ulushi
    used: bool                        # clear_pct ≥ min_clear_pct


class Debug(BaseModel):
    """L1b: tahlil nimaga asoslangani — admin aniqlikni tekshirishi uchun. Fermerga ko'rsatilmaydi"""
    scenes: list[Scene]               # ≥ search_cloud_lt bulutli sahnalar umuman qidirilmaydi — bu ro'yxatda yo'q
    search_cloud_lt: float
    min_clear_pct: float
    good_rel: float                   # piksel/dala medianasi ≥ shu — "yaxshi"
    weak_rel: float                   # < shu — "sust"
    peak_ndvi_median: float           # zonalar shu medianaga nisbatan
    pixels: int                       # dala ichidagi 10 m piksellar (chet 1 piksel tashlangan)
    weak_lat: float | None            # eng katta sust bo'lak markazi
    weak_lon: float | None
    ndvi_map_png_base64: str          # mavsum cho'qqisi (90-persentil) mutlaq NDVI + sust markaz


class AnalyzeResponse(BaseModel):
    field_id: int
    area_ha: float
    country_code: str | None
    centroid_lat: float
    centroid_lon: float
    scenes_used: int
    scenes_rejected_cloud: int
    zones: Zones
    peak_ndvi: float
    peak_date: date
    prev_year_peak_ndvi: float | None
    growing_season: Period | None     # NDVI'dan aniqlangan; ekin topilmasa None
    stress_periods: list[Period]
    water: Water | None               # mavsum topilmasa None
    timeseries: list[Point]
    recommendations: list[Recommendation]
    map_png_base64: str
    chart_png_base64: str
    debug: Debug | None = None


class Rejected(Exception):
    """422 (poligon) yoki 404 (surat yo'q) — Java qayta urinmaydi."""

    def __init__(self, status: int, code: str, **params: Any):
        self.status, self.code, self.params = status, code, params


class Upstream(Exception):
    """502 — Earth Search / Open-Meteo javob bermadi. Java qayta urinadi."""
