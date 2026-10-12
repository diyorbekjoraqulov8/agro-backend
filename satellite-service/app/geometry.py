import json
import logging
from pathlib import Path

from pyproj import Transformer
from shapely import STRtree
from shapely.geometry import Point, Polygon, shape
from shapely.ops import transform

from app.schemas import Rejected

log = logging.getLogger(__name__)

MIN_HA, MAX_HA = 0.5, 500.0
MAX_VERTICES = 200                    # Telegram sendData 4096 bayt chegarasi
LAT_MIN, LAT_MAX = -60.0, 75.0        # Sentinel-2 qishloq xo'jaligi hududlarini qamraydi

COUNTRIES_FILE = Path(__file__).resolve().parent.parent / "data" / "ne_50m_admin_0_countries.geojson"
COUNTRIES_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/"
                 "master/geojson/ne_50m_admin_0_countries.geojson")


def utm_epsg(lon: float, lat: float) -> int:
    zone = int((lon + 180) // 6) % 60 + 1          # lon = 180 da 61 bo'lib ketmasin
    return (32600 if lat >= 0 else 32700) + zone   # 326xx — shimol, 327xx — janub


def parse_polygon(geojson: dict) -> Polygon:
    """Arzon tekshiruvlar avval, qimmat (maydon) keyin."""
    if geojson.get("type") != "Polygon":
        raise Rejected(422, "NOT_POLYGON")
    try:
        ring = geojson["coordinates"][0]
        coords = [(float(x), float(y)) for x, y in ring]
    except (KeyError, IndexError, TypeError, ValueError):
        raise Rejected(422, "BAD_COORDINATES")
    if len(coords) > MAX_VERTICES + 1:
        raise Rejected(422, "TOO_MANY_VERTICES", max=MAX_VERTICES)
    if len(coords) < 4:
        raise Rejected(422, "TOO_FEW_VERTICES")
    for lon, lat in coords:
        if not (-180 <= lon <= 180 and LAT_MIN <= lat <= LAT_MAX):
            raise Rejected(422, "OUT_OF_RANGE")
    lons = [c[0] for c in coords]
    if max(lons) - min(lons) > 180:                 # 180° meridianni kesib o'tadi
        raise Rejected(422, "CROSSES_ANTIMERIDIAN")
    poly = Polygon(coords)                          # halqa yopilmagan bo'lsa shapely yopadi
    if not poly.is_valid:
        raise Rejected(422, "SELF_INTERSECTING")
    return poly


def to_utm(poly: Polygon) -> tuple[Polygon, int]:
    c = poly.centroid
    epsg = utm_epsg(c.x, c.y)
    fwd = Transformer.from_crs(4326, epsg, always_xy=True).transform
    return transform(fwd, poly), epsg


def check_area(area_ha: float) -> None:
    if area_ha < MIN_HA:
        raise Rejected(422, "AREA_TOO_SMALL", min_ha=MIN_HA, area_ha=round(area_ha, 2))
    if area_ha > MAX_HA:
        raise Rejected(422, "AREA_TOO_LARGE", max_ha=MAX_HA, area_ha=round(area_ha, 2))


class CountryIndex:
    """Natural Earth chegaralari bilan offline aniqlash — tashqi geocoding API, kalit, kvota yo'q."""

    def __init__(self, path: Path = COUNTRIES_FILE):
        self.codes: list[str] = []
        self.tree: STRtree | None = None
        if not path.exists():
            log.warning("%s yo'q — country_code null bo'ladi. Yuklash: python -m app.geometry", path)
            return
        geoms = []
        for f in json.loads(path.read_text())["features"]:
            p = f["properties"]
            code = p.get("ISO_A2_EH") or p.get("ISO_A2")   # ISO_A2 Fransiya/Norvegiyada "-99"
            if code and code != "-99":
                geoms.append(shape(f["geometry"]))
                self.codes.append(code)
        self.tree = STRtree(geoms)

    def lookup(self, lon: float, lat: float) -> str | None:
        if self.tree is None:
            return None
        hits = self.tree.query(Point(lon, lat), predicate="intersects")
        return self.codes[int(hits[0])] if len(hits) else None


if __name__ == "__main__":                          # python -m app.geometry — faylni yuklaydi
    import httpx
    COUNTRIES_FILE.parent.mkdir(exist_ok=True)
    COUNTRIES_FILE.write_bytes(httpx.get(COUNTRIES_URL, timeout=60, follow_redirects=True).content)
    print(f"{COUNTRIES_FILE} ({COUNTRIES_FILE.stat().st_size // 1024} KB)")
