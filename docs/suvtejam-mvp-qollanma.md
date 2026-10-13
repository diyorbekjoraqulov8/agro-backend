# SuvTejam MVP — qadam-baqadam qo'llanma

> **Nima uchun:** `agro` skeleti (project-v1 dan olingan auth + RBAC) ustiga fermerlar uchun Telegram bot +
> sun'iy yo'ldosh tahlili xizmatini qurish.
> **Qayerda:** `docs/suvtejam-mvp-qollanma.md`
> **Bog'liq:** `CLAUDE.md` (skelet qoidalari va tuzoqlari shu yerda ham amal qiladi).
> **Holat:** 2026-10-04 dagi reja. Versiya raqamlari va tashqi API'larni o'rnatishdan oldin
> manbadan tekshiring.

---

## 0. MVP nima qiladi — bir qarashda

1. Fermer Telegram agro-kanaldagi reklamadan botga keladi (`/start kanal1`).
2. 3 savol: ekin, sug'orish usuli, suv manbai. Joylashuv so'ralmaydi — u poligondan aniqlanadi
   (xizmat **global**, mamlakat ro'yxati yo'q).
3. Mini App'da (bot ichidagi xarita) dala chegarasini barmog'i bilan chizadi.
4. Bot "tahlil qilinmoqda" deydi, 1-2 daqiqadan keyin hisobot yuboradi:
   maydon, zonalar xaritasi (yaxshi / o'rta / zaif), mavsum grafigi, suv stressi davrlari,
   taxminiy suv ehtiyoji, 2-3 tavsiya.
5. Hisobotdan keyin qolgan savollar + "Ha, bog'laning" tugmasi → lead.
6. Xodimlar (admin, agronom) skeletdagi auth bilan kirib fermer, dala, tahlil va lead'larni ko'radi.

**MVP'da YO'Q:** dala chegarasini avtomatik aniqlash, sensorlar, to'lov, har kungi
sug'orish tavsiyasi. Bular keyingi bosqich.

---

## 1. ARXITEKTURA

```
 Telegram ──long polling──▶ ┌──────────────────────────┐   HTTP (ichki tarmoq)   ┌────────────────────────┐
                            │  Java backend            │ ──────────────────────▶ │ satellite-service      │
 Mini App (static HTML) ────│  Spring Boot 4 (skelet)  │   X-Internal-Key        │ Python + FastAPI       │
   sendData → bot           │  bot/ farmer/ field/     │ ◀────────────────────── │ Earth Search (AWS)     │
                            │  analysis/ + auth, rbac  │   JSON + PNG            │ Open-Meteo             │
 Xodimlar (brauzer) ──────▶ │  PostgreSQL + Flyway     │                         └────────────────────────┘
   JWT cookie               └──────────────────────────┘
```

### Arxitektura qarorlari (skeletdagi ADR ro'yxatining davomi)

| # | Qaror | Sabab |
|---|---|---|
| S1 | Sun'iy yo'ldosh qismi **alohida Python xizmati** | Raster ekotizimi (`pystac-client`, `odc-stac`, `rasterio`, `numpy`) Python'da ancha boy. Java faqat orkestratsiya qiladi |
| S2 | Ma'lumot manbai — **Earth Search (AWS), `sentinel-2-c1-l2a`** | Ochiq API, kalit va kvota yo'q. C1 — 2017 dan beri bir xil qayta ishlangan, yillararo taqqoslash to'g'ri |
| S3 | Fermer = **`farmers` jadvali**, `users` EMAS | `users` da `email`+`password` majburiy, fermerda ikkalasi ham yo'q. `users` — faqat xodimlar (login qiladiganlar). Fermerning identifikatori — `telegram_user_id` |
| S4 | Bot — **long polling**, webhook emas | Kirish endpoint'i yo'q → yangi `SecurityFilterChain` kerak emas, skeletning xavfsizlik modeli buzilmaydi. Webhook keyinroq, yuklama oshganda |
| S5 | Poligon Mini App'dan **`Telegram.WebApp.sendData`** orqali keladi | Mini App backend'ga to'g'ridan-to'g'ri so'rov yubormaydi → `initData` tekshiruvi, CORS, yangi zanjir kerak emas. Cheklov: 4096 bayt, faqat **reply keyboard** tugmasidan ochilgan Mini App'da ishlaydi |
| S6 | Poligon MVP'da **GeoJSON `JSONB`**, PostGIS emas | Hibernate Spatial'ni Boot 4 / Hibernate 7 bilan sozlash xavfini hozir olmaymiz. Geometriya hisobi (maydon, validatsiya) Python'da. PostGIS — fazoviy so'rov kerak bo'lganda |
| S7 | Tahlil **navbat jadvali** (`analysis_jobs`) + `@Scheduled` worker | Tahlil 20-90 s davom etadi. Bot update'ini bloklamaymiz; restart'da yo'qolmaydi; qayta urinish mumkin |
| S8 | Worker navbatni **`FOR UPDATE SKIP LOCKED`** bilan oladi | Kelajakda 2 ta instans bo'lsa ham bitta job ikki marta ishlanmaydi |
| S9 | Python xizmati **tashqariga ochilmaydi**, `X-Internal-Key` header | Faqat Docker ichki tarmog'i. Kalit doimiy vaqtda solishtiriladi (`hmac.compare_digest`) |
| S10 | Bot holati (qaysi savolda turgani) **DB'da** (`bot_states`) | Restart'da fermer savol o'rtasida qolib ketmasin |

---

## 2. MANBALAR (bookmark qiling)

**Telegram**
- Bot API (to'liq): https://core.telegram.org/bots/api
- Mini Apps (`sendData`, `initData`, tema ranglari): https://core.telegram.org/bots/webapps
- Java kutubxona (pengrad): https://github.com/pengrad/java-telegram-bot-api
- BotFather: Telegramda `@BotFather`

**Xarita (Mini App)**
- Leaflet: https://leafletjs.com
- Leaflet-Geoman (poligon chizish, bepul versiyasi yetarli): https://geoman.io/docs/leaflet
- Muqobil: Leaflet.draw — https://github.com/Leaflet/Leaflet.draw
- Esri World Imagery (sun'iy yo'ldosh fon qatlami, atribut shart): https://www.arcgis.com/home/item.html?id=10df2279f9684e4a9f6a7f08febac2a9

**Sun'iy yo'ldosh**
- Earth Search: https://github.com/Element84/earth-search
- STAC Browser'da kolleksiya: https://radiantearth.github.io/stac-browser/#/external/earth-search.aws.element84.com/v1/collections/sentinel-2-c1-l2a
- pystac-client: https://pystac-client.readthedocs.io
- odc-stac: https://odc-stac.readthedocs.io
- rasterio: https://rasterio.readthedocs.io
- O'quv qo'llanma (STAC + Python, eng yaxshi boshlang'ich): https://carpentries-incubator.github.io/geospatial-python/
- NDVI / NDMI formulalari: https://custom-scripts.sentinel-hub.com/sentinel-2/ndvi/ va https://custom-scripts.sentinel-hub.com/sentinel-2/ndmi/
- Natijani ko'z bilan tekshirish: https://browser.dataspace.copernicus.eu

**Ob-havo va suv**
- Open-Meteo (bepul, kalitsiz, `et0_fao_evapotranspiration` tayyor): https://open-meteo.com/en/docs
- Open-Meteo arxiv (o'tgan mavsum): https://open-meteo.com/en/docs/historical-weather-api
- FAO-56 (bug'lanish va Kc jadvallari): https://www.fao.org/4/x0490e/x0490e00.htm

**Java / Python**
- Spring `RestClient`: https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
- Spring `@Scheduled`: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- FastAPI: https://fastapi.tiangolo.com
- PostgreSQL `SKIP LOCKED`: https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE

---

## 3. LOYIHA TUZILISHI

Bitta repo. Repo ildizi — Java backend (hozirgi tuzilish o'zgarmaydi), Python xizmati
va Mini App ildizda alohida papka bo'lib qo'shiladi:

```
agro/                            ← Gradle loyiha ildizi (Java backend)
├── src/main/java/uz/app/agro/
│   ├── common/ security/ auth/ user/ rbac/ audit/   (skeletdan, o'zgarmaydi)
│   ├── farmer/      FarmerEntity, FarmerService, FarmerController (xodimlar uchun)
│   ├── field/       FieldEntity, FieldService, FieldController
│   ├── analysis/    AnalysisJob, AnalysisWorker, AnalysisService, AnalysisController
│   ├── satellite/   SatelliteClient, SatelliteProperties, dto/
│   ├── stats/       StatsController, StatsService, StatsRepository (admin dashboard)
│   └── bot/         BotConfig, BotUpdateHandler, BotStateService, BotMessages, keyboards/
├── satellite-service/           ← Python (Gradle unga tegmaydi)
│   ├── app/main.py  app/analysis.py  app/weather.py  app/render.py  app/schemas.py
│   ├── requirements.txt
│   └── Dockerfile
├── miniapp/                     ← Vercel: index.html, app.js, style.css, vendor/leaflet, vercel.json
├── admin/                       ← admin panel: Vue 3 + TS + Vite + Tailwind 4 + shadcn-vue (admin/README.md)
├── compose.yaml                 ← dev: satellite (prod — S10)
└── docs/
```

**Yangi xizmat qo'shilsa** (masalan `notification-service`): ildizda `<nom>-service/` papka,
o'z `Dockerfile`i bilan, `compose.yaml` ga yangi `services:` bloki. `src/main/` — faqat Java
(Gradle source set), boshqa tildagi kod u yerga qo'yilmaydi.

**Dev'da ishga tushirish:** `docker compose up -d --build` — Python xizmati Docker'da,
kalit root `.env` dagi `SATELLITE_INTERNAL_KEY` dan (Java bilan avtomatik bir xil).
Java va PostgreSQL — kompyuterda (`./gradlew bootRun`).

Skeletdagi qoida saqlanadi: **package-by-feature**, har feature ichida
`XController`, `XService`, `XRepository`; papka faqat fayl ko'payganda (`dto/`).

---

## 4. QURISH TARTIBI

Skeletdagidek: har faza tugagach **to'xta va tekshir**. Tekshirish retseptlari har
faza oxirida.

| Faza | Nima | Tekshiruv natijasi |
|---|---|---|
| **S0** | Tayyorgarlik: BotFather, server | `./gradlew bootRun` ishlaydi, bot `/start` ga hech narsa demaydi |
| **S1** | Python: `/health` + `/analyze` | `curl` bilan bitta dala uchun JSON + PNG |
| **S2** | Flyway V6–V10: farmers, fields, analysis_jobs, bot_states, huquqlar | Migratsiyalar toza bazada o'tadi |
| **S3** | Java entity/repo/service (farmer, field, analysis) | `ddl-auto: validate` xatosiz |
| **S4** | `satellite/`: `SatelliteClient` + properties | Java'dan Python'ga so'rov o'tadi |
| **S5** ✅ | `bot/`: long polling, `/start`, 3 savol, uz/ru/en | Bot savollarni beradi, javoblar `farmers` da |
| **S6** ✅ | Mini App (Vercel) + poligon qabul qilish | `fields` da GeoJSON va maydon |
| **S7** ✅ | `AnalysisWorker` + hisobot yuborish | Fermer botda rasm va matn oladi |
| **S8** | 2-qism savollar + "Bog'laning" → lead | `farmers.lead_status = REQUESTED`, audit yozuvi |
| **S9** ✅ | Xodimlar (admin panel) API'si (`@PreAuthorize`) | AGRONOMIST ko'radi, USER 403 oladi |
| **S10** | Docker Compose + deploy | Serverda hammasi ishlaydi |
| **S11** | Pilot: 5-10 tanish fermer, keyin reklama | Real dalalarda xatolar ro'yxati |

---

## FAZA S0 — Tayyorgarlik

### S0.1 Skeletni moslash

Skelet allaqachon moslangan: paket `uz.app.agro`, `rootProject.name = 'agro'`.

```bash
createdb agro
cp .env.example .env          # DB_USERNAME / DB_PASSWORD
./gradlew bootRun             # RSA kalitlar birinchi build'da avtomatik yasaladi
```

### S0.2 Telegram bot

1. `@BotFather` → `/newbot` → nom va `username` (masalan `suvtejam_bot`).
2. Token'ni **hech qayerga commit qilmang** → `TELEGRAM_BOT_TOKEN` env.
3. `/setdescription`, `/setabouttext`, `/setuserpic` — fermer birinchi ko'radigan narsa.
4. Ikkinchi, **test bot** oching (`suvtejam_dev_bot`). Dev'da u ishlaydi, prod bot'ga tegmaysiz.
   Bitta token bilan ikki joyda long polling qilsangiz, update'lar ikkiga bo'linib ketadi.

### S0.3 Sun'iy yo'ldoshni qo'lda sinab ko'rish

Kod yozishdan oldin https://browser.dataspace.copernicus.eu da o'zingiz biladigan
2-3 ta dalani oching, iyul-avgust sanalarida NDVI va Moisture Index qatlamlarini
ko'ring. Bu keyin Python natijalarini solishtirish uchun "haqiqat" bo'ladi.
Shu dalalarning poligonini GeoJSON qilib saqlab qo'ying (https://geojson.io da chizish mumkin) — test uchun kerak.

---

## FAZA S1 — `satellite-service` (Python)

### S1.1 Muhit

```bash
cd satellite-service
python3.12 -m venv .venv && source .venv/bin/activate
pip install fastapi uvicorn[standard] pydantic pystac-client odc-stac rasterio \
            numpy shapely pyproj matplotlib httpx
pip freeze > requirements.txt
```

### S1.2 API shartnomasi

Java va Python o'rtasidagi kelishuv — **o'zgartirsangiz ikkala tomonda o'zgartiring**.

`POST /analyze` (header: `X-Internal-Key: <kalit>`)

```json
{
  "field_id": 42,
  "geometry": { "type": "Polygon", "coordinates": [[[69.21,41.21],[69.22,41.21],[69.22,41.22],[69.21,41.21]]] },
  "crop": "COTTON",
  "season_start": "2026-04-15",
  "season_end": "2026-09-30",
  "compare_previous_year": true
}
```

Javob `200` (haqiqiy javob, Argentina test dalasi, `timeseries` va rasmlar qisqartirilgan):

```json
{
  "field_id": 2,
  "area_ha": 20.53,
  "country_code": "AR",
  "centroid_lat": -33.898, "centroid_lon": -60.5975,
  "scenes_used": 34, "scenes_rejected_cloud": 10,
  "zones": { "good_pct": 59.5, "mid_pct": 12.7, "weak_pct": 27.7, "weak_side": "E" },
  "peak_ndvi": 0.654, "peak_date": "2026-04-10",
  "prev_year_peak_ndvi": 0.74,
  "growing_season": { "from": "2026-02-24", "to": "2026-05-10" },
  "stress_periods": [],
  "water": { "et0_sum_mm": 237.3, "etc_sum_mm": 168.8, "effective_rain_mm": 140.8,
             "irrigation_need_m3_ha": 280, "irrigation_need_low_m3_ha": 200, "irrigation_need_high_m3_ha": 300 },
  "timeseries": [ { "date": "2025-11-01", "ndvi": 0.621, "ndmi": 0.18 } ],
  "recommendations": [ { "code": "WEAK_ZONE", "params": { "weak_pct": 27.7, "side": "E" } },
                       { "code": "BELOW_LAST_YEAR", "params": { "pct": 12 } } ],
  "map_png_base64": "...",
  "chart_png_base64": "..."
}
```

- **Tavsiyalar — kod + parametr, matn EMAS.** Xizmat global: matnni Java fermer tilida
  (`farmers.language`) yasaydi. Kodlar: `WATER_STRESS`, `WEAK_ZONE`, `BELOW_LAST_YEAR`,
  `FIELD_UNIFORM`, `LOW_CONFIDENCE`, `NO_CROP_DETECTED`.
- **Rasmlarda matn yo'q** (faqat raqam, rang, masshtab) — har tilda ishlaydi.
- `growing_season` va `water` — `null` bo'lishi mumkin (ekin topilmadi: shudgor, yalang'och yer).
- `weak_side`: `N, NE, E, SE, S, SW, W, NW, CENTER` yoki `null`.

Xatolar — `{"code": "...", "params": {...}}`:
`422` — poligon yaroqsiz (`SELF_INTERSECTING`, `AREA_TOO_SMALL`, `AREA_TOO_LARGE`, `TOO_MANY_VERTICES`,
`OUT_OF_RANGE`, `CROSSES_ANTIMERIDIAN`, ...); `404` — `NO_SCENES`; `502` — `UPSTREAM` (Earth Search / Open-Meteo).
Java `422` va `404` ni fermerga tushunarli xabarga aylantiradi, `502` da qayta urinadi.

### S1.3 Poligon validatsiyasi (birinchi qilinadigan ish)

| Tekshiruv | Chegara | Nega |
|---|---|---|
| `shapely` `is_valid` | majburiy | O'z-o'zini kesgan poligonda maydon noto'g'ri |
| Maydon | 0,5 – 500 ga | 0,5 ga dan kichigida piksel juda kam (10 m piksel → 1 ga = 100 piksel) |
| Uchlar soni | ≤ 200 | `sendData` 4096 bayt chegarasi |
| Koordinatalar | lon −180…180, lat −60…75 | Xato nuqtalar. Sentinel-2 qishloq xo'jaligi hududlarini shu oraliqda qamraydi |
| Antimeridian | poligon 180° ni kesib o'tmasin | `shapely` va `odc-stac` bbox noto'g'ri bo'ladi; MVP'da rad etish yetarli |

Maydonni **UTM proyeksiyasida** hisoblang (graduslarda emas). Zona dala markazidan,
yarim shar kenglikdan aniqlanadi:

```python
def utm_epsg(lon: float, lat: float) -> int:
    zone = int((lon + 180) // 6) % 60 + 1          # lon = 180 da 61 bo'lib ketmasin
    return (32600 if lat >= 0 else 32700) + zone   # 326xx — shimol, 327xx — janub
```

**Mamlakat:** Python poligon markazini Natural Earth chegaralari (`ne_10m_admin_0_countries`,
xizmat ichida fayl sifatida) bilan solishtirib `country_code` (ISO 3166-1 alpha-2) qaytaradi.
Tashqi geocoding API'si kerak emas, kvota va kalit yo'q.

### S1.4 Asosiy tahlil — `app/analysis.py`

```python
import numpy as np
import odc.stac
from pystac_client import Client
from rasterio.features import geometry_mask
from shapely.geometry import shape, mapping
from shapely.ops import transform
from pyproj import Transformer

EARTH_SEARCH = "https://earth-search.aws.element84.com/v1"
COLLECTION = "sentinel-2-c1-l2a"
SCALE, OFFSET = 0.0001, -0.1          # kolleksiya metadata'sidagi raster:bands qiymatlari
BAD_SCL = {0, 1, 3, 8, 9, 10, 11}     # nodata, saturated, bulut soyasi, bulut (o'rta/yuqori), cirrus, qor

def load_field(geom_wgs84: dict, start: str, end: str, epsg: int):
    poly = shape(geom_wgs84)
    items = list(Client.open(EARTH_SEARCH).search(
        collections=[COLLECTION],
        intersects=geom_wgs84,
        datetime=f"{start}/{end}",
        query={"eo:cloud_cover": {"lt": 60}},   # sahna bo'yicha; dala ustidagi bulutni SCL hal qiladi
    ).items())
    if not items:
        return None, None
    ds = odc.stac.load(
        items,
        bands=["red", "nir", "swir16", "scl"],
        bbox=poly.buffer(0.001).bounds,          # dala atrofida ~100 m zaxira
        crs=f"EPSG:{epsg}",
        resolution=10,                           # swir16 (20 m) 10 m ga qayta namunalanadi
        groupby="solar_day",
    )
    to_utm = Transformer.from_crs(4326, epsg, always_xy=True).transform
    poly_utm = transform(to_utm, poly).buffer(-10)   # chetdagi 1 piksel (ariq, yo'l) tashlanadi
    inside = ~geometry_mask([mapping(poly_utm)],
                            out_shape=ds.odc.geobox.shape,
                            transform=ds.odc.geobox.affine)
    return ds, inside

def indices(ds, inside):
    red = ds.red.where(ds.red != 0) * SCALE + OFFSET
    nir = ds.nir.where(ds.nir != 0) * SCALE + OFFSET
    swir = ds.swir16.where(ds.swir16 != 0) * SCALE + OFFSET
    clear = ~ds.scl.isin(list(BAD_SCL)) & inside
    ndvi = ((nir - red) / (nir + red)).where(clear)
    ndmi = ((nir - swir) / (nir + swir)).where(clear)
    # Dalaning 70% dan kami ochiq bo'lgan sanalarni tashlaymiz
    clear_frac = clear.sum(("y", "x")) / inside.sum()
    good_dates = clear_frac >= 0.7
    return ndvi.sel(time=good_dates), ndmi.sel(time=good_dates), int((~good_dates).sum())
```

> ⚠️ **Tuzoq: `OFFSET = -0.1`.** Collection 1 ma'lumotlarida qiymat `DN × 0.0001 − 0.1`.
> Offset unutilsa NDVI jimgina siljiydi, ayniqsa yalang'och tuproqda. Qiymatni
> kolleksiya metadata'sidan (`item_assets.red.raster:bands`) tekshirib turing.
>
> ⚠️ **`odc.stac.load` parametrlari versiyaga qarab o'zgaradi** — o'rnatgan versiyangiz
> hujjatini tekshiring. Birinchi ishga tushirishda kichik dala va qisqa sana oralig'i bilan sinang.

### S1.5 Zonalar va stress

```python
def zones(ndvi):
    # Mavsum cho'qqisidagi (90-persentil) har piksel qiymati, dala medianasiga nisbatan
    peak = ndvi.quantile(0.9, dim="time")
    med = float(peak.median())
    rel = peak / med
    total = int(rel.notnull().sum())
    good = float((rel >= 0.95).sum()) / total * 100
    weak = float((rel < 0.85).sum()) / total * 100
    return peak, {"good_pct": round(good, 1), "mid_pct": round(100 - good - weak, 1),
                  "weak_pct": round(weak, 1)}
```

**Nega nisbiy chegara:** paxta va bug'doyning mutlaq NDVI'si har xil, nav va ekish
sanasiga bog'liq. Dalaning o'z medianasiga nisbatan solishtirish **dala ichidagi
notekislikni** ko'rsatadi — bu aynan notekis sug'orishning izi.

**Suv stressi davri:** ketma-ket 2+ sanada NDMI dala medianasi oldingi sanaga nisbatan
tushib ketgan va NDVI o'smayotgan davrlar. Boshlanishiga oddiy qoida bilan qiling, pilot
dalalardagi fermer gaplari bilan sozlang.

### S1.6 Suv ehtiyoji — `app/weather.py`

Open-Meteo arxivdan kunlik `et0_fao_evapotranspiration` (mm) ni olasiz:

```
https://archive-api.open-meteo.com/v1/archive?latitude=41.21&longitude=69.21
  &start_date=2026-04-15&end_date=2026-09-30
  &daily=et0_fao_evapotranspiration,precipitation_sum&timezone=auto
```

`ETc = ET0 × Kc`. MVP'da Kc — FAO-56 jadvalidan, ekin bosqichlari bo'yicha
(paxta uchun taxminan: boshlang'ich 0,35 → o'rta 1,15–1,20 → oxiri 0,70; aniq qiymat va
bosqich uzunliklarini FAO-56, 11- va 12-jadvallardan oling).
Birlik: **1 mm = 10 m³/ga**.

Keyingi qadam: Kc ni jadvaldan emas, **NDVI'dan** hisoblash (dala haqiqiy rivojlanishini
hisobga oladi). Adabiyotda NDVI → Kcb chiziqli bog'lanishlari bor; pilot ma'lumotida sinab tanlang.

> Fermerga **"taxminiy"** deb ko'rsating va oraliq bering (masalan 7 000–8 500 m³/ga).
> Aniq son ishonch uyg'otadi, lekin xato bo'lsa ishonchni yo'qotadi.

### S1.7 Rasmlar — `app/render.py`

- **Xarita:** `peak / med` massivini 3 rangga bo'lib (`matplotlib`, `ListedColormap`
  yashil/sariq/qizil), poligon chegarasi bilan PNG. O'lcham ~800×800, Telegram uchun yetarli.
- **Grafik:** NDVI vaqt qatori, 2026 va 2025 ikki chiziq. O'qlar o'zbekcha.
- Ranglarni rang-ko'r odamlar ham ajrata oladigan qiling (yashil-qizil o'rniga yashil-to'q sariq-binafsha ham bo'ladi) va rasm ostida legenda yozing.

### S1.8 FastAPI — `app/main.py`

```python
import hmac, os
from fastapi import FastAPI, Header, HTTPException

app = FastAPI()
INTERNAL_KEY = os.environ["INTERNAL_KEY"]          # yo'q bo'lsa xizmat ishga tushmasin

def guard(key: str | None):
    if key is None or not hmac.compare_digest(key, INTERNAL_KEY):
        raise HTTPException(status_code=401)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/analyze")
def analyze(req: AnalyzeRequest, x_internal_key: str | None = Header(default=None)):
    guard(x_internal_key)
    ...  # validate → load_field → indices → zones → weather → render → AnalyzeResponse
```

`def` (async emas) qoldiring — raster ishi CPU va bloklovchi I/O, FastAPI uni thread
pool'da ishlatadi. Joriy va o'tgan yil **parallel** yuklanadi (`ThreadPoolExecutor`) — vaqt ~2 barobar qisqaradi.

Ishga tushirish — `python -m app.serve` (uvicorn'ni to'g'ridan-to'g'ri EMAS):

> ⚠️ **`uvicorn --workers N` fail-fast'ni yutib yuboradi.** Asosiy jarayon yiqilgan worker'ni
> qayta-qayta ko'taradi: `INTERNAL_KEY` yo'q bo'lsa konteyner o'lmaydi, osilib turadi (sinovda
> 8 daqiqa). `app/serve.py` ilovani worker'lardan oldin import qiladi → xato bo'lsa exit 1.

### S1 — Tekshirish

```bash
cd satellite-service
python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m app.geometry                  # mamlakat chegaralari → data/ (bir marta)
export INTERNAL_KEY=dev-key-0123456789abcdef       # kamida 16 belgi
.venv/bin/python -m app.serve &
curl -s localhost:8090/health
curl -s -o /tmp/r.json -w "%{http_code} %{time_total}s\n" -X POST localhost:8090/analyze \
  -H 'Content-Type: application/json' -H "X-Internal-Key: $INTERNAL_KEY" -d @test/uz-mirzachul.json
jq '{area_ha, country_code, scenes_used, zones, growing_season, recommendations}' /tmp/r.json
jq -r .map_png_base64 /tmp/r.json | base64 -d > /tmp/map.png   # oching va Copernicus Browser bilan solishtiring
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8090/analyze -H 'X-Internal-Key: xato' -d '{}'   # 401
```

Kutilgan: `area_ha` geojson.io dagi maydonga ±2% mos; zaif zonalar Copernicus Browser'da
ko'rgan joyingizga to'g'ri keladi; vaqt 90 s dan kam.

2026-10-11 dagi o'lchov (Toshkent, laptop): Mirzacho'l 18,8 ga — 61 s (94 sahna/yil),
Argentina 20,5 ga — 26 s; Docker'da 35 s. Test fayllari: `satellite-service/test/`.

---

## FAZA S2 — Flyway migratsiyalar

Skelet `V5` da tugaydi (dev seed — `db/seed/R__dev_users.sql`). Qoidalar skeletdagidek: soft delete → **qisman** unique index,
`ON CONFLICT ... WHERE deleted = FALSE`.

### `V6__create_farmers.sql`

```sql
CREATE TABLE farmers (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY,
    telegram_user_id BIGINT       NOT NULL,
    chat_id          BIGINT       NOT NULL,
    telegram_username VARCHAR(64),
    full_name        VARCHAR(160),
    phone            VARCHAR(20),
    language         VARCHAR(5)   NOT NULL DEFAULT 'uz',
    country_code     VARCHAR(2),     -- ISO 3166-1 alpha-2, poligondan (Python)
    main_crop        VARCHAR(32),
    irrigation_type  VARCHAR(32),   -- FURROW, DRIP, SPRINKLER, OTHER
    water_source     VARCHAR(32),   -- CANAL, OWN_PUMP, WELL, OTHER
    total_area_ha    NUMERIC(10,2),
    source           VARCHAR(64),   -- /start parametri: reklama qaysi kanaldan
    lead_status      VARCHAR(16)  NOT NULL DEFAULT 'NONE',  -- NONE, REQUESTED, CONTACTED, PILOT, REJECTED
    lead_note        TEXT,
    blocked_bot      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_date     TIMESTAMP(6),
    updated_date     TIMESTAMP(6),
    CONSTRAINT pk_farmers PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_farmers_tg_user_active ON farmers (telegram_user_id) WHERE deleted = FALSE;
CREATE INDEX idx_farmers_lead   ON farmers (lead_status) WHERE deleted = FALSE;
CREATE INDEX idx_farmers_source ON farmers (source);
```

### `V7__create_fields.sql`

```sql
CREATE TABLE fields (
    id               BIGINT GENERATED BY DEFAULT AS IDENTITY,
    farmer_id        BIGINT        NOT NULL,
    name             VARCHAR(80)   NOT NULL,
    crop             VARCHAR(32)   NOT NULL,
    boundary_geojson JSONB         NOT NULL,
    area_ha          NUMERIC(10,2),
    centroid_lat     NUMERIC(9,6),
    centroid_lon     NUMERIC(9,6),
    deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_date     TIMESTAMP(6),
    updated_date     TIMESTAMP(6),
    CONSTRAINT pk_fields PRIMARY KEY (id),
    CONSTRAINT fk_fields_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (id)
);

CREATE INDEX idx_fields_farmer ON fields (farmer_id) WHERE deleted = FALSE;
```

### `V8__create_analysis_jobs.sql`

```sql
CREATE TABLE analysis_jobs (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY,
    field_id      BIGINT       NOT NULL,
    kind          VARCHAR(16)  NOT NULL DEFAULT 'FREE',      -- FREE, PAID
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',   -- PENDING, RUNNING, DONE, FAILED
    season_start  DATE         NOT NULL,
    season_end    DATE         NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    next_try_at   TIMESTAMP(6) NOT NULL DEFAULT now(),
    result        JSONB,          -- rasmlar SIZ (analysis_images da)
    error         VARCHAR(500),
    started_at    TIMESTAMP(6),
    finished_at   TIMESTAMP(6),
    created_date  TIMESTAMP(6),
    updated_date  TIMESTAMP(6),
    CONSTRAINT pk_analysis_jobs PRIMARY KEY (id),
    CONSTRAINT fk_analysis_field FOREIGN KEY (field_id) REFERENCES fields (id)
);

CREATE INDEX idx_analysis_queue ON analysis_jobs (next_try_at) WHERE status = 'PENDING';
CREATE INDEX idx_analysis_field ON analysis_jobs (field_id, created_date DESC);
```

`analysis_jobs` **soft delete'siz** — tarix, o'chirilmaydi (skeletdagi `audit_logs` mantiqi).

### `V9__create_bot_states.sql`

```sql
CREATE TABLE bot_states (
    telegram_user_id BIGINT       NOT NULL,
    step             VARCHAR(32)  NOT NULL,
    data             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at       TIMESTAMP(6) NOT NULL DEFAULT now(),
    CONSTRAINT pk_bot_states PRIMARY KEY (telegram_user_id)
);
```

### `V10__seed_agro_permissions.sql`

```sql
INSERT INTO permissions (name, description) VALUES
    ('farmer:read',   'Fermerlarni ko''rish'),
    ('farmer:update', 'Fermer va lead holatini o''zgartirish'),
    ('field:read',    'Dalalarni ko''rish'),
    ('analysis:read', 'Tahlil natijalarini ko''rish'),
    ('analysis:run',  'Tahlilni qayta ishga tushirish')
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO roles (name, description, system_role) VALUES
    ('AGRONOMIST', 'Agronom: fermer, dala, tahlillarni ko''radi', false)
ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN ('farmer:read','farmer:update','field:read','analysis:read','analysis:run');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'AGRONOMIST'
  AND p.name IN ('farmer:read','farmer:update','field:read','analysis:read');
```

> Huquqlar DB'da va har so'rovda o'qiladi (skelet ADR #13) — keyin rolga huquq qo'shish
> uchun deploy kerak emas.

### S2 — Tekshirish

```bash
dropdb --if-exists mig_test; createdb mig_test
for f in $(ls src/main/resources/db/migration/V*.sql | sort -V); do
  psql -q -v ON_ERROR_STOP=1 -d mig_test -f "$f"
done
psql -d mig_test -c "\d farmers" -c "\d analysis_jobs"
psql -d mig_test -c "SELECT r.name, p.name FROM roles r JOIN role_permissions rp ON rp.role_id=r.id JOIN permissions p ON p.id=rp.permission_id WHERE r.name='AGRONOMIST';"
dropdb mig_test
```

---

## FAZA S3 — Entity, repository, service

Skelet konvensiyalari (eslatma): entity `XEntity`, DTO — `record`, controller faqat DTO
qaytaradi, `@Data`/`@AllArgsConstructor` entity'da yo'q, enum'lar **`EnumType.STRING`**,
`@PreAuthorize` service'da.

### S3.1 Enum'lar

`Crop { COTTON, WHEAT, CORN, RICE, ALFALFA, VEGETABLES, ORCHARD, OTHER }`,
`IrrigationType`, `WaterSource`, `LeadStatus`, `JobStatus`, `JobKind`.
Har biri o'z feature paketida (`farmer/entity/`, `analysis/entity/`).

### S3.2 `FarmerEntity`

`BaseEntity` dan meros, `@SQLDelete` + `@SQLRestriction("deleted = false")`
(`UserEntity` dagidek). Maydonlar — V6 ustunlari.

### S3.3 `FieldEntity` — JSONB ustuni

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "boundary_geojson", nullable = false, columnDefinition = "jsonb")
private Map<String, Object> boundaryGeojson;
```

Skeletdagi `AuditLog.details` qanday map qilingan bo'lsa, xuddi shunday qiling —
u allaqachon Boot 4 da ishlashi tekshirilgan.

`farmer` ga `@ManyToOne(fetch = LAZY)`. N+1 tuzog'i: ro'yxat endpoint'larida `@EntityGraph`.

### S3.4 `AnalysisJob` — `BaseEntity` dan **meros olmaydi**

`deleted` ustuni yo'q. `id`, `createdDate`, `updatedDate` ni o'zida e'lon qiling
(`AuditLog` namunasiga qarang). Rasmlar — **alohida `analysis_images` jadvali** va
`AnalysisImage` entity (bog'lanishsiz, `findById(jobId)`).

> ⚠️ `@Basic(fetch = LAZY)` Hibernate **bytecode enhancement**'siz jimgina e'tiborsiz qoladi —
> rasm baytlari har job bilan yuklanardi. Skeletda enhancement yo'q, shuning uchun jadval ajratildi.

### S3.5 Navbatdan olish — `AnalysisJobRepository`

```java
@Query(value = """
        SELECT * FROM analysis_jobs
        WHERE status = 'PENDING' AND next_try_at <= now()
        ORDER BY next_try_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
List<AnalysisJob> lockNextBatch(@Param("limit") int limit);
```

Bu metod faqat **tranzaksiya ichida** chaqiriladi (aks holda qulf darhol yechiladi).

### S3.6 `AuditEvent` ga qo'shimchalar

```
FARMER_REGISTERED, FIELD_CREATED, ANALYSIS_REQUESTED,
ANALYSIS_COMPLETED, ANALYSIS_FAILED, LEAD_REQUESTED, LEAD_STATUS_CHANGED
```

Oxiriga qo'shing (STRING saqlanadi, tartib muhim emas, lekin odat yaxshi).
Fermer `users` da yo'q → `actor(...)` o'rniga `target("FARMER", farmerId)` +
`detail("telegramUserId", ...)`.

### S3 — Tekshirish

`./gradlew bootRun` — `ddl-auto: validate` xatosiz o'tishi kerak. Xato bo'lsa — entity
va SQL'dagi tip/ustun nomi mos emas (skelet 5.3 bo'limi).

---

## FAZA S4 — `satellite/` (Java → Python)

### S4.1 Sozlama

`application.yml`:

```yaml
satellite:
  base-url: ${SATELLITE_URL:http://localhost:8090}
  internal-key: ${SATELLITE_INTERNAL_KEY}
  connect-timeout: 5s
  read-timeout: 150s          # birlik MAJBURIY (skelet 5.5)
```

```java
@Validated
@ConfigurationProperties(prefix = "satellite")
public record SatelliteProperties(
        @Pattern(regexp = "https?://.+") String baseUrl,
        @Pattern(regexp = "[A-Za-z0-9_\\-]{16,}") String internalKey,   // @NotBlank emas — yechilmagan ${VAR} literal bo'lib keladi
        Duration connectTimeout,
        Duration readTimeout
) {}
```

### S4.2 Klient — `satellite/SatelliteClient`

Kod: `src/main/java/uz/app/agro/satellite/`. Asosiy qarorlar:

- `RestClient` + `JdkClientHttpRequestFactory` (Spring Framework 7.0.9 da tekshirilgan).
- **`HttpClient.Version.HTTP_1_1` MAJBURIY** (pastdagi tuzoq).
- Xatolar ikki turga bo'linadi — worker (S7) shunga qarab qaror qiladi:

| Python javobi | Java exception | Worker |
|---|---|---|
| `422`, `404` → `{"code","params"}` | `SatelliteRejectedException` (`status`, `code`, `params`) | `FAILED`, fermerga xabar, qayta urinmaydi |
| `401` (kalit mos emas) | `SatelliteUnavailableException` + `log.error` | qayta urinadi; 3 dan keyin `FAILED` |
| `5xx`, ulanib bo'lmadi, timeout | `SatelliteUnavailableException` | qayta urinadi |

> ⚠️ **Boot 4 = Jackson 3.** Databind — `tools.jackson.databind.*` (`@JsonNaming`,
> `PropertyNamingStrategies`, `JsonMapper`), annotatsiyalar esa eski paketda qoldi —
> `com.fasterxml.jackson.annotation.*` (`@JsonProperty`, `@JsonIgnoreProperties`).
> Internetdagi `com.fasterxml.jackson.databind` misollari kompilyatsiya bo'lmaydi.

DTO'lar (`satellite/dto/`) — `record`, `@JsonNaming(SnakeCaseStrategy)` **faqat shu
DTO'larda** (global sozlansa skeletning camelCase API'lari buzilardi). Rasmlar — `byte[]`:
Jackson base64 satrni o'zi ochadi. `@JsonIgnoreProperties(ignoreUnknown = true)` — Python
yangi maydon qo'shsa Java yiqilmaydi.

> 🔴 **Tuzoq: JDK `HttpClient` default HTTP/2.** U `Upgrade: h2c` header'i va chunked tana
> yuboradi; uvicorn h2c ni qo'llamaydi va tanani **bo'sh** ko'radi → har so'rov jimgina `422`.
> Xato xabari yo'q — faqat so'rovni ushlab ko'rib topildi.

### S4 — Tekshirish

Vaqtincha `CommandLineRunner` (`@ConditionalOnProperty("satellite.smoke")`) bilan sinalgan, keyin o'chirilgan.
2026-10-11 natijasi:

| Holat | Natija |
|---|---|
| Argentina test dalasi | `OK 45 s`, `area_ha=20.53`, `country=AR`, map 9 KB, chart 34 KB |
| 0,24 ga poligon | `REJECTED 422 AREA_TOO_SMALL {min_ha=0.5, area_ha=0.24}` |
| O'zini kesgan poligon | `REJECTED 422 SELF_INTERSECTING` |
| Boshqa kalit | `UNAVAILABLE satellite 401` + `log.error` |
| Python o'chiq | `UNAVAILABLE ... Connection refused` |
| `SATELLITE_INTERNAL_KEY=` / `${YO_Q}` / `qisqa` | ilova **ishga tushmaydi** (`@Pattern`) |
| `read-timeout` yo'q | ilova ishga tushmaydi (`@NotNull`) |

Python tomonida kalit endi **middleware**da tekshiriladi: endpoint parametrida bo'lsa FastAPI
avval tanani validatsiya qiladi — kalitsiz so'rov `401` emas `422` oladi va API tuzilishini bilib oladi.

---

## FAZA S5 — `bot/` (long polling) ✅ 2026-10-11

Bot: **@agroglobalbot** (hozircha dev uchun; prod'ga chiqishda ALOHIDA bot).
Kutubxona: `com.github.pengrad:java-telegram-bot-api:10.3.0` (Kotlin; `kotlin-stdlib`, OkHttp 5, Gson olib keladi).

| Fayl | Vazifa |
|---|---|
| `bot/TelegramProperties` | `telegram.enabled` / `bot-token` / `miniapp-url`, `@Pattern` |
| `bot/BotConfig` | `TelegramBot` bean — faqat `telegram.enabled=true` |
| `bot/BotRunner` | `SmartLifecycle`: long polling, buyruqlar menyusi (uz/ru/en), 409 ogohlantirishi |
| `bot/BotUpdateHandler` | holat mashinasi, `callback_data` tekshiruvi, MDC |
| `bot/BotSender` | yuborish; HECH QACHON exception tashlamaydi; 403 → `blocked_bot`, 429 → log |
| `bot/BotState(+Repository, Service)`, `BotStep` | `bot_states` |
| `bot/BotKeyboards`, `BotMessages` | tugmalar, `resources/bot/messages{,_uz,_ru}.properties` |
| `farmer/FarmerRegistrationService` | bot uchun yozuvlar (**`@PreAuthorize` yo'q — controller'ga ulanmasin**) |

Holat mashinasi (global — viloyat savoli yo'q, joylashuv poligondan):

```
/start [source] → ASK_CROP → ASK_IRRIGATION → ASK_WATER_SOURCE → WAIT_POLYGON (S6)
```

- `/start` har doim boshidan; `source` faqat BIRINCHI marta (`[A-Za-z0-9_-]{1,64}`).
- Til: Telegram `language_code` → `uz`/`ru`/`en` (boshqasi `en`); `/lang` bilan o'zgaradi.
- Tugma bosilgach savol xabaridan tugmalar olinadi va tanlov yoziladi (ikki marta bosilmaydi).
  Eski xabardagi tugma — qiymat yangilanadi, bosqich siljimaydi.
- Matn yozsa — "tugmani tanlang" + joriy savol qayta (restart'dan keyin ham, holat DB'da).
- Faqat **shaxsiy chat**; guruhdagi xabarlar e'tiborsiz.
- Ism: boshqaruv belgilari (NUL) olib tashlanadi, 160 **code point** (emoji kesilmaydi).

### S5 — tuzoqlar (hammasi sinovda uchradi)

> ⚠️ **O'zbekcha `'` + MessageFormat.** Parametrli xabarda Spring `MessageFormat` ishlatadi,
> oddiy `'` maxsus belgi — "o'zingiz" → "ozingiz". Matnlarda `ʻ` (U+02BB) va `’` (U+2019).
>
> ⚠️ **`Chat.Type.Private`** — katta `P` (`private` Java kalit so'zi).
>
> ⚠️ **`CallbackQuery.message()` emas, `maybeInaccessibleMessage()`** — 48 soatdan eski xabar
> `Message` bo'lmaydi.
>
> ⚠️ **Bitta token — bitta poller.** IntelliJ'dagi ilova + terminaldagi ilova = `409 Conflict`,
> update'lar ikkiga bo'linadi. `BotRunner` buni log'da aniq aytadi.
>
> ⚠️ **Spring Boot BOM Gson'ni 2.14.0 → 2.13.2 ga tushiradi** (pengrad 2.14 bilan yig'ilgan).
> Polling, javoblar va `BotUtils.parseUpdate` sinovda ishladi; pengrad yangilanganda qayta tekshiring.
>
> ⚠️ **`.env` oxirida yangi qator yo'q bo'lsa** `>>` bilan qo'shilgan qator oldingisiga yopishadi
> (`TOKEN=...TELEGRAM_ENABLED=true`) — token buziladi, xato esa faqat 401 bo'lib chiqadi.

### S5 — Tekshirish

Simulyatsiya (vaqtinchalik runner, haqiqiy Telegram JSON → `BotUtils.parseUpdate` → handler), 2026-10-11:

| Holat | Natija |
|---|---|
| `/start kanal_test` (ru) | fermer, `source=kanal_test`, `language=ru`, audit `FARMER_REGISTERED` |
| `c:HACKED`, `l:xx` | e'tiborsiz |
| qayta `/start boshqa_kanal` | `source` o'zgarmadi, `ASK_CROP` |
| guruh chati | bazaga yozilmadi |
| ismda NUL/BEL + 200 emoji, `language_code=de`, `/start ' OR 1=1 --` | tozalandi, 160 belgi, `en`, `source=null` |
| birinchi xabar `/start` emas | baribir ro'yxatdan o'tdi |

Jonli:

```bash
# IntelliJ'da AgroApplication'ni qayta ishga tushiring (.env: TELEGRAM_ENABLED=true)
# Telegram: @agroglobalbot → /start test1 → 3 savol
psql -d agro -c "SELECT telegram_user_id, language, source, main_crop, irrigation_type, water_source FROM farmers;"
psql -d agro -c "SELECT * FROM bot_states;"
# Savol o'rtasida ilovani qayta ishga tushiring → biror matn yozing → bot o'sha savolni qayta beradi
```

---

## FAZA S6 — Mini App (poligon chizish) ✅ 2026-10-11

### S6.1 Mini App — `miniapp/` (Vercel)

| Fayl | |
|---|---|
| `index.html`, `app.js`, `style.css` | chizish: xaritaga bosib burchak qo'yish, nuqtani surish, ↶ orqaga, ✕ tozalash, ◎ joylashuv |
| `vendor/leaflet/` | Leaflet 1.9.4 — **o'zimizda** (CDN emas), sha384 unpkg bilan tekshirilgan |
| `vercel.json` | CSP (`script-src 'self' https://telegram.org`), `frame-ancestors` — Telegram Web, kesh |

- **Geoman ishlatilmadi** (589 KB) — qishloqdagi mobil internet uchun og'ir. O'z chizish ~250 qator,
  Leaflet bilan jami ~165 KB. Telefonda "bos-bos-bos" toolbar'li chizishdan sodda.
- Fon: Esri World Imagery + chegaralar/joy nomlari qatlami (atribut bilan).
- Til: bot URL'ga `?lang=uz|ru|en` qo'shadi (fermer tanlagan til, Telegram interfeysi tili emas).
- Mijoz tekshiruvlari (qulaylik uchun): ≥ 3 nuqta, ≤ 200, chiziqlar kesishmasin, 0,5–500 ga,
  payload ≤ 4000 **bayt** (`TextEncoder` — kirill nom 2 bayt/harf).
- Maydon: sferik formula (turf.area bilan bir xil) — Python/pyproj geodezik qiymatidan **0,1%** farq.
- `tg.disableVerticalSwipes()` (Bot API 7.7+) — chizayotganda pastga surish Mini App'ni yopmasin.
- Telegram tashqarisida — sinov rejimi: MainButton o'rniga oddiy tugma, payload sahifada ko'rinadi.

> 🔴 **Tuzoq (real Telegram'da topildi): Telegram ichidamizmi — `initData` bilan aniqlab BO'LMAYDI.**
> Reply keyboard tugmasidan ochilgan Mini App'da (`sendData` faqat shu yo'lda ishlaydi) `initData`
> **bo'sh**. Natijada Mini App o'zini "brauzerda" deb o'yladi, MainButton chiqmadi, yuborib bo'lmadi.
> To'g'ri belgi — `Telegram.WebApp.platform !== 'unknown'` (SDK'da default `'unknown'`, haqiqiysi
> `tgWebAppPlatform` launch parametridan).
>
> ⚠️ Telegram webview `app.js` ni keshlab qolishi mumkin — `index.html` da `app.js?v=N`, har
> o'zgarishda `N` ni oshiring.

### S6.2 Bot tomoni

- `WAIT_POLYGON` → **reply keyboard** tugmasi (`BotKeyboards.fieldMap`, `isPersistent`).
  `MINIAPP_URL` bo'sh bo'lsa — "tez orada" matni.
- `web_app_data` → `BotPolygonParser` (ISHONCHSIZ kirish: `v=1`, 3–200 nuqta, raqam, diapazon,
  ≤ 4096 bayt, halqani yopadi, nom tozalanadi ≤ 80) → `FieldIntakeService.submit`:
  dala + `FREE` job (oxirgi 12 oy) + audit `FIELD_CREATED`, `ANALYSIS_REQUESTED` — **bitta tranzaksiya**.
- Natija: `QUEUED` → `ANALYZING`, reply tugma olinadi; `FREE_USED` → dala saqlanadi, job yo'q;
  `FIELD_LIMIT` (20 ta dala).
- Mini App maydoniga ishonilmaydi: `area_ha` va aniq markaz — tahlildan keyin Python javobidan (S7).

### S6 — Tekshirish (2026-10-11)

Brauzer (Chrome, lokal server): chizish, maydon 25,9 ga (pyproj: 25,895), bow-tie → "chiziqlar
kesishmasin" va tugma o'chadi, 0,01 ga → "juda kichik", ↶ → 2 nuqtada "kamida 3", `?lang=ru` +
kirill nom → 156 bayt payload.

Bot simulyatsiyasi (brauzerdagi haqiqiy payload + 9 ta buzuq): JSON emas, `v=2`, 2 nuqta,
`lon=200`, `lat=80`, satr koordinata, 250 nuqta, 5000 belgili nom (> 4096 bayt) — hammasi rad
etildi; haqiqiy → `QUEUED` (field + FREE job + 2 audit, `ANALYZING`); yopilmagan uchburchak +
NUL'li nom → yopildi, tozalandi, `FREE_USED` (job yaratilmadi).

```bash
# Deploy (Vercel loyihasi: agro-miniapp, akkaunt diyorbekjoraqulov8)
cd miniapp && npx vercel deploy --prod      # → https://agro-miniapp.vercel.app
# .env: MINIAPP_URL=https://agro-miniapp.vercel.app/  → ilovani qayta ishga tushiring
# Tekshiruv: CSP/HSTS header'lari, leaflet.js sha384 = unpkg, vercel.json/.gitignore → 404
# Telegram: /start → 3 savol → "🗺 Dalani xaritada belgilash" → chizish → Yuborish
psql -d agro -c "SELECT id, farmer_id, name, crop, jsonb_array_length(boundary_geojson->'coordinates'->0) FROM fields;"
psql -d agro -c "SELECT id, field_id, kind, status FROM analysis_jobs;"
# Ikkinchi dalani yuboring → yangi FREE job YARATILMASLIGI kerak
```

---

## FAZA S7 — Tahlil worker'i va hisobot ✅ 2026-10-11

```
AnalysisWorker (@Scheduled fixedDelay 10s, SchedulingConfig → @EnableScheduling)
  → AnalysisJobStore.requeueStuck()     RUNNING > 10 daq → PENDING (ilova o'chgan)
  → AnalysisJobStore.claimBatch()       FOR UPDATE SKIP LOCKED, PENDING → RUNNING, attempts++
  → AnalysisExecutor.run(id)            @Transactional YO'Q
       store.loadInput                  qisqa tx; started_at shu yerda (paketda kutgan vaqt hisoblanmaydi)
       SatelliteClient.analyze          30–90 s, tx TASHQARISIDA
       store.saveSuccess                DONE, result (rasmsiz JSON), analysis_images, field.area_ha/centroid
                                        (Python UTM'dan), farmer.country_code, audit ANALYSIS_COMPLETED
       store.saveRejected               422/404 → FAILED "REJECTED:<kod>", qayta urinilmaydi
       store.saveRetry                  tarmoq/5xx/kutilmagan → PENDING, retry-base × 2^(n-1); max-attempts → FAILED
       AnalysisNotifier (bot)           COMMIT'dan KEYIN; STAFF va blocked_bot — yuborilmaydi
```

- Tranzaksiya chegaralari **alohida bean**da (`AnalysisJobStore`) — ADR #24 (self-invocation).
- `AnalysisNotifier` — interfeys `analysis/` da, implementatsiya `bot/BotAnalysisNotifier`
  (`ObjectProvider` — bot o'chiq bo'lsa worker baribir ishlaydi, natija admin panelda).
- **Bepul kvota: `FAILED` hisoblanmaydi** (`existsFreeJobForFarmer` → `status <> 'FAILED'`) —
  poligon yaroqsiz bo'lsa fermer qayta chizadi.
- Sozlama: `analysis.worker.{enabled, poll-interval, batch-size, max-attempts, retry-base, stuck-after}`.

**Fermerga hisobot** (`BotReportFormatter` — Telegram'ga bog'liq emas, `BotAnalysisNotifier` — yuboradi):
1. 🗺 zonalar xaritasi + izoh: maydon, ekin, zonalar %, zaif tomon, mavsum, NDVI cho'qqisi, o'tgan yil.
2. 📈 grafik + legenda.
3. 💧 sug'orish ehtiyoji oralig'i + hisob asosi + ≤ 3 tavsiya (Python **kodlari** → fermer tili; noma'lum kod o'tkazib yuboriladi).
Xabarlar orasida 1,1 s (Telegram: chatga ~1 xabar/s). Keyin `bot_states = REPORTED`.
Rad etilsa — sabab + xarita tugmasi (`WAIT_POLYGON`); urinishlar tugasa — "texnik nosozlik" (`REPORTED`).

> ⚠️ Raqamlar Java'da formatlanadi (`18,8 ga` / `18.8 ha`, `1 300` / `1,300`) — MessageFormat `{0}` ga
> raqam berilsa server locale'i bo'yicha formatlaydi.

### S7 — Tekshirish (2026-10-11, vaqtinchalik baza, Docker'dagi haqiqiy Python)

| Job | Natija |
|---|---|
| Mirzacho'l, PENDING | `DONE` 74 s: 18,84 ga, UZ, rasmlar `analysis_images` da (7 + 40 KB), `result` JSON'da rasm yo'q, field/farmer yangilandi |
| bow-tie poligon | `FAILED REJECTED:SELF_INTERSECTING`, 1 urinish; fermer bepul kvotasi bo'shadi |
| 20 daq RUNNING'da qotgan | navbatga qaytarildi → `DONE` 34 s, AR |
| Python o'chiq (`retry-base=3s`) | urinish 1 → +3 s, 2 → +6 s, 3 → `FAILED` + audit |

Matnlar uz/ru/en — haqiqiy Python javoblaridan, izohlar 260–282 belgi (< 1024).

```bash
docker compose up -d                      # Python
# IntelliJ: AgroApplication qayta ishga tushiring → 15 s dan keyin worker PENDING job'ni oladi
psql -d agro -c "SELECT id, status, attempts, error, finished_at - started_at AS davom FROM analysis_jobs ORDER BY id DESC LIMIT 5;"
# Python'ni to'xtating (docker compose stop) → job PENDING, attempts oshadi, next_try_at uzayadi
# Ilovani RUNNING paytida o'ldiring (kill -9) → 10 daq dan keyin qayta olinadi
```

---

## FAZA B1 — Tariflar va to'lovlar ✅ 2026-10-11

**Qaror (loyiha egasi):** "bepul limitsiz" belgisi YO'Q. Har fermerda **to'lovlar tarixi**; xodim
admin paneldan "to'lov qildi" deb yozadi. To'lov yo'q — bepul rejim (kichik maydon, 1 tahlil),
pullik — 100 ga gacha.

| Jadval | |
|---|---|
| `plans` (V12) | `FREE` (tizim, to'lovsiz: **20 ga**, 5 dala, 1 tahlil umrbod), `MONTHLY` (30 kun, **100 ga**, 50 dala, cheksiz). Limitlar DB'da — deploysiz o'zgaradi. Soft delete yo'q — `active=false` |
| `farmer_subscriptions` | har to'lov — yozuv: tarif, summa, usul (`CASH/CARD/TRANSFER/OTHER`), `paid_at`, `valid_from..valid_until`, kim yozgani. **O'chirilmaydi** — `CANCELLED` + sabab |

Bugungi tarif (`PlanService.effective`): bugun amal qilayotgan `ACTIVE` obuna → uning tarifi
(tahlillar obuna davrida sanaladi, `PAID` job); yo'q → `FREE` (tahlillar umrbod, `FREE` job).
`FAILED` tahlil kvotani yemaydi.

| Endpoint | Huquq |
|---|---|
| `GET /plans` · `POST /plans` · `PUT /plans/{id}` | `billing:read` · `billing:manage` · `billing:manage` (kod o'zgarmaydi; FREE — faqat nom/limit) |
| `GET /farmers/{id}/plan` | `billing:read` — bugungi tarif + ishlatilgan tahlillar |
| `GET /farmers/{id}/subscriptions` | `billing:read` — tarix |
| `POST /farmers/{id}/subscriptions` | `billing:manage` — **"to'lov qildi"**; bo'sh maydonlar tarifdan, muddat joriy obuna oxiridan davom etadi |
| `POST /subscriptions/{id}/cancel` | `billing:manage` — `{reason}` |
| `GET /subscriptions?status=&planId=&expiringBefore=` | `billing:read` — "muddati tugayotganlar" |

`GET /farmers/{id}` javobida `plan` (bugungi tarif). ADMIN — `billing:read` + `billing:manage`,
AGRONOMIST — `billing:read`. Audit: `PAYMENT_RECORDED`, `SUBSCRIPTION_CANCELLED`, `PLAN_CREATED`, `PLAN_UPDATED`.

**Bot:** maydon limiti **Java'da** (sferik formula, pyproj'dan ~0,1%) — `AREA_LIMIT` da dala
saqlanmaydi, bepul fermerga "pullik tarifda N ga gacha" deyiladi. Mini App limitni `?maxHa=` dan
oladi (faqat UX). Python'ning 500 ga — texnik yuqori chegara (`PlanRequest.maxFieldHa ≤ 500`).

Tekshiruv (vaqtinchalik baza): to'lov → `MONTHLY` 11.10–09.11; ikkinchi to'lov → 10.11–09.12
(davom etdi); FREE'ga to'lov / manfiy summa / kelajak `paidAt` / `until<from` / BITCOIN → 400;
dublikat kod → 409; bekor → `CANCELLED`, qayta → 409. Rol: AGRONOMIST o'qiydi, yoza olmaydi; USER 403.
Bot: bepul 25 ga → `AREA_LIMIT`; 14 ga → `FREE`; ikkinchisi → `QUOTA_USED`; pullik 80 ga → `PAID`;
120 ga → `AREA_LIMIT`; 3-, 4-tahlil → `PAID`.

**Ochiq:** to'lov qilinganda fermerga botda xabar ("Tarif faollashdi, … gacha"); muddat tugashidan
oldin eslatma; onlayn to'lov (Payme/Click) — keyingi bosqich.

---

## FAZA A1 — Admin panel (`admin/`) ✅ 2026-10-11

Vue 3.5 + TypeScript 6 + Vite 8 + Tailwind 4 + shadcn-vue 2.8 (reka-ui, `nova`, zinc + yashil brend).
Ishga tushirish va tuzilish — `admin/README.md`.

Sahifalar (har biri `meta.perm` bilan): Dashboard (`/stats/overview`, `/stats/sources`) · Fermerlar
(filtr, qidiruv) · Fermer (ma'lumot, tarif, lead, dalalar, to'lovlar tarixi, **"To'lov qildi"**, bekor
qilish) · Dalalar · Dala (Leaflet xarita, tahlillar, "Tahlilga yuborish") · Tahlillar · Tahlil
(KPI, zonalar xaritasi, grafik, suv balansi, tavsiyalar, `FAILED` → qayta urinish; `PENDING/RUNNING`
— 5 s da yangilanadi) · Tariflar (tahrirlash/yaratish) · To'lovlar ("7/30 kunda tugaydigan") ·
Xodimlar · Rollar (huquqlar bilan) · Audit · Profil (sessiyalar, yakunlash).

> ⚠️ shadcn-vue `init` (Vite, nova) Inter'ni import qiladi, lekin `--font-sans` ni bermaydi — qo'lda
> qo'shildi. `nova` uslubida `CardHeader` — grid: o'ngdagi ikonka/tugma `CardAction` bilan (`flex-row` ishlamaydi).
> TS 6 shabloni `erasableSyntaxOnly` — konstruktor parametr-xossalari taqiqlangan; `baseUrl` eskirgan.
> Ikonkalar — `@lucide/vue` (`lucide-vue-next` emas).

**Backend'da yo'q (panel faqat ko'rsatadi):** xodim yaratish, xodimga rol biriktirish, alohida
huquqlar ro'yxati (rollar formasi — barcha rollardagi huquqlar birlashmasi).

---

## FAZA B2 — Bot: ko'p dala, sodda til, fayllar ✅ 2026-10-11

**Doimiy asosiy menyu** (reply keyboard, `isPersistent`): `🗺 Yangi dala` (Mini App) · `📋 Dalalarim` · `🌐 Til`.
Xizmat bir martalik emas — tahlildan keyin ham menyu turadi. Menyu matni har tilda tanilanadi.

**📋 Dalalarim** (inline): ro'yxat → kartochka (ekin, maydon, oxirgi tahlil) → `📊 Hisobot` (saqlangan
natijadan qayta) · `🔄 Qayta tahlil` (tarif kvotasi, "bitta faol tahlil") · `✏️ Chegarani o'zgartirish`
(Mini App eski chegara bilan: `?fieldId=&name=&poly=` — Google polyline 1e6, `bot/Polyline.java` ↔
`app.js decodePolyline`) · `🗑 O'chirish` (tasdiq bilan, soft delete, tarix qoladi).
Har id `callback_data`/`web_app_data`'dan — ISHONCHSIZ: `FieldBotService`, `FieldIntakeService`,
`AnalysisReportService` egalikni tekshiradi (begona → "topilmadi"). Audit: `FIELD_UPDATED`, `FIELD_DELETED`.

**Hisobot sodda tilda** (NDVI/ET₀/mm yo'q): "56% — yaxshi o'smoqda", "gektariga 900–1 300 m³ suv",
"13.05 – 28.05 kunlari ekin suvsiz qiynalgan". Footer **tarifga qarab**: bepul — taklif, pullik —
"Tarifingiz: Oylik · 09.11.2026 gacha". Ostida `📄 PDF hisobot` · `📊 Excel jadval`:
- PDF — Python `POST /report/pdf` (matplotlib, DejaVu Sans; matnlar Java'dan tayyor, emoji'siz).
- CSV — Java: UTF-8 BOM, `;`, CSV injection himoyasi (`=+-@`).

`telegram.polling=false` — instans faqat yuboradi (2-instans / sinov; 409 bo'lmaydi).

Tekshiruv (simulyatsiya, 16 qadam): begona dala kartochkasi/tahriri/o'chirish/hisoboti — rad;
bepul kvota → `QUOTA_USED`; to'lovdan keyin → `PAID`; takror → `BUSY`; faol tahlil paytida tahrir →
`BUSY` (nom o'zgarmaydi); tahrir → `FIELD_UPDATED` + yangi job; o'chirilgan dala → "topilmadi".

> ⚠️ PDF: sarlavha sahifa oxirida yolg'iz qolmasin (`need(...)` sarlavha + 2 qator), sarlavhalardan
> ham emoji olib tashlansin (DejaVu'da yo'q → bo'sh kvadrat).

---

## FAZA B3 — Sug'orish eslatmasi ✅ 2026-10-11

**Qarorlar (loyiha egasi):** eslatma — sug'orish yaqinlashganda (+ haftalik holat); sug'orishni fermer
"✅ Sug'ordim" bilan bildiradi; tuproqni fermer aytadi va SoilGrids bilan tekshiriladi; **hammaga** (bepulga ham).

**Model** (Python `app/water.py`, FAO-56 soddalashtirilgan, **stateless**): oxirgi sug'orishdan bugungacha
`Dr = Dr + ET0·Kc − P_eff` (Open-Meteo forecast: `past_days ≤ 92` + 7 kun prognoz, `timezone=auto`),
`Dr ≥ RAW` — sug'orish. `RAW = p × TAW`, `TAW = tuproq (mm/m) × ildiz (m)` — `irrigation/SoilType`,
`CropWater` (FAO-56 jadval o'rtachalari; pilotda sozlanadi). `Kc` — dalaning NDVI'sidan (saqlangan yoki
tahlilniki ≤ 10 kun; eskisi — Sentinel `/ndvi/latest`, 30 kun, ~14 s). NDVI < 0,2 — ekin yo'q, eslatma yo'q.

| Python | |
|---|---|
| `POST /water/balance` | `{lat, lon, last_irrigated_on, kc, taw_mm, raw_mm}` → bugungi tanqislik, `due`, `days_until`, `irrigate_on`, 3 kunlik yomg'ir, mahalliy sana/offset |
| `POST /soil` | SoilGrids → `SAND/LOAM/CLAY` + qum/loy %. ⚠️ standart chuqurliklar (`0-5/5-15/15-30cm`), `0-30cm` — bo'sh javob |
| `POST /ndvi/latest` | so'nggi 30 kundagi oxirgi ochiq sana NDVI |

**Java** (`irrigation/`): `field_water` (1:1, V13) + `irrigation_events` (tarix). `FieldWaterStore` (tx) /
`IrrigationService` (Python — tx'siz) / `IrrigationReminderJob` (har soat, dala **mahalliy** soati 07:00 —
global). Qaror (`decide`): `DUE` — har kuni sug'orilguncha; `SOON` — 1–2 kun qolganda bir marta;
`STATUS` — 7 kunda bir. Yomg'ir ≥ 10 mm kutilsa — "kechiktirsa bo'ladi".

**Bot:** hisobotdan keyin (sozlanmagan bo'lsa) — tuproq (qumloq/o'rtacha/soz/bilmayman) → SoilGrids bilan
solishtiriladi (farq bo'lsa fermerga muloyim aytiladi, fermer javobi ishlatiladi) → "Oxirgi marta qachon
sug'organsiz?" → keyingi sana. Eslatma ostida `✅ Sug'ordim` · `🔕/🔔`. Dala kartochkasida "Keyingi sug'orish: 15.10".
Admin: `GET /fields/{id}/water` (`field:read`) + dala sahifasida kartochka (nomuvofiqlik belgisi).

> ⚠️ **Jackson `SnakeCaseStrategy` raqamdan oldin `_` qo'ymaydi:** `rainNext3dMm` → `rain_next3d_mm`,
> Python `rain_next_3d_mm` — jimgina o'qilmaydi. Raqamli nomga `@JsonProperty`.

Tekshiruv (Docker'dagi haqiqiy Python/SoilGrids/Open-Meteo/Sentinel): fermer "soz" ↔ SoilGrids "qumoq"
(qum 23%, loy 31%) → nomuvofiqlik qayd etildi; begona fermer callback'lari — o'zgarishsiz; 10–24 kun → STATUS,
22/23/24 kun → "keyingi 17/15/14.10", 25+ kun → DUE, tanqislik TAW'da to'xtaydi (153,8% = TAW/RAW);
`decide` qoidasi 10 holatda; ekin yo'q (NDVI 0,08) → eslatma yo'q; `/ndvi/latest` → 0,43 (14 s).

**Ochiq:** model koeffitsiyentlarini pilot dalalarda (fermer gapi, sug'orish jurnali) sozlash;
ko'p dalada Open-Meteo limiti (bepul ~10 000 so'rov/kun) — keshlash yoki tijorat kalit.

---

## FAZA B4 — Har dalaning o'z ekini, hudud nomi, sodda matnlar ✅ 2026-10-11

- **Ekin — DALANIKI, fermerniki emas.** Bitta xo'jalikda g'alla, paxta, poliz — har biri alohida dala
  (bitta poligonda aralash ekin bo'lsa tahlil zonalari ma'nosiz). Mini App'da nom yonida ekin tanlovi
  (boshlang'ichi — fermerning asosiy ekini, `?crop=`); `/start` dagi savol endi "asosan qaysi ekin".
  Dala kartochkasida `🌱 Ekinni o'zgartirish` — almashlab ekish (g'alladan keyin mosh/poliz) chegarani
  qayta chizmasdan. **V14:** `analysis_jobs.crop` — tahlil paytidagi ekin (eski hisobot buzilmaydi).
- **Default nom → hudud nomi.** Poligon tayyor bo'lganda Mini App OpenStreetMap **Nominatim**'dan
  (CORS ochiq, kalitsiz) "qishloq, tuman" oladi: "Uray, Jomboy tumani". Fermer o'zi yozsa — tegilmaydi.
  Shartlar: ≤ 1 so'rov/s, Referer — so'rov faqat poligon tayyorda, ~1 km keshi; `vercel.json`:
  `connect-src` + `Referrer-Policy: strict-origin-when-cross-origin`. Ko'p foydalanuvchida — o'z geocoder'i.
- **Matnlar:** "suv stressi", "taxminiy suv ehtiyoji" o'rniga — "ekin qaysi kunlari suvsiz qolgan",
  "dalangizga qancha suv kerak". "Taxminan" olib tashlandi, lekin "aniq" yozilmadi: raqamlar model
  hisobi (±15%) — oraliq bilan beriladi; "aniq" deb aytilsa, fermer o'z hisobi bilan solishtirib ishonchni yo'qotadi.
  Bot profili (nom/tavsif) — ilova qayta ishga tushganda o'zi yangilanadi.

---

## FAZA B5 — Fikr va yordam (murojaatlar) ✅ 2026-10-11

**Maqsad:** fermer botning o'zida muammo, taklif yoki savol yozadi; xodim admin paneldan javob beradi,
javob bot orqali fermerga boradi.

- Bot: menyuda `💬 Fikr va yordam` (+ `/support`) → tur (🐞 muammo / 💡 taklif / 🌱 maslahat-savol)
  → `FEEDBACK_TEXT` → matn yoki rasm + izoh. Oldingi bosqich `bot_states.data.prevStep` da eslab qolinadi
  va yuborilgach/bekor qilingach qaytariladi (savol o'rtasida bo'lsa — o'sha savol qayta beriladi).
- Menyu tugmasi murojaat matni bo'lib saqlanmaydi — murojaatdan chiqaradi. Stiker/ovoz — "matn yozing".
- Limit: 24 soatda 10 ta (`FeedbackBotService.DAILY_LIMIT`). Boshqaruv belgilari (NUL) tozalanadi, ≤ 4000 belgi.
- Rasm Telegram'da qoladi (`photo_file_id`), admin panel `GET /feedback/{id}/photo` orqali oladi.
- `TELEGRAM_SUPPORT_CHAT_ID` (ixtiyoriy) — yangi murojaat xodimlar guruhiga ham tushadi.
- V15 `feedback` + huquqlar `feedback:read`, `feedback:reply` (ADMIN, AGRONOMIST).
- API: `GET /feedback`, `/feedback/counts`, `/feedback/{id}`, `/feedback/{id}/photo`,
  `POST /feedback/{id}/reply` (faqat NEW; `replyDelivered=false` — botni bloklagan), `POST /feedback/{id}/close`.
- Javob: saqlash (commit, `FeedbackStore`) → Telegram (tranzaksiyadan tashqarida) → `reply_delivered`.
- Admin panel: "Murojaatlar" sahifasi, sidebar'da javobsizlar soni.

Ochiq: ovozli xabar (fermerlar ko'p ishlatadi) — `voice.file_id` + admin panelda `<audio>`.

---

## FAZA B6 — Murojaat → suhbat (chat) ✅ 2026-10-12

**Maqsad:** bitta javob bilan yopilmasin — fermer va operator ikki tomondan biri yakunlaguncha yozishadi;
bir suhbatni faqat bitta operator oladi; yakunda fermer operatorni baholaydi.

- Holatlar: `NEW` (navbat) → `OPEN` (operator oldi, `assignee_id`) → `CLOSED` (fermer / xodim / tizim).
  Fermerda bitta ochiq suhbat — `uq_feedback_farmer_active` (qisman unique index).
- V17: `feedback_messages` (FARMER / STAFF / SYSTEM), eski `reply*` ustunlari xabarlarga ko'chirildi va
  o'chirildi (eski ANSWERED → CLOSED). Yangi huquq `feedback:manage` (ADMIN); OPERATOR roliga read/reply.
- **Claim atomik:** `UPDATE ... WHERE status = 'NEW'` — ikki operator bir vaqtda bossa, ikkinchisi 409.
  `feedback:manage` boshqa operatordagi suhbatni o'ziga ola oladi va istalgan suhbatga yoza oladi.
  Ko'rinish: manage'siz xodim — navbat + o'z suhbatlari; begonasi 404 (ADR #16).
- Bot: murojaat yuborilgach `FEEDBACK_CHAT` — keyingi matn/rasm suhbatga (qabul — 👌 reaksiya).
  `✅ Suhbatni yakunlash` / `🏠 Asosiy menyu` (suhbat ochiq qoladi). Rejimdan tashqarida yozilgan matn ham
  ochiq suhbatga qo'shiladi. Operator xabari ostida `✍️ Javob yozish` / `✅ Yakunlash`. Yopilganda — `1–5 ⭐`.
- Limitlar: yangi murojaat 10/24 soat, fermer xabarlari 150/24 soat.
- API: `GET /feedback?status&mine&assigneeId&rating&q`, `/counts` (NEW, MINE, MINE_UNREAD, OPEN),
  `/operators`, `/{id}/messages?after=`, `/{id}/messages/{mid}/photo`,
  `POST /{id}/claim`, `/{id}/messages`, `/{id}/read`, `/{id}/close`. Audit: `FEEDBACK_CLAIMED`, `FEEDBACK_CLOSED`.
- Admin panel: Navbat (kartochkalar, kutish vaqti rangli) · Mening chatlarim (messenger) · Barcha chatlar
  (manage) · Chatlar tarixi (operator statistikasi, baho filtri). Yangilanish — polling (chat 4 s, ro'yxat 8 s,
  fon tabda siyrakroq); brauzer tab sarlavhasida `(n)` — navbat + o'qilmaganlar.

Ochiq: uzoq jim turgan OPEN suhbatni avtomatik yopish (masalan 7 kun) va operatorga Telegram bildirishnomasi.

---

## FAZA B7 — Fayl servisi (serverdagi papka) ✅ 2026-10-12

**Maqsad:** chatda rasm/PDF ikki tomonga; fayl bizning serverda, Telegram'ga bog'liq emas.

- Papka: `app.files.dir` (`FILES_DIR`, default `storage/files` — loyiha ichida, `.gitignore`da). Prod'da —
  backup qilinadigan disk/volume. Yo'l: `yyyy/MM/<hashId>.<ext>`.
- V18 `files`: `hash_id` (128 bit tasodifiy, base64url 22 belgi — tashqariga faqat shu chiqadi), `path`
  (papkaga NISBIY), nom, tur, hajm, sha256, eni/bo'yi, kim yukladi. Xabarda — `feedback_messages.file_hash_id` (FK).
- Xavfsizlik: tur **magic bytes** bo'yicha (JPG/PNG/PDF oq ro'yxati), ≤ 10 MB (413), rasm **qayta kodlanadi** —
  EXIF/GPS o'chadi, orientatsiya qo'llanadi, > 4096 px kichraytiriladi, > 50 Mpx rad. Nom tozalanadi
  (path traversal), disk yo'li ildizdan chiqmasligi tekshiriladi, atomik yozish (tmp → move).
- API: `POST /files` (multipart `file`, `file:upload`) → `FileResponse`; `GET /files/{hashId}` (kontent,
  `file:read`; rasm inline, PDF attachment, `nosniff`, private kesh) ; `GET /files/{hashId}/info`.
- Chat: `POST /feedback/{id}/messages {text?, fileHashId?}` — faqat o'zi yuklagan STAFF fayl. Bot rasmni izoh
  bilan (`sendPhoto`), PDF'ni `sendDocument` bilan yuboradi. Fermer rasmi/PDF'i kelishi bilan serverga saqlanadi.
- Eski `photo_file_id` xabarlar — ishga tushishda fonda ko'chiriladi (`FeedbackFileBackfill`).
- Admin panel: 📎, Ctrl+V (skrinshot), sudrab tashlash; oldindan ko'rish; xabarda rasm/PDF kartochkasi.

Ochiq: yetim fayllarni tozalash (yuklangan, lekin yuborilmagan), disk backup jadvali, keyin S3'ga o'tish —
faqat `LocalFileStorage` almashadi.

**B7.1 (2026-10-12):**
- Admin → **Fayllar** (`file:manage`, V19, faqat ADMIN): statistika, filtr (nom/hashId, tur, manba, sana),
  panjara/jadval, tafsilot paneli, "qayerda ishlatilgan" (`FileUsageProvider` — har feature o'zi aytadi:
  murojaat, tahlil). Thumbnail: `GET /files/{hashId}/thumb` — 400 px JPEG, birinchi so'rovda yasalib
  `thumbs/` ga keshlanadi. API: `GET /files`, `/files/stats`.
- **Tahlil rasmlari** (xarita, grafik) — V20: `analysis_images` endi hashId saqlaydi, `files.source = SYSTEM`.
  Saqlash tranzaksiyadan oldin; fayl saqlanmasa — zaxira BYTEA. Eski BYTEA fonda ko'chiriladi va bo'shatiladi
  (`AnalysisImageBackfill`). Hammasi ko'chgach — keyingi migratsiyada `map_png/chart_png` o'chiriladi.
- **Lightbox** (`ImageLightbox.vue`): chatda, tahlilda va Fayllar'da rasm sahifaning o'zida katta ochiladi —
  ←/→, Esc, asl o'lcham, yuklab olish.

---

## FAZALAR D1–D3 — Joy topish, sozlanadigan onboarding, bildirishnomalar (2026-10-12)

Loyiha egasi ustuvorligi: "ko'p foydalanuvchi joylashuvini topa olmayapti" → avval xarita.

| Faza | Nima |
|---|---|
| **D1** ✅ | Mini App: avtomatik joylashuv (Telegram LocationManager → brauzer), xarita ustida qidiruv oynasi, global qidiruv (Photon + Nominatim), o'zbek/kirill transliteratsiya, tuman/mahalla chegarasi, "shu atrofdan" qidiruv, qadamlar, brend rangi |
| **D2** ✅ | Onboarding admin'dan (qadamma-qadam xabar/savollar, 3 til), xush kelibsiz — barcha xizmatlar, oxirida inline "🗺 Dalani belgilash" (Mini App → HTTP, initData imzosi) |
| **D3** ✅ | Bildirishnoma servisi (outbox, kanal abstraksiyasi: Telegram → keyin mobil/web), admin: hammaga / filtr / bitta foydalanuvchi |

### D1 ✅
- Oqim: **1 joy → 2 burchaklar → 3 yuborish** (yuqorida qadamlar, pastda maslahat). < 13 zoom'da bosish — "kattalashtiring"
  (tasodifan butun tuman poligon bo'lmasin). Oxirgi joy va so'nggi 6 qidiruv — brauzer xotirasida.
- Qidiruv: **Photon** (yozish jarayonida, xarita markaziga yaqinlari oldin, `lang=default` — o'zbekcha nomlar);
  natija < 3 — o'zbek lotin → rus-lotin (q→k, o'→u…) yoki kirill → lotin varianti; yana kam yoki Enter — **Nominatim**
  (≤ 1 so'rov/s, navbat). Tuman/mahalla (OSM relation/way) — Nominatim lookup bilan chegarasi chiziladi.
- ⚠️ Ommaviy Photon/Nominatim — "fair use". Foydalanuvchi ko'paysa: o'z Photon/Nominatim serveri (Docker) yoki pullik geocoder.
- Brend rangi `#137d41` (admin `--primary`): Telegram sarlavhasi, MainButton, qidiruv oynasi.
- Tuzatildi: global `svg {}` qoidasi Leaflet'ning chizish qatlamini 20px qilardi; `[hidden]` + `display:flex`.
- Juda yaqinlashtirganda xarita yo'qolmaydi: `maxNativeZoom: 17`, `maxZoom: 20` — 17 dan keyin plitka kattalashtiriladi.

### D2 ✅ Onboarding (V22)
- `onboarding_steps`: `code`, `position`, `kind` (MESSAGE | QUESTION), `target` (MAIN_CROP | IRRIGATION_TYPE | WATER_SOURCE —
  savol javobi `farmers` ustuniga ham yoziladi; bo'sh — faqat `farmer_answers` da), `texts`/`options` (jsonb, uz/ru/en).
  Seed: WELCOME (`{name}`), CROP, IRRIGATION, WATER_SOURCE, FINISH (position 1000 — doim oxirida).
- Qoidalar: ichki savollar va FINISH o'chirilmaydi/nofaol qilinmaydi (`system`); javob qiymati variantlardan biri bo'lishi shart.
- Bot: `/start` — to'ldirilgan fermerdan qayta so'ramaydi; chala qolgan — birinchi javobsiz savoldan davom etadi.
  Callback `ob:CODE:VALUE` (eski `c:`/`i:`/`w:` ham ishlaydi). Oxirida FINISH matni + **inline 🗺 tugma** (faqat `PUBLIC_API_URL` bo'lsa).
- Inline web_app tugmasi `sendData` qila olmaydi → Mini App `POST /api/v1/miniapp/fields {initData, data}`:
  `BotInitData` — HMAC("WebAppData", token), `MessageDigest.isEqual`, 24 soat; `miniAppChain` — CSRF yo'q, CORS faqat Mini App origin.
- Admin: **Onboarding** sahifasi — tartib (↑↓), yoqish/o'chirish, 3 til, variantlar, Telegram ko'rinishi. Ruxsat: `onboarding:read|manage`.

### D3 ✅ Bildirishnomalar (outbox, V23)
- 🔴 Qoida: **proaktiv** xabar (hisobot, eslatma, admin javobi, referral, kampaniya) — faqat `NotificationService.enqueue(...)`,
  chaqiruvchi tranzaksiyasida (biznes o'zgarishi bilan atomik). Foydalanuvchi bosganiga darhol javob — to'g'ridan-to'g'ri.
- `notifications`: PENDING → SENDING (`SKIP LOCKED`) → SENT | FAILED | SKIPPED | CANCELLED; backoff 1m/5m/30m/1h,
  `notification.max-attempts`, tezlik `notification.rate-per-second` (Sozlamalar). Bloklagan fermer — FAILED (qayta urinmaydi).
- Kanal — `NotificationChannel` (hozir `BotNotificationChannel`; mobil/web — yangi implementatsiya, chaqiruvchi o'zgarmaydi).
  Shablonlar `Templates` da; kanal o'zi render qiladi (fermer tilida).
- `ref_type/ref_id` — yetkazilgach `NotificationDeliveredEvent` (masalan murojaat xabari "yetkazildi" belgisi, REQUIRES_NEW).
- Kampaniyalar: auditoriya ALL | FILTER (til, ekin, pullik, dalasi bor, faol, davlat, ro'yxat sanasi) | SINGLE;
  `CampaignJob` vaqti kelganini kengaytiradi; bekor qilish — kutayotgan xabarlar CANCELLED.
- Admin: **Bildirishnomalar** — Yangi xabar (oldindan ko'rish + soni) · Kampaniyalar (natija %) · Jurnal (qayta yuborish).
  Fermer sahifasida "Xabarlar" va "Xabar yozish" (fermer tanlangan holda ochiladi). Ruxsat: `notification:read|send`.

---

## FAZALAR C1–C6 — Bot xizmatlari (bo'limlar) va admin nazorati

Reja (2026-10-12, loyiha egasi qarori: 1–7 + ovozli xabar; taklif — 3 do'st = 1 oy bepul):

| Faza | Bo'lim (bot) | Nima | Admin |
|---|---|---|---|
| **C1** ✅ | 🎁 Do'stlarni taklif · ⚙️ Sozlamalar | Taklif havolasi, mukofot, "Mening tarifim", til | Sozlamalar, Takliflar, rollar matritsasi |
| **C2** ✅ | 🌦 Ob-havo | 5 kunlik prognoz + sovuq/issiq/shamol/jala ogohlantirishi | Chegaralar, yuborilgan bildirishnomalar jurnali |
| **C3** ✅ | 📋 Dalalarim → 🛰 Monitoring | Har yangi surat — avtomatik tekshiruv, o'sish pasaysa xabar | Chegaralar, tarif bo'yicha yoqish |
| C4 | 🤖 AI maslahatchi | O'simlik rasmi → AI dastlabki xulosa → agronom tasdig'i | Ko'rib chiqish navbati, aniqlik statistikasi |
| C5 | 📒 Dala daftari · 📊 Hisobotlar | Ishlar jurnali + PDF, suv tejash, tuman bilan taqqoslash | Daftar (dala sahifasi), me'yorlar |
| C6 | 💬 Yordam | Ovozli xabar (operator tinglaydi) | — |

### C2 ✅ 2026-10-13 — Ob-havo xavfi
- Prognoz — dala emas, KATAK (0.1° ≈ 10 km) bo'yicha, `weather_forecasts` keshi; job har soat (:20), katak ≥ 3 soat eskirsa —
  Open-Meteo (50 katak bitta so'rovda). Har soniya EMAS: modellar 1–6 soatda yangilanadi, bepul limit ~10 000/kun.
- Xavflar (admin Sozlamalari → Ob-havo): sovuq ≤ 0° (mart–may, sentyabr–noyabr; janubda teskari), issiq ≥ 40°,
  shamol ≥ 15 m/s, jala ≥ 20 mm yoki do'l. DANGER: −3° / +3° / +5 m/s / ×2. Ufq — bugun + 2 kun.
- Takror yo'q (`weather_alerts` UNIQUE), WARNING → DANGER — bitta "⬆️ yomonlashdi"; issiq — 5 kunda bir marta;
  tunda (21–07, dala vaqti) yuborilmaydi; 12 soatdan eski prognoz bilan ogohlantirilmaydi.
- ⚠️ Bepul Open-Meteo — notijorat. Prod: `OPEN_METEO_URL=https://customer-api.open-meteo.com/v1/forecast` + `OPEN_METEO_API_KEY`.

### C3 ✅ 2026-10-13 — Avtomatik monitoring
- Python `/monitor`: avval faqat STAC metadata (~2 s) — `since` dan keyin surat yo'q bo'lsa COG o'qilmaydi; bor bo'lsa oxirgi
  ochiq surat ≥ 4 kun oldingisi bilan piksel bo'yicha ΔNDVI (~10 s), sustlashgan ulush, tomoni, o'zgarish xaritasi.
- Java: `field_monitoring` (navbat), `monitoring_events` (tarix). Job har soat (:40, server 08–20), 40 tadan;
  yangi surat → 3 kundan keyin, yo'q → ertaga, Python ishlamasa → 2 soatdan keyin.
- Qaror (`Verdict`): ekin yo'q edi (NDVI < 0.25) — NO_CROP; butun dala yalang'och (< 0.2, ≥ 70%) — HARVEST (xabar YO'Q);
  sustlashgan ≥ 15% (admin) — DECLINE → fermerga xarita rasmi + "🔄 Qayta tahlil".
- Admin Sozlamalari → Monitoring: yoqish, faqat pullik (default — ha, Pro'ning asosiy sababi), chegaralar.
  Dala sahifasida "Avtomatik monitoring" kartasi (tarix, xaritalar).
- ⚠️ Python o'zgardi — `docker compose up -d --build satellite-service`.

### C1 ✅ 2026-10-12
- Bot menyusi bo'limli: [Yangi dala][Dalalarim] / [🎁 Do'stlarni taklif][💬 Fikr va yordam] / [⚙️ Sozlamalar].
  Til — Sozlamalar ichida (eski "🌐 Til" tugmasi ham ishlaydi).
- **Taklif:** havola `t.me/<bot>?start=ref_<KOD>` (8 belgi, chalkash harflarsiz). Faqat YANGI fermerga, INSERT'dan
  oldin yoziladi (`referred_by_id` — updatable=false). "Faol" — birinchi tahlili tayyor (`activated_at`, soxta
  akkauntlar sanalmaydi). Mukofot — tahlil commit'idan keyin alohida tranzaksiyada, taklif qiluvchi qatori qulflanadi
  (poyga — ikki marta mukofot yo'q). Obuna `REFERRAL` usuli, summa 0, joriy obunadan keyin davom etadi.
- **Sozlamalar** (`app_settings`, kalitlar — `Setting` enum): admin panelda o'zgaradi, deploysiz. Tur/chegara
  tekshiriladi, audit `SETTING_CHANGED`; ishlamay qolgan qiymat (masalan nofaol tarif) — ogohlantirish.
- **Rollar (V21, faqat qo'shiladi):** MANAGER — fermer/dala/tahlil/billing/takliflar; AGRONOMIST — +tahlil ishga
  tushirish; OPERATOR — +fermer/dala/tahlil ko'rish; settings:manage — faqat ADMIN.
- Admin UI: shadcn `data-checked:`/`data-open:` variantlari `style.css`da ta'riflandi (Switch, Checkbox ko'rinmas edi).

---

## FAZA S8 — 2-qism savollar va lead

Hisobotdan **keyin** (fermer qiymatni ko'rgach) so'raladi:

1. Bu mavsumda suv yetmay qolgan paytlar bo'ldimi? (Ha, ko'p / Ba'zan / Yo'q)
2. Nasos uchun elektrga oyiga taxminan qancha to'laysiz? (oraliq tugmalari)
3. Siz bilan 10 daqiqa gaplashsak bo'ladimi? → **"📱 Raqamni yuborish"** tugmasi
   (`KeyboardButton.requestContact(true)`).

Kontakt kelganda: `contact.userId()` **fermerning o'z** `telegram_user_id` siga teng
ekanini tekshiring (boshqa odamning kontaktini yuborish mumkin). Teng bo'lsa — `phone`,
`lead_status = REQUESTED`, audit `LEAD_REQUESTED`.

**Xabarnoma:** yangi lead → xodimlar Telegram guruhiga (alohida `chat_id` sozlamasi) qisqa
xabar: ism, mamlakat, ekin, maydon, sug'orish usuli, havola. Shunda lead'ni bir soat ichida
ko'rasiz — bu sotuvda juda muhim.

Javoblar uchun alohida jadval kerak bo'lsa (savollar ko'p o'zgarsa) — `V12__create_survey_answers.sql`
(`farmer_id`, `question_key`, `answer`, `created_at`). Boshida `farmers` ustunlari yetarli.

---

## FAZA S9 — Xodimlar (admin panel) API'si ✅ 2026-10-11

Skeletdagi `apiChain` ichida, `/api/v1` prefiks bilan. Admin panel Python'ga **to'g'ridan-to'g'ri
murojaat qilmaydi** — Python'da foydalanuvchi/rol yo'q, `X-Internal-Key` brauzerga berilmaydi.
Panel → Java (JWT cookie, CSRF, `@PreAuthorize`, audit, navbat) → Python.

| Endpoint | Huquq | Eslatma |
|---|---|---|
| `GET /farmers?country=&leadStatus=&source=&crop=&q=` | `farmer:read` | `PageResponse`; `q` — ism/username/telefon (LIKE metabelgilari ekranlangan) |
| `GET /farmers/{id}` | `farmer:read` | dalalar ro'yxati bilan (GeoJSON'siz) |
| `PATCH /farmers/{id}/lead` | `farmer:update` | `{leadStatus, leadNote}`, audit `LEAD_STATUS_CHANGED` (from/to) |
| `GET /fields?farmerId=&crop=&country=` | `field:read` | GeoJSON'siz |
| `GET /fields/{id}` | `field:read` | `boundaryGeojson` — panel xaritada chizadi |
| `GET /analyses?fieldId=&status=&kind=` | `analysis:read` | natija va rasmlarsiz |
| `GET /analyses/{id}` | `analysis:read` | `result` — Python javobi (rasmlarsiz) |
| `GET /analyses/{id}/map.png`, `/chart.png` | `analysis:read` | faqat `DONE`; `Cache-Control: private, 7 kun` |
| `POST /fields/{id}/analyses` | `analysis:run` | `202`, `kind = STAFF` (fermer kvotasiga kirmaydi); tana bo'sh — oxirgi 12 oy; ≤ 400 kun; faol tahlil bo'lsa `409` |
| `POST /analyses/{id}/retry` | `analysis:run` | faqat `FAILED` → `PENDING`, `attempts = 0`, audit `ANALYSIS_RETRIED` |
| `GET /stats/overview` | `farmer:read` | jami fermer/dala/maydon/lead, tahlillar holati, mamlakatlar |
| `GET /stats/sources?from=&to=` | `farmer:read` | voronka har `source` bo'yicha: kelgan → dala → tahlil → lead, `leadRatePct` |

`V11`: `kind` ga `STAFF` + **bitta dalada bitta faol tahlil** qisman unique index'i.
Service'dagi `exists` tekshiruvi yetarli EMAS — 8 ta parallel `POST` sinovida 6 tasini faqat
DB index ushladi (1 × 202, 7 × 409).

> ⚠️ Rasm endpoint'ida `produces = "image/png"` YO'Q: 404 da `ProblemDetail` (JSON) qaytadi,
> `produces` bo'lsa Spring uni yozolmay 406/500 beradi.

### S9 — Tekshirish (2026-10-11, vaqtinchalik baza, haqiqiy Python natijalari bilan)

| endpoint | ADMIN | AGRONOMIST | USER |
|---|---|---|---|
| barcha `GET` | 200 | 200 | 403 |
| `PATCH /farmers/{id}/lead` | 200 | 200 | 403 |
| `POST /fields/{id}/analyses`, `/retry` | 202 | 403 | 403 |

CSRF'siz `POST` → 403. So'rovlar soni 32 qatorda ham o'zgarmas (3–6, N+1 yo'q).

```bash
# login retsepti — CLAUDE.md 8-bo'lim
curl -s -b $J localhost:8081/api/v1/stats/overview | jq
curl -s -b $J "localhost:8081/api/v1/farmers?leadStatus=REQUESTED" | jq '.content[] | {id, fullName, phone}'
curl -s -o /dev/null -w "%{http_code}\n" -b $J -X POST localhost:8081/api/v1/fields/1/analyses -H "X-XSRF-TOKEN: $CSRF"   # 202
```

**Keyinroq (panel talab qilsa):** fermerlarga ommaviy xabar (bot orqali), haqiqiy rangli
sun'iy yo'ldosh surati / istalgan sana NDVI xaritasi (Python'ga yangi endpoint), CSV eksport.

---

## FAZA S10 — Deploy

### S10.1 `docker-compose.yml` (asosiy qismlar)

```yaml
services:
  db:
    image: postgres:16
    environment: [POSTGRES_DB=agro, POSTGRES_USER=agro, POSTGRES_PASSWORD=${DB_PASSWORD}]
    volumes: [pgdata:/var/lib/postgresql/data]
  backend:
    build: .
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_URL=jdbc:postgresql://db:5432/agro
      - DB_USERNAME=agro
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_PRIVATE_KEY=${JWT_PRIVATE_KEY}
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - TELEGRAM_BOT_USERNAME=suvtejam_bot
      - MINIAPP_URL=https://app.<domen>/miniapp/
      - SATELLITE_URL=http://satellite:8090
      - SATELLITE_INTERNAL_KEY=${SATELLITE_INTERNAL_KEY}
    depends_on: [db, satellite]
  satellite:
    build: ./satellite-service
    environment: [INTERNAL_KEY=${SATELLITE_INTERNAL_KEY}]
    # ports: YO'Q — faqat ichki tarmoq
  nginx:
    image: nginx:stable
    ports: ["443:443", "80:80"]
    volumes: [./miniapp:/usr/share/nginx/html/miniapp:ro, ./nginx.conf:/etc/nginx/conf.d/default.conf:ro]
volumes:
  pgdata:
```

- Python image: `python:3.12-slim` — `rasterio` wheel'i GDAL'ni o'zi bilan olib keladi, lekin
  **`libexpat1` ni apt bilan o'rnatish SHART** (usiz `ImportError: libexpat.so.1`). Image ~900 MB.
- Java image: Java 26 uchun Eclipse Temurin tegi borligini tekshiring.
- HTTPS: Let's Encrypt (certbot) — Mini App uchun majburiy.
- Server: 2 vCPU / 4 GB RAM pilot uchun yetarli. Python xizmati AWS'ning `us-west-2`
  mintaqasidan o'qiydi — server undan qanchalik uzoq bo'lsa, tahlil shuncha sekin, lekin
  dala kichik bo'lgani uchun hajm oz.
- **Prodga chiqishdan oldin** skelet seed foydalanuvchilarini o'chiring (skelet 9-bo'lim).
- Kunlik `pg_dump` zaxira.

### S10.2 Kuzatish

- `/actuator/health` (skeletda bor) + Python `/health` — oddiy uptime monitor (masalan UptimeRobot).
- Log'da har tahlil: `jobId`, davomiylik, `scenes_used`. Haftada bir marta `FAILED` job'larni ko'rib chiqing.

---

## FAZA S11 — Ishga tushirish tartibi

1. **5-10 tanish fermer** (yoki o'zingiz biladigan dalalar) bilan pilot — reklamasiz.
   Har birining natijasini Copernicus Browser va fermerning o'z gapi bilan solishtiring.
2. Bot matnlarini fermerlarning savollari asosida tuzating.
3. Har kanal uchun alohida havola: `t.me/suvtejam_bot?start=kanal_nomi`.
4. 2-3 kanalda kichik post → `GET /stats/sources` bilan natijani o'lchang → yaxshisini kengaytiring.
5. Haftalik ko'rsatkichlar: botga kelgan → savollarni tugatgan → poligon chizgan →
   hisobot olgan → lead. Eng katta yo'qotish qayerda bo'lsa, o'sha qadamni soddalashtiring
   (odatda — poligon chizish).

---

## 5. 🔴 SUVTEJAM TUZOQLARI (skelet 5-bo'limining davomi)

| Tuzoq | Oqibat | Yechim |
|---|---|---|
| C1 kolleksiyada `offset = -0.1` unutilgan | NDVI jimgina siljiydi | `DN × 0.0001 − 0.1`, metadata'dan tekshirish |
| Maydon graduslarda hisoblangan | Maydon butunlay noto'g'ri | UTM da hisoblash (`326xx` shimol / `327xx` janub) |
| Dala cheti (ariq, yo'l) hisobga kirgan | Zaif zona "chetida" chiqadi | Poligonni ichkariga 10 m qisqartirish |
| Sahna bulut foizi past, lekin dala ustida bulut | Bir sanada keskin pasayish | Dala bo'yicha SCL maskasi + 70% ochiq qoidasi |
| `sendData` inline tugmadan | Hech narsa kelmaydi, xato ham yo'q | Faqat reply keyboard tugmasi |
| `sendData` > 4096 bayt | Jimgina yuborilmaydi | Koordinatalarni 6 xonaga yaxlitlash, uchlar ≤ 200 |
| Python chaqiruvi tranzaksiya ichida | Hikari pool tugaydi, butun API qotadi | Claim → (tranzaksiyasiz) HTTP → save |
| Bitta bot token'i dev va prod'da | Update'lar ikkiga bo'linadi | Ikki alohida bot |
| Bot thread'ida MDC tozalanmagan | Log'da boshqa fermerning ID'si | `finally { MDC.clear(); }` |
| Begona kontakt qabul qilingan | Noto'g'ri raqam lead'da | `contact.userId() == from.id()` tekshiruvi |
| Earth Search uzilib qolgan | Fermer javob olmaydi | Qayta urinish + 3 dan keyin xabar; zaxira manba sifatida Copernicus Sentinel Hub API |
| Bug'doy pishib qurishi "suv stressi" deb topilgan | Har bug'doy dalasida soxta ogohlantirish | Stress faqat NDVI ≥ mavsum cho'qqisining 80% bo'lganda |
| Yiliga 2 hosil (bug'doy + soya) | Oyna chetidagi kesilgan mavsum tanlanadi | Bir nechta mavsum topiladi, chetga tegmagan to'liq mavsum afzal |
| `rasterio` slim image'da | `ImportError: libexpat.so.1` | `apt-get install libexpat1` |
| `uvicorn --workers` + env xatosi | Konteyner o'lmay osilib turadi | `python -m app.serve` — avval import, keyin worker'lar |
| JDK `HttpClient` default HTTP/2 → uvicorn | Har so'rov jimgina 422 (tana bo'sh) | `.version(HttpClient.Version.HTTP_1_1)` |
| FastAPI'da kalit endpoint parametrida | Kalitsiz so'rov 422 oladi, API tuzilishi oshkor | Kalit middleware'da, validatsiyadan oldin |
| Shimoliy yarim shar kalendari hamma joyga qo'llangan | Janubiy yarim sharda bo'sh dala, ma'nosiz natija | Oxirgi 12 oy + mavsumni NDVI'dan aniqlash |
| Janubiy yarim sharda `326xx` UTM | Maydon va piksellar noto'g'ri | `utm_epsg` da `lat < 0` → `327xx` |

---

## 6. OCHIQ QARORLAR

- [ ] Ekinlar ro'yxati MVP uchun qaysilar (paxta + bug'doy yetarlimi?)
- [ ] Bepul tahlil chegarasi: fermerga 1 ta yoki dala soniga qarab?
- [ ] Xodimlar uchun UI: hozircha Swagger + `psql` yetarlimi, yoki oddiy admin sahifa kerakmi?
- [ ] Bot tillari: qaysilaridan boshlaymiz (uz, ru, en)? Qolganlari uchun `en`.
- [ ] Ekin ro'yxati global: paxta/bug'doydan tashqari soya, sholi, makkajo'xori ham kerakmi?
- [ ] Shaxsiy ma'lumotlar (telefon, joylashuv) — maxfiylik siyosati matni botda (`/privacy`).
      Global xizmat: GDPR (Yevropa fermerlari) va O'zbekiston "Shaxsga doir ma'lumotlar to'g'risida"gi
      qonunini (server O'zbekistonda bo'lsa yoki o'zbek fermerlari ko'p bo'lsa), ayniqsa ma'lumotlarni
      mamlakat ichida saqlash talabini yurist bilan tekshiring — server joylashuvini shunga qarab tanlang.

---

## 7. KEYINGI BOSQICH (MVP'dan keyin)

1. Har kungi sug'orish tavsiyasi: FAO-56 suv balansi modeli (Open-Meteo prognozi + tuproq turi) — kunlik `@Scheduled` job.
2. Sensorlar: LoRaWAN tuproq namligi + suv oqimi o'lchagich → `sensor_readings` jadvali (TimescaleDB yoki oddiy partitsiyalangan jadval).
3. PostGIS: fazoviy so'rovlar (tuman bo'yicha xarita, qo'shni dalalar).
4. Dala chegarasini avtomatik aniqlash.
5. Webhook'ga o'tish (alohida `SecurityFilterChain`, `X-Telegram-Bot-Api-Secret-Token` header tekshiruvi).
