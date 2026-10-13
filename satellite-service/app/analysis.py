"""Sentinel-2 L2A (Earth Search, Collection 1) bo'yicha dala tahlili."""
import logging
import math
from dataclasses import dataclass
from datetime import date

import numpy as np
import odc.stac
import xarray as xr
from pystac_client import Client
from pystac_client.exceptions import APIError
from rasterio.features import geometry_mask
from shapely.geometry import Polygon, mapping

from app.schemas import Period, Rejected, Upstream

log = logging.getLogger(__name__)

EARTH_SEARCH = "https://earth-search.aws.element84.com/v1"
COLLECTION = "sentinel-2-c1-l2a"
SCALE, OFFSET = 0.0001, -0.1          # item_assets.*.raster:bands — 2026-10 da tekshirilgan
BAD_SCL = [0, 1, 3, 8, 9, 10, 11]     # nodata, saturated, bulut soyasi, bulut (o'rta/yuqori), cirrus, qor
MIN_CLEAR = 0.7                       # dalaning 70% dan kami ochiq bo'lsa — sana tashlanadi
EDGE_M = 10                           # chetdagi 1 piksel (ariq, yo'l) tashlanadi
GOOD_REL, WEAK_REL = 0.95, 0.85       # dala medianasiga nisbatan


@dataclass
class FieldSeries:
    dates: list[date]
    ndvi: xr.DataArray                # (time, y, x), dala tashqarisi va bulut — NaN
    ndmi: xr.DataArray
    inside: np.ndarray                # (y, x) bool
    x: np.ndarray                     # piksel markazlari, UTM metr
    y: np.ndarray
    rejected: int


def load_series(poly_wgs: Polygon, poly_utm: Polygon, epsg: int, start: date, end: date) -> FieldSeries | None:
    try:
        items = list(Client.open(EARTH_SEARCH).search(
            collections=[COLLECTION],
            intersects=mapping(poly_wgs),
            datetime=f"{start.isoformat()}/{end.isoformat()}",
            query={"eo:cloud_cover": {"lt": 60}},   # sahna bo'yicha; dala ustidagi bulutni SCL hal qiladi
        ).items())
    except APIError as e:
        raise Upstream(f"Earth Search: {e}") from e
    if not items:
        return None

    try:
        ds = odc.stac.load(
            items,
            bands=["red", "nir", "swir16", "scl"],
            bbox=poly_wgs.buffer(0.001).bounds,      # dala atrofida ~100 m zaxira
            crs=f"EPSG:{epsg}",
            resolution=10,                           # swir16/scl (20 m) 10 m ga — nearest
            groupby="solar_day",
            pool=16,                                 # COG o'qish parallel (I/O bound)
        )
    except Exception as e:                           # rasterio/GDAL tarmoq xatolari
        raise Upstream(f"COG o'qish: {e}") from e

    inside = ~geometry_mask([mapping(poly_utm.buffer(-EDGE_M))],
                            out_shape=ds.odc.geobox.shape,
                            transform=ds.odc.geobox.affine)
    if inside.sum() == 0:
        raise Rejected(422, "AREA_TOO_SMALL")

    def refl(band):                                  # DN → aks ettirish; 0 = nodata
        return band.where(band != 0) * SCALE + OFFSET

    red, nir, swir = refl(ds.red), refl(ds.nir), refl(ds.swir16)
    mask = xr.DataArray(inside, dims=("y", "x"), coords={"y": ds.y, "x": ds.x})
    clear = ~ds.scl.isin(BAD_SCL) & mask
    # OFFSET (−0.1) tufayli qorong'i piksel (suv, soya) aks ettirishi manfiy bo'lishi mumkin — indeks [−1, 1] dan
    # chiqib ketadi (−1.1 kabi) va medianani buzadi. Fizik ma'nosiz qiymat — NaN
    ndvi = ((nir - red) / (nir + red)).where(clear)
    ndmi = ((nir - swir) / (nir + swir)).where(clear)
    ndvi = ndvi.where((ndvi >= -1) & (ndvi <= 1))
    ndmi = ndmi.where((ndmi >= -1) & (ndmi <= 1))

    clear_frac = (clear.sum(("y", "x")) / inside.sum()).values
    keep = clear_frac >= MIN_CLEAR
    ndvi, ndmi = ndvi.isel(time=keep), ndmi.isel(time=keep)
    dates = [np.datetime64(t, "D").astype(date) for t in ndvi.time.values]
    return FieldSeries(dates, ndvi, ndmi, inside, ds.x.values, ds.y.values, int((~keep).sum()))


def medians(s: FieldSeries) -> tuple[np.ndarray, np.ndarray]:
    return (s.ndvi.median(("y", "x")).values, s.ndmi.median(("y", "x")).values)


def zones(s: FieldSeries) -> tuple[np.ndarray, dict]:
    """Mavsum cho'qqisidagi har piksel / dala medianasi. Nisbiy — ekin va navga bog'liq emas,
    dala ichidagi notekislikni (notekis sug'orish izini) ko'rsatadi."""
    peak = s.ndvi.quantile(0.9, dim="time", skipna=True).values
    med = float(np.nanmedian(peak))
    rel = peak / med
    valid = ~np.isnan(rel)
    total = int(valid.sum())
    good = float((rel >= GOOD_REL).sum()) / total * 100
    weak_mask = valid & (rel < WEAK_REL)
    weak = float(weak_mask.sum()) / total * 100
    return rel, {"good_pct": round(good, 1), "mid_pct": round(100 - good - weak, 1),
                 "weak_pct": round(weak, 1), "weak_side": weak_side(s, valid, weak_mask)}


def weak_side(s: FieldSeries, valid: np.ndarray, weak: np.ndarray) -> str | None:
    """Zaif piksellar og'irlik markazi dala markaziga nisbatan qaysi tomonda."""
    if weak.sum() == 0:
        return None
    xx, yy = np.meshgrid(s.x, s.y)
    dx = xx[weak].mean() - xx[valid].mean()
    dy = yy[weak].mean() - yy[valid].mean()          # UTM: y shimolga o'sadi
    size = math.sqrt(valid.sum()) * 10               # dala "diametri", metr
    if math.hypot(dx, dy) < 0.15 * size:
        return "CENTER"
    sides = ["E", "NE", "N", "NW", "W", "SW", "S", "SE"]
    return sides[round(math.degrees(math.atan2(dy, dx)) / 45) % 8]


def growing_season(dates: list[date], ndvi: np.ndarray) -> Period | None:
    """NDVI ≥ asos + 30% amplituda bo'lgan uzluksiz davrlar — har biri bitta hosil.
    Kalendar jadvali yo'q — janubiy yarim shar va tropikda ham ishlaydi.
    Yiliga 2 hosil (bug'doy + soya) bo'lsa, oyna chetiga tegmagan TO'LIQ mavsum afzal:
    chetdagisi yarmi kesilgan bo'lak, uning stress va suv hisobi noto'g'ri."""
    ok = ~np.isnan(ndvi)
    if ok.sum() < 4:
        return None
    v = ndvi[ok]
    d = [x for x, k in zip(dates, ok) if k]
    base, top = float(np.percentile(v, 10)), float(v.max())
    if top - base < 0.15 or top < 0.3:               # ekin ko'rinmaydi (shudgor, yalang'och yer)
        return None
    thr = base + 0.3 * (top - base)
    runs, i = [], 0
    while i < len(v):
        if v[i] < thr:
            i += 1
            continue
        j = i
        while j + 1 < len(v) and v[j + 1] >= thr:
            j += 1
        runs.append((i, j, float(v[i:j + 1].max())))
        i = j + 1
    complete = [r for r in runs if r[0] > 0 and r[1] < len(v) - 1]
    i, j, _ = max(complete or runs, key=lambda r: r[2])
    return Period(start=d[i], end=d[j])


def season_peak(dates: list[date], ndvi: np.ndarray, season: Period | None) -> tuple[date, float]:
    """Mavsum ichidagi cho'qqi; mavsum yo'q bo'lsa — butun oyna."""
    pts = [(d, float(v)) for d, v in zip(dates, ndvi)            # float32 → float: round() toza bo'lsin
           if not np.isnan(v) and (season is None or season.start <= d <= season.end)]
    return max(pts, key=lambda p: p[1])


def stress_periods(dates: list[date], ndvi: np.ndarray, ndmi: np.ndarray,
                   season: Period | None) -> list[Period]:
    """Mavsum ichida ketma-ket 2+ sanada NDMI tushgan va NDVI o'smagan davrlar.
    Faqat ekin to'liq rivojlangan paytda (NDVI ≥ cho'qqining 80%): pishib qurish
    (bug'doy, may) ham NDMI'ni tushiradi — bu stress emas.
    Boshlang'ich qoida — pilot dalalardagi fermer gaplari bilan sozlanadi."""
    if season is None:
        return []
    canopy = 0.8 * season_peak(dates, ndvi, season)[1]
    pts = [(d, v, m) for d, v, m in zip(dates, ndvi, ndmi)
           if season.start <= d <= season.end and not (np.isnan(v) or np.isnan(m)) and v >= canopy]
    out, run = [], []
    for (d0, v0, m0), (d1, v1, m1) in zip(pts, pts[1:]):
        if m1 < m0 - 0.01 and v1 <= v0 + 0.01:
            run.append((d0, d1, m0 - m1))
            continue
        _close(run, out)
        run = []
    _close(run, out)
    return out


def _close(run, out):
    if len(run) >= 2 and sum(r[2] for r in run) >= 0.03:   # kichik shovqinni tashlaymiz
        out.append(Period(start=run[0][0], end=run[-1][1]))
