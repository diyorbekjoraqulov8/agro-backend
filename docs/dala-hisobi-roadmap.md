# Dala hisobi — yo'l xaritasi (2026-10-25)

> Maqsad: fermer dalasini kiritgach, **ilova o'zi** dalani kuzatadi, tahlil qiladi va
> kommunal to'lov ilovasidek sodda ko'rinishda aytadi: nima joyida, nima kechikdi, qayerga borish kerak.
> Ish shu fayl bo'yicha **birin-ketin** qilinadi. Har fazadan keyin **code-reviewer** va **tester**
> (memory: `workflow-review-and-test`), so'ng shu fayldagi holat belgisi yangilanadi.

---

## 0. Tamoyillar — har fazada tekshiriladigan qoidalar

1. **Ilova o'zi gapiradi.** Fermer savol bermaydi. AI chat — yordamchi, asosiy yo'l emas.
2. **Har signal = aniq harakat.** "NDVI 0.62" emas → "shimoli-sharqiy burchakka boring", "azot bering".
   Harakati yo'q signal fermerga ko'rsatilmaydi.
3. **Kam, lekin aniq.** Spam bo'lsa fermer botni o'chiradi. Har xabar turi uchun kunlik chegara va dedup.
4. **Sodda til.** Atamasiz, uz/ru/en, keyinchalik ovoz.
5. **Tasdiqlanmagan agronomik maslahat yo'q.** Preparat, doza, muddat — agronom tasdiqlagan
   kontentdan keladi (DB'da, admin tahrirlaydi), koddan yoki AI'dan "o'ylab topilmaydi".
6. **Halollik.** Sun'iy yo'ldosh (Sentinel-2: 10 m, ~5 kun, bulutda ko'rmaydi) kasallik **nomini** aytmaydi —
   faqat "shu yerda nimadir yomonlashdi". Sababni dalaga borish + rasm aniqlaydi.

## Kelishilgan qarorlar (2026-10-26)

1. **Dala qo'shilganda 3 savol:** ekin, ekish sanasi, sug'orish usuli (dala darajasida). Qolgani avtomatik
   (chegara, maydon, hudud, tuproq, ob-havo) yoki kerak bo'lgan paytda so'raladi.
2. **Bepul tarif:** 1-tahlil ochiq — 1 ta muammo to'liq ko'rinadi, qolganlari faqat soni bilan 🔒
   ("qayerda va nima qilish kerak — tarifni yangilang"). Keyin haftasiga bir marta faqat yangi muammolar soni.
3. **Soni faqat haqiqiy.** Muammo yo'q bo'lsa — "dalangiz yaxshi holatda". Soxta teaser yo'q.
4. **AI faqat rasm tashxisida.** Nazorat — formulalar va qoidalar (NDVI zonalari, FAO-56, ob-havo, GDD, kalendar).
5. **Kasallik/zararkunanda — "xavf", "muammo" emas.** Ob-havo/GDD faqat *vaqt*ni aytadi, borligini emas.
   Model "tekshirib ko'ring" deydi; "dorilang" — faqat tasdiqdan keyin (fermer rasmi, qo'shni fermer tasdig'i,
   feromon tutqich soni). Sun'iy yo'ldosh pasayishi — *joy* signali.

## Holat jadvali

| Faza | Nima | Holat |
|---|---|---|
| H0 | Tayyorgarlik: ochiq ishlarni yopish | ✅ 2026-10-26 (`bc0310b`) |
| H1 | Ekin mavsumi: ekish sanasi | ✅ 2026-10-26: review + sinov (11/11, parallel 5/5) |
| H2 | Ekin kalendari (agrotexnika shabloni) | ⏳ |
| H3 | Dala vazifalari + "Bajardim" | ⏳ |
| H4 | **Dala hisobi** ekrani (bot + Mini App + admin) | ⏳ |
| H5 | Xabar ritmi: kunlik + haftalik xulosa | ⏳ |
| H6 | Zona xaritasi + "Borib ko'ring" + rasm tashxisi | ⏳ |
| H7 | Kasallik va zararkunanda xavfi (ob-havo, GDD) | ⏳ |
| H8 | Ovozli va rasmli ko'rsatmalar | ⏳ |
| H9 | Dala daftari: xarajat, hosil, foyda | ⏳ |
| H10 | Qo'shnilar bilan solishtirish, hosil prognozi | ⏳ |

H1–H5 — **birinchi chiqarish** (fermer "Dala hisobi" ni ko'radi). H6–H7 — ikki asosiy muammoning yechimi.
H8–H10 — ushlab qolish.

---

## H0 — Tayyorgarlik

- `docs/HANDOFF.md` dagi ochiq ishlar: 3b kompilyatsiya + qayta sinov, commit.
- **Qaror kerak:** HANDOFF'dagi "Menyu versiyasi" va "Haftalik dala holati" fazalari shu yo'l xaritasi
  bilan ustma-ust tushadi. Taklif: menyu versiyasini H4 ga, haftalik holatni H5 ga birlashtirish.

**Natija:** `git status` toza, HANDOFF yangilangan.

---

## H1 — Ekin mavsumi (ekish sanasi)

**Nega:** kalendar, GDD, hosil prognozi — hammasi "qachon ekilgan" ga bog'liq. Hozir `FieldEntity` da
faqat ekin turi (`Crop`) bor, ekish sanasi yo'q.

**Ma'lumot:** yangi jadval `field_seasons` (alohida jadval, chunki bitta dalada bir yilda ikki ekin
bo'ladi: bug'doydan keyin takroriy ekin).

| Ustun | Izoh |
|---|---|
| `field_id` | FK |
| `crop` | `Crop` (STRING) |
| `sowing` | `UNANSWERED` / `SOWN` / `NOT_SOWN` / `UNKNOWN` |
| `sown_on` | ekish sanasi (faqat `SOWN`) |
| `sown_on_approx` | tez tugmadan — taxminiy (NDVI'dan taxmin — keyinroq) |
| `status` | `ACTIVE` / `FINISHED` |
| `finished_on` | hosil yig'ilgan sana |

- Bitta dalada bir vaqtda faqat bitta `ACTIVE` — **qisman unique index** `WHERE status = 'ACTIVE'`.
- Mavjud dalalar: migratsiyada `sown_on` bo'sh `ACTIVE` mavsum yaratiladi.

**Bot:** dala qo'shilganda va mavjud fermerlarga bir marta: "Qachon ekdingiz?" → tez tugmalar
(bu hafta / o'tgan oy / sanani tanlash / bilmayman). "Bilmayman" — mavsum qoladi, kalendar ishlamaydi.
Hosil yig'ilganda "🌾 Hosilni yig'dim" → mavsum yopiladi, yangisini so'raydi.

**Natija:** `psql` — har faol dalada bitta `ACTIVE` mavsum; ikkinchi `ACTIVE` qo'shish → xato.

**Qilingani (2026-10-26, V40):**
- `field_seasons.sowing`: `UNANSWERED` / `SOWN` / `NOT_SOWN` ("hali ekilmagan" — egasi so'radi) / `UNKNOWN`;
  `sown_on` faqat `SOWN` da (CHECK), `sown_on_approx` — tez tugmadan. `fields.irrigation_method` + `IrrigationType.RAINFED` (lalmi).
- Sana kiritish — **faqat tugmalar** (tez javob yoki oy → kun), matn rejimi yo'q. Kelajak va 400 kundan eski sana rad.
- Mavjud fermerlarga bir martalik so'rov **qilinmadi** — real fermerlar yo'q (egasi); dala kartasidagi `📅 Ekish sanasi`.
- Ekin o'zgarsa: ekilgan mavsum yopiladi + yangisi; ekilmagan — faqat ekini. `🌾 Hosilni yig'dim` — faqat `SOWN` da.
- Callback'lar: `sw:` / `si:` / `sh:` / `sy:` (`BotSeason`). Admin: dala sahifasida ekish sanasi va sug'orish usuli.
- Mini App tahririda ekin almashsa — mavsum ham (`FieldIntakeService` → `cropChanged`), sana qayta so'raladi.
- ⏭ **Keyinga (H4):** sug'orish eslatmasi (`irrigation`) hali `fields.irrigation_method` ni o'qimaydi — lalmi (`RAINFED`)
  dalada ham "sug'orishni sozlash" taklif qilinadi. Dala hisobi yig'ilganda lalmi dalada sug'orish qatori chiqmasin.

---

## H2 — Ekin kalendari (shablon)

**Nega:** "Dala hisobi" qatorlarining asosiy manbai. Fermer so'ramasdan "azot berish vaqti keldi" deyiladi.

**Ma'lumot (DB'da, admin tahrirlaydi — ADR #2 ruhi: yangi ekin uchun deploy kerak emas):**

`crop_task_templates`

| Ustun | Izoh |
|---|---|
| `crop` | ekin |
| `kind` | `FERTILIZE`, `SPRAY`, `IRRIGATE`, `CULTIVATE`, `SCOUT`, `HARVEST` |
| `trigger` | `DAYS_AFTER_SOWING` yoki `GDD` (H7 dan keyin) |
| `offset_days` / `gdd` | qachon |
| `window_days` | necha kun ichida bajarish kerak (keyin — kechikdi) |
| `title_*`, `instruction_*` | uz/ru/en matn: nima, qancha, qanday |
| `active` | o'chirib qo'yish mumkin |
| `approved_by` | agronom ismi — **bo'sh bo'lsa fermerga ko'rsatilmaydi** |

**Birinchi kontent:** `WHEAT` va `COTTON` (eng ko'p fermer). Qolganlari keyin.

🔴 **Risk — kontent:** doza va preparatlar agronom tomonidan yoziladi/tasdiqlanadi. Men faqat struktura va
qoralama beraman. Preparatlar — O'zbekistonda ro'yxatdan o'tganlaridan.

**Admin:** `Kalendar` sahifasi — ekin bo'yicha shablonlar ro'yxati, tahrirlash, `approved_by`.
Permission: yangi `CALENDAR_*` (`Permissions.java` + migratsiya — ikkalasi).

**Natija:** admin'da bug'doy shablonlari ko'rinadi; `approved_by` siz shablon fermerga chiqmaydi.

---

## H3 — Dala vazifalari + "Bajardim"

**Nega:** shablon — umumiy; vazifa — **shu dalaning** aniq sanali ishi.

`field_tasks`

| Ustun | Izoh |
|---|---|
| `season_id` | FK |
| `template_id` | nullable (ob-havo/monitoring vazifasining shabloni yo'q) |
| `source` | `CALENDAR`, `IRRIGATION`, `WEATHER`, `MONITORING`, `DISEASE` |
| `due_on`, `window_ends_on` | |
| `status` | `PENDING`, `DONE`, `SKIPPED` (`OVERDUE` saqlanmaydi — `window_ends_on < bugun` dan hisoblanadi) |
| `done_on`, `note` | |

- Generatsiya **idempotent**: unique `(season_id, template_id)`. Ekish sanasi o'zgarsa — `PENDING` lar qayta hisoblanadi,
  `DONE` larga tegilmaydi.
- Kunlik job (mavjud job'lar uslubida, ShedLock/`FOR UPDATE SKIP LOCKED` — mavjud pattern'ga qarab).

**Bot:** vazifa xabarida `[✅ Bajardim] [⏭ O'tkazib yubordim] [❓ Qanday qilinadi]`.
Callback'da egalik tekshiruvi (begona vazifa → hech narsa, ADR #16 ruhi).

**Natija:** bug'doy, ekish sanasi −40 kun → kalendar vazifalari to'g'ri sanalarda; "Bajardim" → `DONE`;
begona `taskId` bilan callback → o'zgarmaydi.

---

## H4 — Dala hisobi ekrani

**Nega:** butun g'oyaning yuzi. Hozirgi xizmatlar (sug'orish, ob-havo, monitoring, AI) bitta joyga yig'iladi.

```
🌾 Bug'doy dalasi — 12 ga                    Holat: 🟡 E'tibor kerak
─────────────────────────────────────────────
💧 Sug'orish        2 kundan keyin            🟢
🌿 Azot o'g'iti     3 kun KECHIKDI            🔴  [Bajardim]
🦠 Zang kasalligi   Xavf yuqori (5 kun nam)   🟡  [Nima qilish kerak?]
📍 Shimoli-sharq    2.1 ga sustlashdi (−18%)  🔴  [Xaritada ko'rish]
❄️ Sovuq            Yo'q                       🟢
```

**Backend:** `FieldStatusService` (yangi paket `fieldstatus/` yoki `field/` ichida — fayl soniga qarab).
Qatorlar manbalari:

| Qator | Manba (mavjud) |
|---|---|
| 💧 Sug'orish | `irrigation` — `next_irrigation_on`, `depletion_pct` |
| ❄️🔥💨🌧 Ob-havo | `weather` — `WeatherRisk` |
| 📍 Zona | `monitoring` — oxirgi `DECLINE` hodisasi |
| 🌿 Kalendar | `field_tasks` (H3) |
| 🦠 Kasallik | H7 dan keyin |

- Rang: 🟢 hammasi joyida / 🟡 yaqin 3 kunda ish bor yoki xavf / 🔴 kechikkan yoki DANGER. Dala rangi = eng yomon qator.
- **So'rov soni chegarasi:** fermerning barcha dalalari uchun bitta "hisob" — N+1 yo'q (har manba bitta so'rov, `IN (...)`).
- DTO `record`; controller entity qaytarmaydi.

**Bot:** asosiy menyu birinchi tugmasi — `📋 Dala hisobi`. Bir nechta dala bo'lsa — avval qisqa ro'yxat
(`🔴 3-dala · 🟡 1-dala · 🟢 2-dala`, eng yomoni tepada), keyin bitta dala.
Menyu versiyasi (HANDOFF) shu yerda.

**Mini App:** shu ekran + xarita. **Admin:** fermer kartasida shu hisob (qo'llab-quvvatlash uchun).

**Natija:** `GET /fields/{id}/status` — 5 manbadan qatorlar; begona dala → 404; SQL log'da so'rov soni o'lchanadi.

---

## H5 — Xabar ritmi

**Nega:** kommunal ilova har kuni yozmaydi — **muddat yaqinlashganda** yozadi. Ushlab qolish shundan.

- **Kunlik (ertalab, dala mahalliy vaqti):** faqat yangi 🔴 yoki bugun muddati keladigan vazifa bo'lsa. Bo'lmasa — jim.
- **Haftalik (dushanba):** "Dalalaringiz holati" — har dala bitta qator + o'tgan hafta bajarilgan ishlar.
  HANDOFF'dagi "🔒 N ta muammo" teaser (bepul tarifga) shu yerda.
- Mavjud `notification` outbox orqali. Dedup: bitta vazifa haqida kuniga 1 marta. Kunlik umumiy chegara — Sozlamalardan.
- Fermer xabar turlarini o'chira oladi (sozlamalar).

**Natija:** bitta fermerga kuniga chegaradan ko'p xabar ketmaydi; hech narsa bo'lmagan kuni — xabar yo'q.

---

## H6 — Zona xaritasi + "Borib ko'ring"

**Muammo 1 yechimi:** katta dalada qayerda nima bo'layotganini bilish.

- **Zonani odam tilida nomlash:** zona markazi va dala markazidan yo'nalish (shimoli-sharq, janub...) + maydon (ga).
- **O'zgarish, holat emas:** "o'tgan suratga nisbatan −18%". `Verdict` mantig'i (bir tekis pasayish = tabiiy) saqlanadi.
- **Mini App:** zona poligoni xaritada, `📍 Men qayerdaman` (GPS), zonagacha masofa va yo'nalish.
- **Zonada:** `📷 Rasm olish` → mavjud AI maslahat (C4) — lekin **zona va vazifaga bog'langan** (`scout` yozuvi:
  zona, rasm, AI javobi, fermer izohi). Natija "Dala hisobi" dagi 📍 qatorni yopadi.
- **Bir necha dala:** dalalar reytingi (eng yomoni tepada) — H4 ro'yxatida.

**Tekshirish kerak:** Python servis zonalar poligonini qaytaradimi yoki faqat statistika — kod ko'rilib aniqlanadi.

**Natija:** monitoring `DECLINE` → hisobda 📍 qator → Mini App'da zona → rasm → qator yopiladi.

---

## H7 — Kasallik va zararkunanda xavfi

**Muammo 2 yechimi:** fermer bilmagan xavfni **ko'rinishidan oldin** aytish.

1. **Ob-havo modellari:** kasallik ma'lum harorat + namlik davomiyligida tarqaladi (masalan, bug'doy zangi,
   un-shudring). Qoida: ekin + harorat oralig'i + nam soatlar (RH ≥ 90%) + ketma-ket kunlar → xavf darajasi.
2. **GDD (harorat yig'indisi):** ekish sanasidan (H1) boshlab kunlik `max(0, (tmax+tmin)/2 − base)`.
   Zararkunanda avlodi va ekin bosqichi (masalan, ko'sak qurti) shu bilan bashorat qilinadi.
   H2 shablonidagi `trigger = GDD` shu yerda ishlaydi.
3. Qoidalar ham **DB'da**, parametrlar agronom/adabiyotdan, `approved_by` bilan (0-bo'lim, 5-tamoyil).
4. Natija — `field_tasks` (`source = DISEASE`, turi `SCOUT`: "tekshirib ko'ring"), "dorilang" emas.
5. **Tasdiq signallari** (qaror #5): fermer rasmi (H6) → AI; shu tumanda boshqa fermer tasdiqlagan bo'lsa —
   qo'shnilarga xavf darajasi oshadi; feromon tutqich soni (fermer kiritadi). Faqat tasdiqdan keyin `SPRAY` vazifasi.

**Tekshirish kerak:** Open-Meteo'dan hozir kunlik ma'lumot olinadi; nam soatlar uchun **soatlik** `relative_humidity_2m`
kerak — `OpenMeteoClient` kengaytiriladi, kesh (`WeatherCell`) hajmi o'lchanadi.

**Natija:** sun'iy nam haftali prognoz bilan bug'doy dalasida 🦠 qator paydo bo'ladi; quruq prognozda — yo'q.

---

## H8 — Ovozli va rasmli ko'rsatmalar

- Har shablonga (H2, H7): **oldindan yozilgan ovoz** (agronom ovozi, o'zbekcha) va "kasallik qanday ko'rinadi" rasmi.
  Mavjud fayl servisi orqali admin yuklaydi. TTS emas — o'zbekcha TTS sifati ishonchsiz.
- Botda `❓ Qanday qilinadi` → matn + ovoz + rasm.

**Natija:** vazifa xabaridan bir bosishda ovozli ko'rsatma.

---

## H9 — Dala daftari

**Nega:** ma'lumot to'plangani sari ilovadan ketish qiyinlashadi; fermerning eng qimmat savoli — "foyda bormi".

- Yozuvlar: ish (avtomatik — "Bajardim" dan), xarajat (o'g'it, yoqilg'i, ish haqi, suv — summa), hosil (t).
- Hisob: mavsum bo'yicha xarajat/ga, hosil/ga, taxminiy foyda.
- Bot: "Bajardim" dan keyin ixtiyoriy "Qancha sarfladingiz?" (o'tkazib yuborsa bo'ladi).

**Natija:** mavsum yopilganda dala bo'yicha hisobot (Mini App + PDF/rasm botda).

---

## H10 — Solishtirish va hosil prognozi

- **Qo'shnilar bilan:** "tumaningizdagi bug'doy dalalari o'rtachasidan 12% pastsiz" (NDVI bo'yicha, shu sanada).
  Maxfiylik: guruhda **kamida 5 ta** dala bo'lmasa ko'rsatilmaydi, hech qachon nom/joy yo'q.
- **Hosil prognozi:** NDVI cho'qqisi + ekin → t/ga. 🔴 Bir mavsum o'z ma'lumotimiz (H9 dagi haqiqiy hosil) bo'lmaguncha
  faqat "taxminiy oraliq" deb ko'rsatiladi yoki umuman chiqarilmaydi.

**Natija:** bir mavsum ma'lumotidan keyin ishga tushadi.

---

## Ochiq savollar (egasi hal qiladi)

1. **Tariflar:** qaysi qism bepul? Taklif: Dala hisobi (H4) hammaga, lekin bepulda faqat 1 dala va
   🔴 qatorlar soni ko'rinadi, tafsilot — pullik (HANDOFF'dagi "🔒 N ta muammo" g'oyasi).
2. **Agronom:** kalendar va kasallik kontentini kim yozadi/tasdiqlaydi? H2 shusiz fermerga chiqmaydi.
3. **Birinchi ekinlar:** bug'doy + paxta yetarlimi, yoki pilot hududingizda boshqasi ko'pmi?
4. **Mini App yoki bot:** Dala hisobining asosiy ekrani qayerda bo'ladi? Taklif: botda qisqa matn, to'liq — Mini App.

## Har fazaning ish tartibi

1. Qisqa spetsifikatsiya (shu fayldagi bo'limni aniqlashtirish) → egasi tasdiqlaydi.
2. Kod (CLAUDE.md konvensiyalari, yangi `V*` migratsiya).
3. code-reviewer + tester parallel → tuzatish → qayta sinov.
4. Qo'lda tekshirish buyruqlari (`curl`, `psql`) egasiga.
5. Shu fayldagi holat jadvali yangilanadi, commit (egasi so'rasa).
