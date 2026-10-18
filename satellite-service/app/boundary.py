"""R2: "dalaga bir bosish" — nuqta atrofidagi dala chegarasini Sentinel-2 dan topish (taklif; fermer to'g'rilaydi).

Usul (scipy'siz, numpy + rasterio):
  1. Nuqta atrofida ~1,6 km oyna, oxirgi 75 kunning bulutsiz 3 ta sanasi (10 m piksel).
  2. Har piksel belgisi: NDVI (3 sana) + NDMI (oxirgisi) — qo'shni dalalar bitta ekin bo'lsa ham ekish/sug'orish vaqti
     farq qiladi, bir necha sana ularni ajratadi.
  3. Nuqtadagi 3×3 o'rtacha bilan masofa < T bo'lgan va keskin chegara (gradient) bo'lmagan piksellar — nuqtadan
     "suv toshqini" (flood fill). T kichikdan kattaga: maydon birdan oshib ketsa (qo'shni dalaga "oqib o'tdi") — to'xtaydi.
  4. Teshiklar to'ldiriladi, ingichka ko'priklar (1 piksellik) uziladi, poligon soddalashtiriladi (~12 m).
Aniqlik: ±10 m (piksel); kichik (<1 ga) va bir xil qo'shni dalalarda xato bo'lishi mumkin — natija faqat taklif.
"""
import logging
from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
import odc.stac
from pydantic import BaseModel, Field
from pyproj import Transformer
from pystac_client import Client
from pystac_client.exceptions import APIError
from rasterio.features import shapes
from shapely.geometry import Point, mapping, shape
from shapely.ops import transform

from app.analysis import BAD_SCL, COLLECTION, EARTH_SEARCH, OFFSET, SCALE
from app.geometry import LAT_MAX, LAT_MIN, MAX_HA, MIN_HA, utm_epsg
from app.schemas import Rejected, Upstream

log = logging.getLogger(__name__)

HALF_M = 800                      # oyna yarmi: 1,6 × 1,6 km (≈ 256 ga) — katta dala chetga tegsa "touchesEdge"
DAYS = 75
SCENES = 3
MIN_CLEAR = 0.8                   # oynaning 80% ochiq bo'lgan sana
THRESHOLDS = (0.5, 0.7, 0.9, 1.15, 1.45)
GRAD_MAX = 0.12                   # NDVI qo'shni piksel farqi — undan katta: chegara (ariq, yo'l, boshqa ekin)
EDGE_Z = 1.0                      # boshqa belgilar: qo'shni farq shuncha standart og'ishdan katta — chegara
RECT_OK = 0.72                    # maydon / eng kichik o'ralgan to'rtburchak — undan past: qo'shniga "oqgan" shakl
MAX_POINTS = 80
SCL_WATER = 6


class DetectRequest(BaseModel):
    lat: float = Field(ge=LAT_MIN, le=LAT_MAX)
    lon: float = Field(ge=-180, le=180)


class DetectResponse(BaseModel):
    coordinates: list[list[float]]    # [[lon, lat], ...] yopiq halqa
    area_ha: float
    scene_date: date
    confidence: str                   # HIGH | LOW (chetga tekkan / juda kichik / qo'shniga oqish belgisi)
    touches_edge: bool


@dataclass
class _Scene:
    f: np.ndarray                     # (F, H, W) belgilar: NDVI × sanalar + NDMI (oxirgi)
    bands: np.ndarray                 # (2, H, W) oxirgi sananing red, nir — chegara (gradient) uchun
    valid: np.ndarray
    affine: object
    epsg: int
    cx: float
    cy: float
    scene: date


def detect(req: DetectRequest) -> DetectResponse:
    sc = _load(req)
    poly, touches, rect = _segment(sc)
    area_ha = poly.area / 10_000
    if area_ha < MIN_HA:
        raise Rejected(422, "AREA_TOO_SMALL", min_ha=MIN_HA, area_ha=round(area_ha, 2))
    if area_ha > MAX_HA:
        raise Rejected(422, "AREA_TOO_LARGE", max_ha=MAX_HA, area_ha=round(area_ha, 2))
    inv = Transformer.from_crs(sc.epsg, 4326, always_xy=True)
    ring = [[round(x, 6), round(y, 6)] for x, y in (inv.transform(px, py) for px, py in poly.exterior.coords)]
    low = touches or area_ha < 1 or rect < RECT_OK
    log.info("boundary %.5f,%.5f → %.1f ga, %d nuqta, to'rtburchaklik %.2f, sana %s, %s", req.lat, req.lon, area_ha,
             len(ring), rect, sc.scene, "LOW" if low else "HIGH")
    return DetectResponse(coordinates=ring, area_ha=round(area_ha, 2), scene_date=sc.scene,
                          confidence="LOW" if low else "HIGH", touches_edge=touches)


def _load(req: DetectRequest) -> _Scene:
    epsg = utm_epsg(req.lon, req.lat)
    fwd = Transformer.from_crs(4326, epsg, always_xy=True)
    inv = Transformer.from_crs(epsg, 4326, always_xy=True)
    cx, cy = fwd.transform(req.lon, req.lat)
    win = Point(cx, cy).buffer(HALF_M, cap_style=3)                 # kvadrat
    wgs = transform(inv.transform, win)

    end = date.today()
    try:
        items = list(Client.open(EARTH_SEARCH).search(
            collections=[COLLECTION], intersects=mapping(Point(req.lon, req.lat)),
            datetime=f"{(end - timedelta(days=DAYS)).isoformat()}/{end.isoformat()}",
            query={"eo:cloud_cover": {"lt": 40}}, max_items=150,   # 75 kun ≈ 30–60 ta; tartib kafolatsiz — o'zimiz saralaymiz
        ).items())
    except APIError as e:
        raise Upstream(f"Earth Search: {e}") from e
    if not items:
        raise Rejected(404, "NO_SCENES")
    items.sort(key=lambda i: i.datetime, reverse=True)

    # 1-bosqich: faqat bulut niqobi (SCL, 20 m — kichik) — qaysi sanalar ochiq; 2-bosqich: faqat o'sha 3 sananing kanallari.
    # Tarmoq (COG o'qish) — asosiy vaqt: keraksiz sanalarning red/nir/swir'ini yuklamaymiz
    common = dict(bbox=wgs.bounds, crs=f"EPSG:{epsg}", resolution=10, groupby="solar_day", pool=16)
    try:
        scl = odc.stac.load(items[:12], bands=["scl"], **common)
    except Exception as e:
        raise Upstream(f"COG o'qish: {e}") from e
    clear_all = ~scl.scl.isin(BAD_SCL).values                        # (t, y, x)
    frac = clear_all.mean(axis=(1, 2))
    order = np.argsort(scl.time.values)[::-1]                         # yangidan eskiga
    col0, row0 = ~scl.odc.geobox.affine * (cx, cy)
    r0, c0 = int(row0), int(col0)
    h0, w0 = clear_all.shape[1:]
    if not (1 <= r0 < h0 - 1 and 1 <= c0 < w0 - 1):
        raise Rejected(422, "POINT_NOT_CLEAR")
    latest = order[np.argmax(frac[order] >= MIN_CLEAR)] if (frac >= MIN_CLEAR).any() else None
    if latest is not None and (scl.scl.values[latest, r0 - 1:r0 + 2, c0 - 1:c0 + 2] == SCL_WATER).sum() >= 5:
        raise Rejected(422, "POINT_IS_WATER")
    # Oyna umuman ochiq VA bosilgan nuqta atrofi (3×3) ochiq sanalar
    picked = [t for t in order if frac[t] >= MIN_CLEAR and clear_all[t, r0 - 1:r0 + 2, c0 - 1:c0 + 2].all()][:SCENES]
    if not picked:
        raise Rejected(404, "NO_CLEAR_SCENE")
    days = {np.datetime64(scl.time.values[t], "D") for t in picked}
    chosen = [i for i in items[:12] if np.datetime64(i.datetime.date(), "D") in days]
    try:
        ds = odc.stac.load(chosen, bands=["red", "nir", "swir16"], like=scl, groupby="solar_day", pool=16)
    except Exception as e:
        raise Upstream(f"COG o'qish: {e}") from e

    def refl(b):
        return np.where(b != 0, b * SCALE + OFFSET, np.nan)

    # ds sanalari — picked tartibida emas: sana bo'yicha moslaymiz (yangidan eskiga)
    ds_days = [np.datetime64(t, "D") for t in ds.time.values]
    red, nir, swir = (refl(ds[b].values.astype("float64")) for b in ("red", "nir", "swir16"))
    feats = []
    idx = []
    for t in picked:
        d = np.datetime64(scl.time.values[t], "D")
        if d not in ds_days:
            continue
        k = ds_days.index(d)
        idx.append((t, k))
        nd = (nir[k] - red[k]) / (nir[k] + red[k])
        feats.append(np.where(clear_all[t] & (np.abs(nd) <= 1), nd, np.nan))
    if not feats:
        raise Rejected(404, "NO_CLEAR_SCENE")
    t0, k0 = idx[0]
    ndmi = (nir[k0] - swir[k0]) / (nir[k0] + swir[k0])
    feats.append(np.where(clear_all[t0] & (np.abs(ndmi) <= 1), ndmi, np.nan))
    f = np.stack(feats)                                               # (F, H, W)
    # Oxirgi sana ochiq bo'lsa yetarli: eski sanalarda shu piksel bulutli bo'lsa — masofa qolgan belgilar bo'yicha (nanmean)
    valid = ~np.isnan(f[0]) & ~np.isnan(f[-1])
    bands = np.stack([np.where(clear_all[t0], red[k0], np.nan), np.where(clear_all[t0], nir[k0], np.nan)])

    transform_aff = scl.odc.geobox.affine
    col, row = ~transform_aff * (cx, cy)
    r, c = int(row), int(col)
    h, w = valid.shape
    if not (1 <= r < h - 1 and 1 <= c < w - 1) or not valid[r, c]:
        raise Rejected(422, "POINT_NOT_CLEAR")
    return _Scene(f, bands, valid, transform_aff, epsg, cx, cy, np.datetime64(scl.time.values[t0], "D").astype(date))


def _segment(sc: _Scene):
    """@return (UTM poligon, oyna chetiga tegdimi, to'rtburchaklik 0..1)"""
    f = sc.f
    valid = ~np.isnan(f[0]) & ~np.isnan(f[-1])
    col, row = ~sc.affine * (sc.cx, sc.cy)
    r, c = int(row), int(col)

    seed = f[:, r - 1:r + 2, c - 1:c + 2].reshape(f.shape[0], -1)
    seed_mean = np.nanmean(seed, axis=1)
    scale = np.nanstd(f.reshape(f.shape[0], -1), axis=1)
    scale = np.where(scale < 0.02, 0.02, scale)
    dist = np.sqrt(np.nanmean(((f - seed_mean[:, None, None]) / scale[:, None, None]) ** 2, axis=0))
    # Chegara: belgilar va oxirgi red/nir bo'yicha keskin farq (ariq, yo'l, daraxt qatori, boshqa ekin/shudgor)
    edge = _gradient(f[0]) > GRAD_MAX
    for a in list(f) + list(sc.bands):
        sd = np.nanstd(a)
        if sd > 0:
            edge |= _gradient(a) / sd > EDGE_Z

    pt = Point(sc.cx, sc.cy)
    cands = []                                                        # (maydon, to'rtburchaklik, chetga tegdi, poligon)
    for th in THRESHOLDS:
        cand = valid & (dist < th) & ~edge
        cand[r, c] = True
        reg = _flood(_open(_open(cand)) | _seed_dot(cand.shape, r, c), r, c)
        if reg.sum() < 4:
            continue
        reg = _fill_holes(reg)
        touches = bool(reg[0].any() or reg[-1].any() or reg[:, 0].any() or reg[:, -1].any())
        poly = _polygon(reg, sc.affine, pt)
        if poly is None or poly.is_empty:
            continue
        rect = poly.area / max(poly.minimum_rotated_rectangle.area, 1.0)
        cands.append((poly.area, rect, touches, poly))
    if not cands:
        raise Rejected(422, "NOT_FOUND")
    # Dalalar odatda to'rtburchak: yetarlicha to'rtburchak (qo'shniga oqmagan) eng kattasi; bo'lmasa — eng to'rtburchagi
    good = [x for x in cands if x[1] >= RECT_OK and not x[2]]
    area, rect, touches, poly = max(good, key=lambda x: x[0]) if good else max(cands, key=lambda x: x[1])

    poly = poly.buffer(0).simplify(12, preserve_topology=True)
    tol = 12
    while len(poly.exterior.coords) > MAX_POINTS and tol < 60:
        tol += 8
        poly = poly.simplify(tol, preserve_topology=True)
    poly = type(poly)(poly.exterior)                                  # ichki teshiklar — yo'q (chegara chizig'i)
    return poly, touches, rect


def _polygon(reg: np.ndarray, affine, pt: Point):
    """Niqob → nuqtani o'z ichiga olgan poligon (bo'lmasa — eng kattasi)"""
    best = None
    for geom, _ in shapes(reg.astype("uint8"), mask=reg, transform=affine):
        p = shape(geom)
        if p.contains(pt):
            return p
        if best is None or p.area > best.area:
            best = p
    return best


def _gradient(a: np.ndarray) -> np.ndarray:
    """Qo'shni (4 tomon) piksel bilan eng katta farq; NaN — chegara emas (bulutni chegara deb olmaymiz).
    Chet — o'zi bilan (edge-replicate): np.roll qarama-qarshi chetga o'ralib, chekkani soxta "chegara" qilardi"""
    p = np.pad(a, 1, mode="edge")
    g = np.zeros_like(a)
    h, w = a.shape
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        d = np.abs(a - p[1 + dy:1 + dy + h, 1 + dx:1 + dx + w])
        g = np.fmax(g, np.nan_to_num(d, nan=0.0))
    return g


def _shift(m: np.ndarray, dy: int, dx: int) -> np.ndarray:
    out = np.zeros_like(m)
    h, w = m.shape
    out[max(dy, 0):h + min(dy, 0), max(dx, 0):w + min(dx, 0)] = m[max(-dy, 0):h + min(-dy, 0), max(-dx, 0):w + min(-dx, 0)]
    return out


def _dilate(m: np.ndarray) -> np.ndarray:
    return m | _shift(m, 1, 0) | _shift(m, -1, 0) | _shift(m, 0, 1) | _shift(m, 0, -1)


def _erode(m: np.ndarray) -> np.ndarray:
    return m & _shift(m, 1, 0) & _shift(m, -1, 0) & _shift(m, 0, 1) & _shift(m, 0, -1)


def _open(m: np.ndarray) -> np.ndarray:
    """1 piksellik ko'priklar (qo'shni dalaga ingichka o'tish) uziladi"""
    return _dilate(_erode(m)) & m


def _seed_dot(shape_: tuple, r: int, c: int) -> np.ndarray:
    m = np.zeros(shape_, dtype=bool)
    m[r, c] = True
    return m


def _flood(cand: np.ndarray, r: int, c: int) -> np.ndarray:
    """Nuqtadan 4-bog'langan to'ldirish (iterativ dilatsiya — oyna kichik, 160×160)"""
    reg = _seed_dot(cand.shape, r, c)
    while True:
        nxt = _dilate(reg) & cand
        if nxt.sum() == reg.sum():
            return nxt
        reg = nxt


def _fill_holes(m: np.ndarray) -> np.ndarray:
    """Chetdan yetib bo'lmaydigan "tashqi"lar — teshik (daraxt, bino) — dalaga qo'shiladi"""
    outside = np.zeros_like(m)
    outside[0, :], outside[-1, :], outside[:, 0], outside[:, -1] = ~m[0, :], ~m[-1, :], ~m[:, 0], ~m[:, -1]
    while True:
        nxt = _dilate(outside) & ~m
        if nxt.sum() == outside.sum():
            return ~nxt
        outside = nxt
