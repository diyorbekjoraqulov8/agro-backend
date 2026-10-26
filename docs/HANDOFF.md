# HANDOFF — keyingi agent uchun (2026-10-19)

> Ish shu yerda to'xtatildi. Avval shu faylni, keyin `CLAUDE.md`, `docs/suvtejam-mvp-qollanma.md` ("FAZALAR" bo'limi) va
> memory'ni o'qing. Brend — **Dropy**, paket `uz.app.agro` o'zgarmaydi.
> Ish tartibi (memory `workflow-review-and-test`): har fazadan keyin `.claude/agents/` dagi **code-reviewer** va
> **tester** ni parallel (fonda) ishga tushiring, topilmalarni tuzating, tuzatishlarni qayta sinating.

---

## 1. Holat bir qarashda

| Ish | Holat |
|---|---|
| Support bot (V37), guruhdan tarif berish | ✅ tayyor, sinalgan, commit'da (`8c790c2 Telegram Chat`) |
| 3a — Chegirmalar (V38, V39) | ✅ tayyor, 2 marta sinalgan, commit'da |
| 3b — Mavsumiy obuna | ✅ 2026-10-26: qayta sinov 7/7, "A" qarori sinovi 5/5, commit'da |
| Menyu versiyasi | ⏸ **qoldirildi** (egasi, 2026-10-26) — 4-bo'lim faqat tarix uchun |
| Haftalik dala holati + "🔒 N ta muammo" teaser | ➡️ `docs/dala-hisobi-roadmap.md` H5 ga ko'chdi |
| **Keyingi ish** | ➡️ **`docs/dala-hisobi-roadmap.md`** — H1 dan boshlab |

Muhit toza: scratch baza yo'q (`agro` dan boshqa), 8082/8099 portlar bo'sh, scratchpad nusxasi o'chirilgan.

## 2. Commit qilinmagan o'zgarishlar (`git status`)

Code-reviewer 3b tuzatishlarini tekshirdi — 🔴 yo'q. Uning 🟡/🟢 topilmalari bo'yicha qilingan o'zgarishlar:

| Fayl | O'zgarish |
|---|---|
| `settings/Setting.java` | Mavsum izohi/tavsiflari: "oylar × narx" → "tarif narxi × qolgan kunlar / tarif muddati"; "bir oydan kam" → "tarif muddatidan kam" |
| `billing/SeasonOffers.java` | izoh "tarif muddatidan kam qoldi" |
| `bot/BotPlans.java` | javadoc misoli "(150 kun)"; **Agro Biznes filtri `seasonFor(farmerId, current)` ga ko'chirildi** — endi `seasonOffer(tg)` (ya'ni `BotUpdateHandler.requestSeason`, eski `pl:season` tugmasi) ham cheksiz tarif egasiga taklif bermaydi |
| `settings/SettingsService.java` | `seasonWarning()` ga 2 holat: tarifda `durationDays == null`; mavsum oxirigacha qolgan kun < tarif muddati. `ChronoUnit` import qo'shildi |
| `admin/src/views/SettingsView.vue` | `paidPlans` dan `contactSales` tariflar chiqarildi (backend ularni rad etadi) |
| `billing/DiscountPricing.java` | FIXED chegirma belgisi — `d.getValue()` emas, haqiqiy farq `price − finalPrice` (narx 0 ga kesilganda to'g'ri summa) |
| `supportbot/SupportPlans.java` | `seasonGone()` — taklif yopilgan bo'lsa xodimga oddiy muddat tugmalari qaytadi (PERIODS, METHOD, confirm) |

**2026-10-26 — 3b qayta sinov ✅ (7/7), keyin egasi qarori "A":** Agro Biznes filtri `BotPlans.seasonFor` dan
`SeasonOffers.offer` ga ko'chdi — endi bot, support guruhi (`sb:pp`, `:99`) va admin (`GET /farmers/{id}/season-offer` → 204)
uchun bir xil. `EffectivePlan.unlimited()` qo'shildi; `SeasonOffers.periodDays()` — `SettingsService.seasonWarning` ham
shu bilan hisoblaydi; "muddat yo'q" ogohlantirishi `SEASON_PLAN` qatoriga ko'chdi; izoh va import tartibi tuzatildi.

**Birinchi qadam:**
```bash
./gradlew compileJava -q            # kompilyatsiya tekshirilmagan!
cd admin && npx vue-tsc --noEmit    # yoki loyihadagi type-check skripti
```

Reviewer'ning qolgan, **qilinmagan** kichik takliflari (ixtiyoriy):
- `Setting.SEASON_DISCOUNT` min 0 → 0% da bot "X → X (−0%)" ko'rsatadi. Min 1 yoki 0% da chiziqli narxni yashirish.
- `SupportPlans.confirmSeason` taklifni tasdiq paytida qayta hisoblaydi — tasdiq xabari kecha ko'rsatilgan bo'lsa summa/`from` farq qiladi. Xato emas, xodimga farqni ko'rsatish mumkin.
- `docs/suvtejam-mvp-qollanma.md` → "### Mavsumiy obuna (2026-10-18)" da formula hali `⌈kunlar / muddat⌉` — **proporsional** deb yangilang (`narx × kunlar / muddat`, muddatdan kam qolsa taklif yo'q).

## 3. 3b qayta sinovi — qilinishi kerak

Tester limit sababli ikki marta yarim yo'lda to'xtagan. Shu ro'yxat bilan qayta ishga tushiring (2-bo'lim kompilyatsiyadan o'tgach):

1. **Proporsional narx** (`SeasonOffers`): base = price × days / durationDays (UZS — 0 kasr), final = base × (100−pct)/100, days = from..end inklyuziv. Pro 59000/30 kun, 20%, 101 kun → base 198633, final 158906. Obunasiz fermer; joriy obunali fermer (from = oxiri+1); qolgan 29 kun → taklif yo'q (204), 30 kun → bor; pct 0 va 90.
2. **Agro Biznes** faol obunasi bor fermer: katalogda 🌱 blok yo'q **va** `pl:season` callback → `plans.unavailable`.
3. **Bot matni**: "(N kun)"; joriy obunali fermerda "📅 …dan boshlanadi (joriy obunangizdan keyin)". uz/ru/en.
4. **Sozlamalar**: yil 2020–2100 dan tashqari (`+10000-01-01`, `2019-12-31`, `2101-01-01`) → 400; sale-from > end va end o'tgan → GET /settings da warning; qolgan kun < muddat → warning (yangi); `season.plan=BUSINESS` → 400, matn "bu yerda tanlab bo'lmaydi".
5. **Guruh (SupportPlans)**: mavsumiy tasdiqni 2 marta tez bosish → 1 obuna, "✅ yozildi" xabari almashmaydi; tasdiqdan oldin `season.enabled=false` → `support.plan.seasonGone`, tugmalar qoladi; soxta callback (boshqa planId, `:99`) ishlamaydi.
6. **Admin**: `GET /farmers/{id}/season-offer` javobida `days` bor; mavsumiy davr bilan to'lov yoziladi.
7. **FIXED chegirma** 10000.5 → butun so'mga yaxlitlanadi.

Tester qoidalari (avvalgi sinovlardagi muhit): `agro` ga yozilmaydi — `pg_dump agro | pg_restore` nusxa; telegram ID'lar 990000000+id / xodimlar 991000001..; loyiha nusxasi scratchpad'da; `BotConfig` va `SupportBotClient` `127.0.0.1:8099` soxta Telegram API'ga, tokenlar soxta; port 8082, `TELEGRAM_POLLING=false`, `SUPPORT_BOT_POLLING=false`, `SUPPORT_GROUP_ID=-1009999999999`; `SupportBotRunner` sharti faqat nusxada yumshatiladi; JUnit yo'q; oxirida hammasi tozalanadi.

Sinov o'tgach — egasiga xulosa, keyin commit (egasi so'rasa).

## 4. Keyingi faza — Menyu versiyasi (egasi tasdiqlagan)

Muammo: Telegram reply keyboard bot shu menyuni biriktirgan **keyingi** xabargacha eskisicha qoladi. Menyu ko'p javobga
biriktirilgan, lekin hammasiga emas — yangilanishdan keyin fermerda eski menyu qoladi. "/start bosing" deyish yomon UX.

Kelishilgan g'oya (eski sessiyadagi tavsiya):
- **Menyu versiyasi** (masalan `farmers.menu_version` yoki bot holatida) va joriy versiya (kod konstantasi yoki sozlama).
  Fermerning versiyasi eski bo'lsa — uning **birinchi** bosishidagi javobga yangi asosiy menyu majburan biriktiriladi va versiya yangilanadi.
- Admin "Bildirishnomalar" sahifasida kampaniyaga **"asosiy menyuni yangilash"** belgisi — xabar bilan birga yangi menyu boradi
  (foydali yangilik bo'lganda; oyiga 1–2 tadan ko'p emas).
- Telefonda qolgan eski tugmalar baribir ishlaydi (allaqachon bor — `BotMenu`, `BotSection`).

Bu skeletda yo'q yangi narsa → `CLAUDE.md` bo'yicha **avval qisqa spetsifikatsiya**, keyin kod. Yangi migratsiya — **V40**.
Kerakli joylar: `bot/BotMenu`, `bot/BotSection`, `bot/BotUpdateHandler`, `notification/` (outbox, V23), admin `NotificationsView`.
Hujjat: `docs/suvtejam-mvp-qollanma.md` ga yangi bo'lim.

## 5. Undan keyin

**Haftalik dala holati va "🔒 N ta muammo topildi" teaser'i** — bepul fermerlarga muammolar soni ko'rinadi, tafsilot Pro'da.
Bepuldan Pro'ga o'tkazishning asosiy vositasi.

## 6. Kod tashqarisidagi ish (tugagan)

Telegram/Instagram/X kanal strategiyasi bo'yicha maslahat 2026-10-19 da chatda berildi (nom, tavsif, sozlamalar, post turlari,
agro-kanallar statistikasi). Kod o'zgarishi yo'q. Asosiy fikr: har kanal/reklama uchun alohida deep link
(`t.me/agroglobalbot?start=<manba>`) — admin'da manba bo'yicha konversiya ko'rinadi.
