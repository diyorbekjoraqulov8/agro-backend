# HANDOFF — keyingi sessiya uchun (2026-10-28)

> Avval shu faylni, keyin `CLAUDE.md` ni o'qing. Brend — **Dropy**, paket `uz.app.agro` o'zgarmaydi.
> Tushuntirish o'zbekcha, texnik atamalar inglizcha. Avtomatik test yozilmaydi (ADR #39).
> **Ish tartibi:** har fazadan keyin `.claude/agents/` dagi **code-reviewer** va **tester** ni parallel (fonda)
> ishga tushiring, topilmalarni tuzating, tuzatishlarni qayta sinating. **Commit — egasi o'zi qiladi.**
> Oldingi HANDOFF'lar (3b, L1) — git tarixida.

---

## 1. Yo'nalish (egasi qarori, 2026-10-26)

- **Botga vaqt sarflamaymiz** — backend xizmatlari va **aniqligi**. Bot/Mini App keyin ulanadi.
- Sinov maydoni — **admin panel**: admin "Lab dala" chizadi, tizim uni fermer dalasidek kuzatadi.
- **Agronom tasdig'i kutilmaydi** — tavsiyani AI yozadi, umumiy darajada (aniq doza/preparat yo'q).
- Reja va holat — `docs/dala-hisobi-roadmap.md` boshidagi **"L" jadval** (H2+ to'xtatilgan).

| Faza | Holat |
|---|---|
| L1 — Lab dala | ✅ commit `19e3964`, `1cb8096` |
| L1b — holat tarixi + tahlil debug | ✅ commit `caeca7a` |
| L2 — ground truth → aniqlik | ✅ review + sinov — **commit qilinmagan** (egasi qiladi) |
| L3 — motorni kuchaytirish | ⏸ **TO'XTATILGAN — egasi sinayapti** (2026-10-28) |

> ⏸ **Hozirgi holat (2026-10-28):** ish shu yerda to'xtatildi. Egasi real dalalarni admin panelda sinab,
> qaysi qismda kamchilik borligini **aniq ma'lumot bilan** olib keladi. Motorni kuchaytirish (L3) taxmin bilan
> EMAS, shu ma'lumot bilan boshlanadi — egasi kelmaguncha L3 kodiga tegilmaydi.

## 2. L1b — nima qilindi (commit `caeca7a`)

| Joy | Nima |
|---|---|
| Python `analysis.py` | `FieldSeries.scenes` (har sahna: `cloud_pct`, `clear_pct`, `used`), `SEARCH_CLOUD_LT`, `zones()` → `(rel, z, peak)` |
| Python `main.py`, `schemas.py` | `AnalyzeResponse.debug` (`Scene`, `Debug`): sahnalar, chegaralar, mediana, piksellar, sust bo'lak markazi, `ndvi_map_png_base64`. `safe_debug` — xato bo'lsa `None`, tahlil yiqilmaydi |
| Python `snapshot.py` | `core_point()` ajratildi (snapshot + debug umumiy) |
| `V42__analysis_ndvi_image.sql` | `analysis_images.ndvi_file_hash_id` + FK `files` |
| `analysis/` | `AnalysisImages.storeNdvi` (ixtiyoriy, zaxirasiz), `saveSuccess(..., ndviHashId)`, debug PNG result jsonb'dan olib tashlanadi, `GET /analyses/{id}/ndvi.png` |
| `field/FieldHistoryService` | `GET /fields/{id}/history?from&to` (`field:read` **va** `analysis:read`): NDVI (tahlil timeseries — eng yangi tahlil ustun, + monitoring), hodisalar (ANALYSIS, MONITORING, WEATHER — fermer+katak, NOTIFICATION — jobId/fieldId/status.fieldId/WEATHER_ALERT). 6 so'rov. Default — joriy + oldingi mavsum, bitta mavsumda ≥ 12 oy; ≤ 3 yil |
| Admin | `FieldHistoryCard.vue` (Chart.js grafik, hodisalar filtri, davr tanlagich), `AnalysisDetailView` "Tahlil asoslari (debug)" (NDVI xaritasi, sahnalar jadvali), `chart.js` bog'liqligi |

Shu sessiyada yana: Lab dala chizish sahifasi qayta yozildi (`FieldCreateView`, `FieldDrawMap`, `lib/geo.ts`),
xarita `maxNativeZoom: 17` (Esri qishloqda "Map data not yet available"), DB porti 5433 (compose `db`, faqat
`127.0.0.1`), CSP'da Nominatim, `BotKeyboards` → `ObjectProvider<MiniAppToken>` (`TELEGRAM_ENABLED=false` ishlaydi),
tester retseptida `SUPPORT_BOT_POLLING=false` — bular egasi tomonidan commit qilingan.

⚠️ Satellite konteyneri L1b kodi bilan qayta yig'ilgan (`docker compose up -d --build satellite`).
Egasining 8081 ilovasi qayta ishga tushirilganda V42 qo'llanadi.

## 3. Keyingi qadamlar (tartib bilan)

1. **Egasi:** L2 ni commit qiladi, ilovani qayta ishga tushiradi (V43), real dalalarni Lab dala qilib chizadi,
   tahlil DONE bo'lgach "Holat tarixi" → "+ Kuzatuv" va ✅➖❌ baholar → "Aniqlik" sahifasi.
2. **Egasi qaytganda olib keladi** (keyingi sessiya shundan boshlanadi):
   - "Aniqlik" sahifasi natijasi (yoki `curl .../api/v1/accuracy | jq`) — qaysi xulosa turi va qaysi sabab ko'p "Mos emas";
   - har muammoli holat: dala (id yoki GeoJSON), sana, dalada aslida nima bor edi, tizim nima dedi (tahlil #id / monitoring);
   - UI/oqimdagi noqulayliklar.
3. **Keyingi sessiya:** ma'lumotni tahlil qilib, eng ko'p adashayotgan joydan L3 ni boshlash.
   Ma'lumotsiz ko'ringan nomzodlar (tartib — ma'lumotga qarab o'zgaradi):
   - **Sust zonani poligon sifatida saqlash** — hozir faqat eng katta bo'lak markazi; kichik bo'laklar "uzoq" chiqadi,
     L2 o'lchovi taxminiy (`accuracy/Match.java`).
   - **Butun dala muammosi** — zonalar dala medianasiga nisbiy, bir tekis yomon dalani ko'rmaydi (L2'da `RELATIVE_ZONES`).
     Yechim: o'tgan yillar va ekin turi uchun kutilgan NDVI bilan solishtirish.
   - **NDRE** — zich ekinda (paxta, makkajo'xori) NDVI ~0.8 da to'yinadi.
   - Signal/shovqin chegaralari (`GOOD_REL`/`WEAK_REL`, monitoring `drop`), AI tavsiya qatlami — faqat ma'lumot bilan.
4. Backend kesishma tekshiruvi (`Rings`) — egasi "keyinroq" dedi.

## 4. Ochiq savollar / kuzatuvlar

- L2 uchun 3–5 ta holati ma'lum real dala (koordinata + "qaysi qismi yomon / nima bo'lgan / qachon").
- Admin "Xabarlar"da Lab xabarlarini alohida ko'rsatish kerakmi.
- Fayl servisi PNG'ni qayta kodlaydi shekilli (Python 17 KB → saqlangan 51 KB) — barcha tahlil rasmlari. Tekshirish kerak.
- `TELEGRAM_POLLING=false` support botni to'xtatmaydi — sinovda doim `SUPPORT_BOT_POLLING=false`.

## 5. L2 — nima qilindi (commit qilinmagan, 2026-10-28)

| Joy | Nima |
|---|---|
| `V43__accuracy.sql` | `field_observations` (soft delete, lat/lon juftligi CHECK), `finding_reviews` (UNIQUE target, soft delete'siz) |
| `accuracy/` | `ObservationEntity`, `FindingReviewEntity`, `Match` (avtomatik solishtirish qoidalari), `AccuracyService`, `AccuracyController`, dto |
| API | `POST /fields/{id}/observations`, `PUT/DELETE /observations/{id}`, `PUT/DELETE /findings/{ANALYSIS|MONITORING}/{id}/review` (atomik upsert), `GET /accuracy?lab=` — yozish `field:write`, o'qish `field:read`+`analysis:read` |
| History | `OBSERVATION` hodisalari, ANALYSIS/MONITORING `data.review` (8 so'rov) |
| Admin | `ObservationDialog` (+ `FieldPointPicker`), `ReviewButtons`, `FieldHistoryCard` (kuzatuv, baho, grafikda romb), `AccuracyView` (`/accuracy`) |

`Match` qoidalari (taxminiy): tahlil — mavsum cho'qqisi (`peak_date`) kuzatuvga ±45 kun eng yaqini; nuqta sust markazdan
`√(sust ulush maydoni/π) + 30 m` ichida → mos. Butun dala muammosi → NOT_APPLICABLE (zonalar nisbiy — L3 sababi).
Monitoring −21…+7 kun, BASELINE hisobga olinmaydi; DAMAGED — DECLINE yoki HARVEST, BARE — DECLINE yoki NO_CROP.
Cheklov: tahlil faqat eng katta sust bo'lak markazini saqlaydi — kichik bo'laklar "uzoq" chiqishi mumkin.

## 6. Qo'lda tekshirish (L1b, L2)

```bash
./gradlew bootRun                                       # V42
# Admin → Dalalar → Lab dala → "Tahlil" → DONE → "Holat tarixi" kartasi; Tahlil sahifasi → "Tahlil asoslari"
psql -d agro -c "SELECT id, jsonb_array_length(result->'debug'->'scenes') sc, result->>'scenes_used' u,
  result->>'scenes_rejected_cloud' r FROM analysis_jobs WHERE status='DONE' ORDER BY id DESC LIMIT 3;"   # sc = u + r
curl -s -b $J localhost:8081/api/v1/fields/<id>/history | jq '{from, to, ndvi: (.ndvi|length), events: [.events[].type]}'
# L2: Dala → "Holat tarixi" → "+ Kuzatuv" (nuqta = tahlil debug'idagi sust markaz) → Aniqlik sahifasida "Mos"
curl -s -b $J localhost:8081/api/v1/accuracy | jq '{manual, auto, rows: [.rows[] | {d: .observation.observedOn, a: .analysis.reason, m: .monitoring.reason}]}'
```
