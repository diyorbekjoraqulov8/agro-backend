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
2. 3-4 savol: viloyat, ekin, sug'orish usuli, suv manbai.
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
│   └── bot/         BotConfig, BotUpdateHandler, BotStateService, BotMessages, keyboards/
├── satellite-service/           ← Python (Gradle unga tegmaydi)
│   ├── app/main.py  app/analysis.py  app/weather.py  app/render.py  app/schemas.py
│   ├── requirements.txt
│   └── Dockerfile
├── miniapp/
│   └── index.html               ← poligon chizish sahifasi (static)
├── docker-compose.yml
└── docs/
```

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
| **S5** | `bot/`: long polling, `/start`, 4 savol | Bot savollarni beradi, javoblar `farmers` da |
| **S6** | Mini App + poligon qabul qilish | `fields` da GeoJSON va maydon |
| **S7** | `AnalysisWorker` + hisobot yuborish | Fermer botda rasm va matn oladi |
| **S8** | 2-qism savollar + "Bog'laning" → lead | `farmers.lead_status = REQUESTED`, audit yozuvi |
| **S9** | Xodimlar API'si (`@PreAuthorize`) | AGRONOM ko'radi, USER 403 oladi |
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

Javob `200`:

```json
{
  "area_ha": 12.41,
  "scenes_used": 23,
  "scenes_rejected_cloud": 7,
  "zones": { "good_pct": 64.2, "mid_pct": 20.9, "weak_pct": 14.9 },
  "peak_ndvi": 0.71,
  "peak_date": "2026-08-02",
  "prev_year_peak_ndvi": 0.77,
  "stress_periods": [ { "from": "2026-07-18", "to": "2026-08-01", "zone": "south" } ],
  "et0_sum_mm": 912.4,
  "etc_sum_mm": 768.0,
  "water_need_m3_ha": 7680,
  "timeseries": [ { "date": "2026-05-03", "ndvi": 0.21, "ndmi": -0.05 } ],
  "recommendations": [ "Janubiy qism 2 hafta suv stressida bo'lgan: sug'orish oralig'ini qisqartiring" ],
  "map_png_base64": "...",
  "chart_png_base64": "..."
}
```

Xatolar: `422` — poligon yaroqsiz (o'z-o'zini kesadi, juda kichik/katta, O'zbekistondan tashqarida);
`404` — mavsumda yaroqli surat topilmadi; `502` — Earth Search / Open-Meteo javob bermadi.
Java `422` va `404` ni fermerga tushunarli xabarga aylantiradi, `502` da qayta urinadi.

### S1.3 Poligon validatsiyasi (birinchi qilinadigan ish)

| Tekshiruv | Chegara | Nega |
|---|---|---|
| `shapely` `is_valid` | majburiy | O'z-o'zini kesgan poligonda maydon noto'g'ri |
| Maydon | 0,5 – 500 ga | 0,5 ga dan kichigida piksel juda kam (10 m piksel → 1 ga = 100 piksel) |
| Uchlar soni | ≤ 200 | `sendData` 4096 bayt chegarasi |
| Joylashuv | O'zbekiston bbox: lon 55.9–73.2, lat 37.1–45.6 | Tasodifiy/xato nuqtalar |

Maydonni **UTM proyeksiyasida** hisoblang (graduslarda emas). O'zbekiston 41N va 42N zonalarida:

```python
def utm_epsg(lon: float, lat: float) -> int:
    return 32600 + int((lon + 180) // 6) + 1   # shimoliy yarim shar
```

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
  &daily=et0_fao_evapotranspiration,precipitation_sum&timezone=Asia/Tashkent
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
pool'da ishlatadi. `uvicorn app.main:app --host 0.0.0.0 --port 8090 --workers 2`.

### S1 — Tekshirish

```bash
export INTERNAL_KEY=dev-key
uvicorn app.main:app --port 8090 &
curl -s localhost:8090/health
curl -s -o /tmp/r.json -w "%{http_code} %{time_total}s\n" -X POST localhost:8090/analyze \
  -H 'Content-Type: application/json' -H 'X-Internal-Key: dev-key' -d @test/field1.json
jq '{area_ha, scenes_used, zones, peak_ndvi}' /tmp/r.json
jq -r .map_png_base64 /tmp/r.json | base64 -d > /tmp/map.png   # oching va Copernicus Browser bilan solishtiring
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8090/analyze -H 'X-Internal-Key: xato' -d '{}'   # 401
```

Kutilgan: `area_ha` geojson.io dagi maydonga ±2% mos; zaif zonalar Copernicus Browser'da
ko'rgan joyingizga to'g'ri keladi; vaqt 90 s dan kam.

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
    region           VARCHAR(64),
    district         VARCHAR(64),
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
    result        JSONB,          -- rasmlar SIZ (rasm — alohida ustunda)
    map_png       BYTEA,
    chart_png     BYTEA,
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
(`AuditLog` namunasiga qarang). `map_png`/`chart_png` → `byte[]` + `@Basic(fetch = LAZY)`
(ro'yxatda rasm baytlari yuklanmasin).

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

### S4.2 Klient

```java
@Component
public class SatelliteClient {

    private final RestClient rest;

    public SatelliteClient(SatelliteProperties props) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        this.rest = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("X-Internal-Key", props.internalKey())
                .requestFactory(factory)
                .build();
    }

    public AnalyzeResponse analyze(AnalyzeRequest req) {
        return rest.post().uri("/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .onStatus(s -> s.value() == 422, (rq, rs) -> { throw new SatelliteRejectedException("INVALID_POLYGON"); })
                .onStatus(s -> s.value() == 404, (rq, rs) -> { throw new SatelliteRejectedException("NO_SCENES"); })
                .body(AnalyzeResponse.class);
    }
}
```

`AnalyzeRequest`/`AnalyzeResponse` — `record`, maydon nomlari Python bilan **snake_case**
(`@JsonProperty("area_ha")` yoki Jackson'da `SNAKE_CASE` strategiyasini faqat shu klient
uchun sozlang — global qilmang, skeletdagi API'lar camelCase).

> ⚠️ `JdkClientHttpRequestFactory` va `RestClient` Spring Framework 7 da bor, lekin
> import yo'llari va metod nomlarini IDE'da tekshiring — skelet 1-bo'limidagi
> "Boot 4 API'ga ishonchsiz bo'lsang — ayt" qoidasi.

### S4 — Tekshirish

Vaqtincha `CommandLineRunner` (dev profilda) bilan bitta test poligon yuboring, log'da
`area_ha` chiqsin. Keyin runner'ni o'chiring.

---

## FAZA S5 — `bot/` (long polling)

### S5.1 Dependency

```groovy
implementation 'com.github.pengrad:java-telegram-bot-api:10.1.0'   // Maven Central'da oxirgi versiyani tekshiring
```

pengrad Spring'ga bog'liq emas — Boot 4 bilan to'qnashuv xavfi kam. (Spring Boot
starter'li `TelegramBots` kutubxonasi Boot 4 ni qo'llashini alohida tekshirish kerak bo'lardi.)

### S5.2 Sozlama

```yaml
telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
  bot-username: ${TELEGRAM_BOT_USERNAME}
  miniapp-url: ${MINIAPP_URL}        # https:// majburiy
  enabled: true
```

`application-dev.yml` da dev bot token'i. `@Pattern(regexp = "\\d+:[A-Za-z0-9_-]{30,}")` token uchun.

### S5.3 Ishga tushirish

```java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class BotRunner implements SmartLifecycle {

    private final TelegramBot bot;
    private final BotUpdateHandler handler;
    private volatile boolean running;

    @Override public void start() {
        bot.setUpdatesListener(updates -> {
            for (Update u : updates) {
                try { handler.handle(u); }
                catch (Exception e) { log.error("update {} ishlanmadi", u.updateId(), e); }  // bitta xato butun paketni to'xtatmasin
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        running = true;
    }
    @Override public void stop() { bot.removeGetUpdatesListener(); running = false; }
    @Override public boolean isRunning() { return running; }
}
```

> ⚠️ **MDC:** update'lar pengrad'ning o'z thread'ida ishlaydi, `MdcFilter` u yerga
> yetmaydi. `handle()` boshida `MDC.put("requestId", "tg-" + updateId)`,
> `MDC.put("userId", "tg:" + telegramUserId)`, oxirida **`finally { MDC.clear(); }`**
> (skelet 5.6).
>
> ⚠️ **Tranzaksiya:** `handle()` ichidagi har bir yozuv service metodlari orqali
> (`@Transactional` **public** metodlarda). Bot handler'ning o'zida tranzaksiya ochmang —
> Telegram'ga HTTP chaqiruv tranzaksiya ichida bo'lmasin.

### S5.4 Holat mashinasi

```
START ─▶ ASK_REGION ─▶ ASK_CROP ─▶ ASK_IRRIGATION ─▶ ASK_WATER_SOURCE ─▶ WAIT_POLYGON
                                                                              │ (Mini App sendData)
                                                                              ▼
            DONE ◀── ASK_CONTACT ◀── ASK_POWER_BILL ◀── ASK_WATER_SHORTAGE ◀── ANALYZING ─▶ (hisobot yuborildi)
```

- Javoblar — **inline tugmalar** (`callback_data`: `crop:COTTON`). Erkin matn faqat ism uchun.
  Tugma = toza ma'lumot, imlo xatolari yo'q.
- `callback_data` ni ishonchsiz kirish deb hisoblang: enum'ga `valueOf` qilishdan oldin
  tekshiring, noma'lum qiymatda jim e'tiborsiz qoldiring.
- Har step `bot_states` ga yoziladi. `/start` har doim qaytadan boshlaydi.
- `/start kanal1` → `source = kanal1` faqat **birinchi** marta yoziladi (keyingi reklamalar
  atributsiyani buzmasin). Parametrni sanitizatsiya: `[A-Za-z0-9_-]{1,64}`.
- Viloyatlar ro'yxati: 12 viloyat + Qoraqalpog'iston + Toshkent shahri — 2 ustunli tugmalar.

### S5.5 Matnlar

Barcha matnlar `BotMessages` klassida yoki `messages_uz.properties` da — kodga sochilmasin.
Lotin yozuvida, qisqa, "siz"lab. Rus tili — keyinroq, `farmers.language` allaqachon bor.

### S5 — Tekshirish

```bash
psql -d agro -c "SELECT telegram_user_id, region, main_crop, irrigation_type, water_source, source FROM farmers;"
psql -d agro -c "SELECT * FROM bot_states;"
```

Qo'lda: dev botga `/start test1` → 4 savolga javob → `farmers` da qator, `source = test1`.
Ilovani savol o'rtasida qayta ishga tushiring → bot o'sha savoldan davom etadi.

---

## FAZA S6 — Mini App (poligon chizish)

### S6.1 Qanday ochiladi

`WAIT_POLYGON` bosqichida bot **reply keyboard** tugmasi yuboradi:

```java
new ReplyKeyboardMarkup(new KeyboardButton("🗺 Dalani xaritada belgilash")
        .webAppInfo(new WebAppInfo(props.miniappUrl())))
    .resizeKeyboard(true).oneTimeKeyboard(true);
```

> 🔴 **`sendData` faqat reply keyboard tugmasidan ochilgan Mini App'da ishlaydi.**
> Inline tugma yoki menyu tugmasidan ochilsa `sendData` jimgina hech narsa yubormaydi.

### S6.2 `miniapp/index.html` — tarkibi

- `https://telegram.org/js/telegram-web-app.js`, Leaflet, Leaflet-Geoman (CDN'dan).
- Fon: sun'iy yo'ldosh qatlami (Esri World Imagery, atribut bilan) — fermer dalasini **ko'radi**.
- Ochilganda: `Telegram.WebApp.expand()`; "Mening joylashuvim" tugmasi (`navigator.geolocation`).
- Faqat **bitta poligon** chizishga ruxsat; chizib bo'lgach maydonni ko'rsating
  (Turf.js `turf.area` bilan, ga da) — fermer "12 ga" ni ko'rib to'g'ri chizganini tushunadi.
- Telegram'ning `MainButton` i: "Yuborish" — poligon bo'lmasa o'chiq.
- Yuborish:

```js
const coords = layer.toGeoJSON().geometry.coordinates[0]
  .map(([lon, lat]) => [+lon.toFixed(6), +lat.toFixed(6)]);   // 6 xona ≈ 10 sm, bayt tejaydi
const payload = JSON.stringify({ v: 1, name: fieldName, coords });
if (payload.length > 4000) { Telegram.WebApp.showAlert("Chegara juda murakkab, kamroq nuqta bilan chizing"); return; }
Telegram.WebApp.sendData(payload);   // Mini App yopiladi, botga web_app_data keladi
```

### S6.3 Bot tomonida qabul qilish

`message.webAppData().data()` → JSON parse → **server tomonda qayta validatsiya**
(fermer kodni o'zgartira oladi): uchlar soni ≤ 200, koordinatalar O'zbekiston bbox'ida,
halqa yopilgan (birinchi = oxirgi, bo'lmasa yoping). Maydon va `is_valid` ni Python
hisoblaydi — Java'da faqat arzon tekshiruvlar.

So'ng: `fields` ga yozish + `analysis_jobs` ga `PENDING` job + audit
`FIELD_CREATED`, `ANALYSIS_REQUESTED` — **bitta tranzaksiyada**. Fermerga:
"✅ Dala qabul qilindi. Tahlil 1-2 daqiqa davom etadi."

**Suiiste'mol cheklovi:** bepul tahlil — fermerga **1 ta**. `FREE` job allaqachon bor bo'lsa,
yangisini yaratmang, "Bepul tahlil allaqachon bajarilgan, qo'shimcha dala uchun bog'laning" deng.

### S6.4 Hosting

Mini App — oddiy static fayl, HTTPS shart. Variantlar: o'z serveringizdagi nginx
(`https://app.<domen>/miniapp/`) yoki GitHub Pages. Backend'ning `/api/**` CSP'si
(`default-src 'none'`) Leaflet'ni bloklaydi — shuning uchun Mini App'ni Spring'dan
**bermang**.

### S6 — Tekshirish

Dev bot'da tugmani bosing → chizing → yuboring.

```bash
psql -d agro -c "SELECT id, farmer_id, crop, jsonb_array_length(boundary_geojson->'coordinates'->0) AS nuqtalar FROM fields;"
psql -d agro -c "SELECT id, field_id, status FROM analysis_jobs;"
```

Ikkinchi marta yuboring → yangi `FREE` job **yaratilmasligi** kerak.

---

## FAZA S7 — Tahlil worker'i va hisobot

### S7.1 Worker

```java
@Component
@RequiredArgsConstructor
public class AnalysisWorker {

    private final AnalysisJobClaimer claimer;     // ALOHIDA bean (self-invocation tuzog'i, skelet ADR #24)
    private final AnalysisExecutor executor;

    @Scheduled(fixedDelayString = "${analysis.poll-interval:10s}")
    public void poll() {
        for (Long jobId : claimer.claimBatch(2)) {   // REQUIRES_NEW: RUNNING qiladi, commit, qulf yechiladi
            executor.run(jobId);                      // tranzaksiyasiz: Python'ga uzoq HTTP chaqiruv
        }
    }
}
```

`@EnableScheduling` ni bitta `@Configuration` ga qo'shing.

`executor.run`:
1. Job + dala ma'lumotini o'qiydi (qisqa read-only tranzaksiya).
2. `SatelliteClient.analyze(...)` — **tranzaksiyadan tashqarida** (90 s DB ulanishini band qilmasin, Hikari pool tugab qoladi).
3. Natijani saqlaydi: `DONE`, `result`, rasmlar (qisqa yozuvchi tranzaksiya) + audit.
4. Fermerga yuboradi (S7.2).
5. Xato bo'lsa:
   - `SatelliteRejectedException` (422/404) → `FAILED`, fermerga tushunarli xabar, qayta urinilmaydi.
   - Tarmoq/5xx → `attempts++`, `PENDING`, `next_try_at = now() + 2^attempts daqiqa`;
     3 urinishdan keyin `FAILED` va fermerga "texnik nosozlik, tez orada qayta yuboramiz".

**Ilova o'chib qolsa:** `RUNNING` da 10 daqiqadan ortiq turgan job'larni worker boshida
`PENDING` ga qaytaring (bitta `UPDATE`).

**Mavsum sanalari:** oktabr–mart oralig'ida tahlil qilinsa — o'tgan mavsum
(`aprel 15 – sentabr 30`), bug'doy uchun — `oktabr 1 (o'tgan yil) – iyun 30`.
Bu mantiq `AnalysisService` da, bir joyda.

### S7.2 Fermerga yuborish

1. `sendPhoto` — zonalar xaritasi, caption'da qisqa xulosa (1024 belgi chegarasi):

```
📍 Dala: 12,4 ga · Paxta · Sirdaryo
🟢 Yaxshi 64%  🟡 O'rta 21%  🔴 Zaif 15%
💧 18 iyul – 1 avgust: janubiy qism suv stressida
📉 2025 yilga nisbatan o'simlik holati 8% past
```

2. `sendPhoto` — mavsum grafigi.
3. `sendMessage` — taxminiy suv ehtiyoji (oraliq bilan) + 2-3 tavsiya + inline tugmalar:
   `[📞 Har kungi sug'orish tavsiyasini xohlayman]` `[❓ Savolim bor]`.

`403 Forbidden` (fermer botni bloklagan) → `farmers.blocked_bot = true`, qayta yubormang.
Telegram cheklovi: bitta chatga sekundiga ~1 xabar — 3 ta xabar orasida kichik pauza.

### S7 — Tekshirish

```bash
psql -d agro -c "SELECT id, status, attempts, error, finished_at - started_at AS davomiylik FROM analysis_jobs ORDER BY id DESC LIMIT 5;"
# Python'ni o'chirib qo'ying → job PENDING'da qoladi, attempts oshadi, next_try_at uzayadi
# Python'ni yoqing → keyingi poll'da DONE, fermer hisobotni oladi
# Ilovani RUNNING paytida o'ldiring (kill -9) → qayta ishga tushganda job qayta olinadi
```

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
xabar: ism, viloyat, ekin, maydon, sug'orish usuli, havola. Shunda lead'ni bir soat ichida
ko'rasiz — bu sotuvda juda muhim.

Javoblar uchun alohida jadval kerak bo'lsa (savollar ko'p o'zgarsa) — `V11__create_survey_answers.sql`
(`farmer_id`, `question_key`, `answer`, `created_at`). Boshida `farmers` ustunlari yetarli.

---

## FAZA S9 — Xodimlar API'si

Skeletdagi `apiChain` ichida, avtomatik `/api/v1` prefiks bilan.

| Endpoint | Huquq | Eslatma |
|---|---|---|
| `GET /farmers?region=&leadStatus=&source=` | `farmer:read` | `PageResponse`, `Specification` (`AuditSpecs` namunasi) |
| `GET /farmers/{id}` | `farmer:read` | dalalar ro'yxati bilan |
| `PATCH /farmers/{id}/lead` | `farmer:update` | `leadStatus`, `leadNote` + audit `LEAD_STATUS_CHANGED` |
| `GET /fields/{id}` | `field:read` | GeoJSON bilan |
| `GET /analyses?fieldId=` | `analysis:read` | rasmlarsiz |
| `GET /analyses/{id}/map.png` | `analysis:read` | `image/png`; CSP `default-src 'none'` rasmni yuklab olishga to'sqinlik qilmaydi |
| `POST /analyses/{id}/retry` | `analysis:run` | `FAILED` → `PENDING` |
| `GET /stats/sources` | `farmer:read` | har `source` bo'yicha: kelgan, tahlil olgan, lead → **qaysi kanal pulga arziydi** |

`Permissions` ga konstantalar (`CAN_READ_FARMER = "hasAuthority('farmer:read')"` va h.k.),
`@PreAuthorize` — service metodlarida.

### S9 — Tekshirish

```bash
# AGRONOMIST rolli xodim yarating (POST /users + rol biriktirish), login qiling (skelet retsepti)
curl -s -b $J localhost:8081/api/v1/farmers | jq '.content | length'        # 200
# USER rolli xodim bilan:
curl -s -o /dev/null -w "%{http_code}\n" -b $J2 localhost:8081/api/v1/farmers   # 403
curl -s -b $J localhost:8081/api/v1/stats/sources | jq
```

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

- Python image: `python:3.12-slim` — `rasterio` wheel'lari GDAL'ni o'zi bilan olib keladi,
  odatda alohida GDAL o'rnatish shart emas.
- Java image: Java 26 uchun Eclipse Temurin tegi borligini tekshiring.
- HTTPS: Let's Encrypt (certbot) — Mini App uchun majburiy.
- Server: 2 vCPU / 4 GB RAM pilot uchun yetarli. Python xizmati AWS'ning `us-west-2`
  mintaqasidan o'qiydi — Toshkentdan har tahlil biroz sekinroq, lekin dala kichik bo'lgani
  uchun hajm oz.
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
| Maydon graduslarda hisoblangan | Maydon butunlay noto'g'ri | UTM (`EPSG:326xx`) da hisoblash |
| Dala cheti (ariq, yo'l) hisobga kirgan | Zaif zona "chetida" chiqadi | Poligonni ichkariga 10 m qisqartirish |
| Sahna bulut foizi past, lekin dala ustida bulut | Bir sanada keskin pasayish | Dala bo'yicha SCL maskasi + 70% ochiq qoidasi |
| `sendData` inline tugmadan | Hech narsa kelmaydi, xato ham yo'q | Faqat reply keyboard tugmasi |
| `sendData` > 4096 bayt | Jimgina yuborilmaydi | Koordinatalarni 6 xonaga yaxlitlash, uchlar ≤ 200 |
| Python chaqiruvi tranzaksiya ichida | Hikari pool tugaydi, butun API qotadi | Claim → (tranzaksiyasiz) HTTP → save |
| Bitta bot token'i dev va prod'da | Update'lar ikkiga bo'linadi | Ikki alohida bot |
| Bot thread'ida MDC tozalanmagan | Log'da boshqa fermerning ID'si | `finally { MDC.clear(); }` |
| Begona kontakt qabul qilingan | Noto'g'ri raqam lead'da | `contact.userId() == from.id()` tekshiruvi |
| Earth Search uzilib qolgan | Fermer javob olmaydi | Qayta urinish + 3 dan keyin xabar; zaxira manba sifatida Copernicus Sentinel Hub API |
| Mavsumdan tashqari tahlil (oktabr) "joriy" sanalar bilan | Bo'sh dala, ma'nosiz natija | Mavsum sanasi `AnalysisService` da, ekin bo'yicha |

---

## 6. OCHIQ QARORLAR

- [ ] Ekinlar ro'yxati MVP uchun qaysilar (paxta + bug'doy yetarlimi?)
- [ ] Bepul tahlil chegarasi: fermerga 1 ta yoki dala soniga qarab?
- [ ] Xodimlar uchun UI: hozircha Swagger + `psql` yetarlimi, yoki oddiy admin sahifa kerakmi?
- [ ] Rus tili qachon?
- [ ] Shaxsiy ma'lumotlar (telefon, joylashuv) — maxfiylik siyosati matni botda (`/privacy`).
      O'zbekiston "Shaxsga doir ma'lumotlar to'g'risida"gi qonunini, ayniqsa ma'lumotlarni
      mamlakat ichida saqlash talabini yurist bilan tekshiring — server joylashuvini shunga qarab tanlang.

---

## 7. KEYINGI BOSQICH (MVP'dan keyin)

1. Har kungi sug'orish tavsiyasi: FAO-56 suv balansi modeli (Open-Meteo prognozi + tuproq turi) — kunlik `@Scheduled` job.
2. Sensorlar: LoRaWAN tuproq namligi + suv oqimi o'lchagich → `sensor_readings` jadvali (TimescaleDB yoki oddiy partitsiyalangan jadval).
3. PostGIS: fazoviy so'rovlar (tuman bo'yicha xarita, qo'shni dalalar).
4. Dala chegarasini avtomatik aniqlash.
5. Webhook'ga o'tish (alohida `SecurityFilterChain`, `X-Telegram-Bot-Api-Secret-Token` header tekshiruvi).
