# HANDOFF — keyingi sessiya uchun (2026-10-26)

> Avval shu faylni, keyin `CLAUDE.md` ni o'qing. Brend — **Dropy**, paket `uz.app.agro` o'zgarmaydi.
> Tushuntirish o'zbekcha, texnik atamalar inglizcha. Avtomatik test yozilmaydi (ADR #39).
> **Ish tartibi:** har fazadan keyin `.claude/agents/` dagi **code-reviewer** va **tester** ni parallel (fonda)
> ishga tushiring, topilmalarni tuzating, tuzatishlarni qayta sinating. Commit — faqat egasi ruxsat bergandan keyin.
> Oldingi HANDOFF (3b, chegirmalar, support bot) — git tarixida.

---

## 1. Yo'nalish o'zgardi (egasi qarori, 2026-10-26)

- **Botga vaqt sarflamaymiz.** Asosiy e'tibor — **backend xizmatlarini kuchaytirish va aniqligi**. Bot/Mini App'ga keyin ulanadi.
- Sinov maydoni — **admin panel**: admin xaritada dala chizadi, tizim uni fermer dalasidek kuzatadi, admin holatini ko'radi.
- **Agronom tasdig'i kutilmaydi** — tavsiyalarni AI yozadi. Kelishilgan chegara: AI umumiy agronomiya darajasida
  ("azotli o'g'it", "fungitsid kerak bo'lishi mumkin"), aniq doza/preparat nomini aytmaydi.
- Haqiqiy muammo — **aniqlik o'lchanmagan**: "shimolda o'sish sust" degan topilma haqiqatga mosmi, bilmaymiz.
- `docs/dala-hisobi-roadmap.md` (H0–H10): H0, H1 bajarildi; H2+ **to'xtatildi**, yangi reja quyida ("L" fazalar).
  Yo'l xaritasini L-rejaga moslab qayta yozish — ochiq ish.

### Yangi reja — "Tahlil laboratoriyasi"

| Faza | Nima | Holat |
|---|---|---|
| **L1** | Admin Lab dala qo'shadi (xaritada chizadi) → fermer dalasi bilan bir xil yo'l: mavsum, birinchi tahlil, monitoring, ob-havo | ✅ review + sinov o'tdi (satellite-service bilan tahlil DONE). Commit — egasi ruxsati |
| L1b | Dala sahifasida **holat tarixi**: NDVI vaqt bo'yicha, monitoring hodisalari, ogohlantirishlar, fermerga ketgan bo'lardi xabarlar. Python `/analyze` ga `debug` bloki: sahnalar ro'yxati (sana, ochiq %, ishlatildi/rad), NDVI xaritasi (`render.ndvi_map` bor, ishlatilmayapti), kuchsiz zona koordinatasi, zona chegaralari | ⏳ |
| L2 | "Aslida nima bor edi" izohi (ground truth) har dala/tahlilga → aniqlik ko'rsatkichi | ⏳ egasidan 3–5 ta holati ma'lum real dala kerak |
| L3 | Motorni kuchaytirish: NDRE (zich ekinda NDVI to'yinadi), ko'p yillik solishtirish, signal/shovqin chegaralari, AI tavsiya qatlami | ⏳ |

## 2. Branch va commit'lar

Branch: **`feature/dala-hisobi`** (main'dan). Commit'lar:
- `bc0310b` — 3b tuzatishlari, Agro Biznes filtri
- `39fe625` — H1: ekin mavsumi (`field_seasons`, V40), ekish sanasi, sug'orish usuli, "Hosilni yig'dim", RAINFED (lalmi)
- `87b787b` — onboarding: lalmi fermerdan suv manbai so'ralmaydi

## 3. L1 — commit qilinmagan (yoki WIP commit'da) o'zgarishlar

**G'oya:** alohida "lab" tizimi emas — admin **haqiqiy dala** qo'shadi. Egasi — tizimning o'z "Lab fermeri".
Shunda mahsulotning o'zi sinaladi va admin'dagi mavjud dala sahifasi (monitoring kartasi, sug'orish, tahlillar) ishlaydi.

| Joy | Nima |
|---|---|
| `V41__lab_fields.sql` | `farmers.lab` (qisman unique `uk_farmers_lab`), Lab fermer (`telegram_user_id=-1`, `chat_id=-1`), BUSINESS obunasi 2099 gacha (tarif yo'q bo'lsa `RAISE EXCEPTION`), `field:write` permission → ADMIN |
| `field/LabFieldService` | `create` (`field:write`; ring tekshiruvi → dala → `seasonService.start(id, crop, sownOn)` → `analysisService.run` (STAFF, `analysis:run` ham kerak) → audit), `delete` (faqat Lab dala, fermer dalasi → 409) |
| `field/Rings` | Ring tekshiruvi bot (`BotPolygonParser`) va admin uchun bitta joyda: `validPoint`, `close`, `name` |
| `field/dto/CreateLabFieldRequest` | `name, crop, coordinates [[lon,lat]], sownOn?, irrigationMethod?` |
| `FieldController` | `POST /fields` (201, FieldResponse), `DELETE /fields/{id}` (204), `GET /fields?lab=true/false` |
| `FieldSeasonService` | `start(fieldId, crop, sownOn)` overload, `validSownOn` |
| `FieldIntakeService` | `applyBoundary(field, ring, areaHa)` — chegara/maydon/markaz (bot ham ishlatadi) |
| `FieldResponse.lab`, `FieldSpecs` lab filtri | |
| Notification | `Recipient.lab`; `NotificationDispatcher` → `store.skipLab` (SKIPPED, "Lab dala — Telegram'ga yuborilmaydi", problems'ga yozilmaydi); `finish(..., problem)` |
| Chiqarib tashlandi | `AudienceQuery` (kampaniya), `StatsRepository` (fermer/dala soni, analysesByStatus), `FarmerSpecs` (fermerlar ro'yxati) |
| `BillingService` | Lab fermerga to'lov yozish va uning obunasini bekor qilish → 409 |
| Admin | `components/app/FieldDrawMap.vue` (bosib chizish), `views/FieldCreateView.vue` (`/fields/new`: xarita, koordinataga borish, GeoJSON qo'yish, nom/ekin/ekish sanasi/sug'orish), `FieldsView` (filtr + "Lab dala" tugmasi), `FieldDetailView` ("Lab dala" badge, ikki bosishli o'chirish), router, api, types, `P.FIELD_WRITE` |

Tekshirilgan: `./gradlew compileJava` ✅, `npx vue-tsc -b` ✅, migratsiyalar toza bazada ✅.
Code-reviewer: 🔴 yo'q; 🟡/🟢 tuzatildi (yuqoridagi `Rings`, `analysisService.run`, migratsiya himoyasi, billing, statistika).
Qolgan 🟢 (ataylab): `FieldSummary` da `lab` yo'q (N+1 bo'lmasin; filtr yetarli); Lab xabarlari "Xabarlar"ni to'ldiradi — keyin filtr.

## 4. Keyingi qadamlar (tartib bilan)

1. ✅ (2026-10-26) tester L1 ni sinadi — hammasi o'tdi. Topilgan eski xato (kampaniya `paid` filtri 500, "ANDEXISTS")
   `AudienceQuery` da tuzatildi — **qayta sinalmagan**: `POST /notifications/campaigns/preview {"type":"FILTER","paid":true}` → 200.
   ⚠️ `V41__lab_fields.sql` egasining `agro` bazasida qo'llangan — **faylni endi o'zgartirmang** (Flyway checksum).
   Kuzatuv: o'zini kesadigan/0 ga poligon ham 201 bilan saqlanadi, tahlil FAILED bo'ladi (admin qo'lda o'chiradi).
   Sinov ro'yxati (tarix uchun):
   migratsiya (lab fermer 1 ta, obuna, `field:write` faqat ADMIN); admin POST → 201, `lab=true`, `field_seasons`, `analysis_jobs` STAFF;
   validatsiya (2 nuqta, 202 nuqta, lat=95, null, bo'sh/81 belgili nom, kelajak/401 kunlik sownOn, 600 ga, noma'lum crop → 400);
   `field:write`siz rol → 403; DELETE lab → 204, fermer dalasi → 409, yo'q → 404; `?lab=` filtri;
   Lab xabari → SKIPPED, problems'ga yozilmaydi; statistika va kampaniya auditoriyasi Lab'ni sanamaydi;
   billing: Lab fermerga to'lov/bekor qilish → 409; bot poligon oqimi (`Rings` refaktori) buzilmagan;
   iloji bo'lsa satellite-service bilan Lab dala tahlili DONE.
2. Egasidan commit ruxsati → commit.
3. L1b spec (qisqa) → kod → review + test.
4. `docs/dala-hisobi-roadmap.md` ni L-rejaga moslash.

## 5. Ochiq savollar egasiga

- L2 uchun 3–5 ta holati ma'lum real dala (koordinata + "qaysi qismi yomon/nima bo'lgan").
- Admin "Xabarlar"da Lab xabarlarini alohida ko'rsatish kerakmi.

## 6. Qo'lda tekshirish (L1)

```bash
./gradlew bootRun                                         # V41 qo'llanadi
psql -d agro -c "SELECT id, full_name, lab FROM farmers WHERE lab;"
# Admin → Dalalar → "➕ Lab dala" → xaritada chizing → Saqlash → dala sahifasi ochiladi
psql -d agro -c "SELECT f.id, f.name, s.sowing, j.kind, j.status FROM fields f
  JOIN farmers fa ON fa.id=f.farmer_id AND fa.lab
  LEFT JOIN field_seasons s ON s.field_id=f.id AND s.status='ACTIVE'
  LEFT JOIN analysis_jobs j ON j.field_id=f.id ORDER BY f.id DESC LIMIT 5;"
psql -d agro -c "SELECT template, status, last_error FROM notifications n JOIN farmers f ON f.id=n.farmer_id AND f.lab ORDER BY n.id DESC LIMIT 5;"
```
