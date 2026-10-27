"""R4: "📸 Bugungi rasm" — oxirgi ochiq Sentinel-2 surati bo'yicha dala holati (mutlaq NDVI) va sust joy markazi.

Fermer kartadan so'raydi (~5–15 s); Java natijani (sana bo'yicha) keshlaydi. Sust joy: dala medianasidan 15% past
piksellar (zone'lar bilan bir xil WEAK_REL) — ulushi 3% dan ko'p bo'lsa markazi (lat/lon) — "📍 borib ko'ring".
"""
from datetime import date, timedelta

import numpy as np
from pydantic import BaseModel
from pyproj import Transformer
from shapely.geometry import Polygon

from app import analysis, render
from app.boundary import _erode
from app.schemas import Rejected

LOOKBACK_DAYS = 30
WEAK_MIN_PCT = 3.0
MIN_CROP_NDVI = 0.3               # dala medianasi shundan past — ekin yo'q/yig'ilgan, sust joy ko'rsatilmaydi


class SnapshotRequest(BaseModel):
    geometry: dict


class SnapshotResponse(BaseModel):
    scene_date: date
    ndvi: float                       # dala medianasi
    weak_pct: float                   # medianadan 15% past piksellar ulushi
    weak_lat: float | None = None     # sust joy markazi (ulushi ≥ 3% bo'lsa)
    weak_lon: float | None = None
    map_png_base64: str


def core_point(mask: np.ndarray, x: np.ndarray, y: np.ndarray, epsg: int) -> tuple[float, float]:
    """Eng katta bo'lakning eng ichki nuqtasi (lon, lat): niqob qayta-qayta toraytiriladi, oxirgi qolganlari — markaz.
    (O'rtacha nuqta ikki bo'lak orasiga — daraxt qatori yoki ariqqa tushib qolardi). mask bo'sh bo'lmasligi shart"""
    if not mask.any():
        raise ValueError("core_point: bo'sh niqob")
    core = mask
    while True:
        nxt = _erode(core)
        if not nxt.any():
            break
        core = nxt
    xx, yy = np.meshgrid(x, y)
    cx, cy = xx[core].mean(), yy[core].mean()
    k = int(np.argmin((xx[core] - cx) ** 2 + (yy[core] - cy) ** 2))
    lon, lat = Transformer.from_crs(epsg, 4326, always_xy=True).transform(xx[core][k], yy[core][k])
    return round(lon, 6), round(lat, 6)


def snapshot(poly: Polygon, poly_utm: Polygon, epsg: int) -> SnapshotResponse:
    today = date.today()
    s = analysis.load_series(poly, poly_utm, epsg, today - timedelta(days=LOOKBACK_DAYS), today)
    if s is None or not s.dates:
        raise Rejected(404, "NO_SCENES")
    nd = s.ndvi.isel(time=len(s.dates) - 1).values
    med = float(np.nanmedian(nd))
    valid = ~np.isnan(nd)
    weak = valid & (nd < med * analysis.WEAK_REL)
    weak_pct = float(weak.sum()) / max(int(valid.sum()), 1) * 100
    lat = lon = None
    if weak_pct >= WEAK_MIN_PCT and med >= MIN_CROP_NDVI:  # ekin yo'q (o'rim, shudgor) dalada "sust joy" ma'nosiz
        lon, lat = core_point(weak, s.x, s.y, epsg)
    return SnapshotResponse(scene_date=s.dates[-1], ndvi=round(med, 3), weak_pct=round(weak_pct, 1),
                            weak_lat=lat, weak_lon=lon,
                            map_png_base64=render.ndvi_map(nd, s.x, s.y, poly_utm,
                                                           (lon, lat) if lat is not None else None, epsg))
