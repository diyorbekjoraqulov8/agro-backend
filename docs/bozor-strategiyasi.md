# Dropy — bozor strategiyasi (2026-10-28)

> Maqsad: raqobatchilar ko'p bo'lgan bozorda Dropy **nimasi bilan boshqacha** bo'lishi va buni
> **qanday tekshirish** kerakligi. Bu hujjat — gipotezalar to'plami, isbotlangan faktlar emas.
> Har bir gipoteza real fermerlar bilan tekshiriladi (7-bo'lim), natija shu faylga yoziladi.
> Texnik reja — `dala-hisobi-roadmap.md`, joriy holat — `HANDOFF.md`.

---

## 1. Asosiy tashxis

**Funksiya bo'yicha raqobatda yutib bo'lmaydi.** Uchala asosiy funksiya endi hammada bor:

| Funksiya | Nega u ajratmaydi |
|---|---|
| Sug'orish vaqti | FAO-56 + ob-havo API ochiq, har kim yozadi |
| Kasallik skaneri | PlantVillage kabi ochiq datasetlar, tayyor modellar; Growz 3 000+ kasallik deydi |
| Dala holati (NDVI) | Sentinel-2 bepul, OneSoil / EOSDA / Cropwise yillardan beri ko'rsatadi |

Fermer NDVI aniqligini solishtirmaydi. U bitta savol beradi: **"bu menga pul topib berdimi / tejadimi?"**
Demak ustunlik texnologiyada emas, quyidagilarda bo'ladi:

1. O'zbekiston reallagiga moslik (suv navbati, lalmi, klaster, til, Telegram)
2. Isbotlangan va ochiq e'lon qilingan aniqlik
3. Yopiq tsikl: maslahat → bajarildi → natija o'lchandi
4. Pul tilida gapirish
5. B2B2C daromad modeli
6. Hududiy tarmoq effekti (qo'shnilar)

---

## 2. Raqobatchilar

> Ma'lumot raqobatchilarning **o'z saytlaridan** (2026-10-28). Ularning raqamlari — o'z da'volari,
> mustaqil tasdiqlanmagan.

| | **Apollo** ([tryapollo.uz](https://www.tryapollo.uz/)) | **Growz** ([growz.io](https://growz.io/)) | **Amuflow AI** |
|---|---|---|---|
| Asosiy | Rasm bo'yicha kasallik tashxisi | Shaxsiy agro maslahat + marketplace + kredit | ochiq ma'lumot topilmadi |
| Kanal | Web, **Telegram**, API; mobil ilova — 2026 rejasida | Android / iOS ilova | — |
| Texnologiya | ResNet-18, PlantVillage (~70k rasm, 38 holat); dron tasvirlari (Fleet) | Fotodiagnostika, haftalik reja, kredit skoring | — |
| Mijoz | Kichik fermer, katta xo'jalik, **bank va sug'urta** (API) | Kichik fermer; sotuvchilar, moliyachilar | — |
| Kuchli tomoni | Agrobank AI500 hakatoni g'olibi — bank bilan aloqa | 100k+ yuklab olish, 130 ekin, IFC/ADB/World Bank hamkor, marketplace + kredit | — |
| Da'volar | Foydalanuvchi soni aytilmagan | 94% aniqlik, 23% hosil o'sishi, 19% suv tejash (ichki tadqiqot, audit jarayonda) | — |
| Zaif tomoni (gipoteza) | Faqat barg rasmi — dalani butunlay ko'rmaydi; PlantVillage laboratoriya rasmlari, dala sharoitida aniqlik pasayadi | Ilova o'rnatish kerak; 3 davlatga umumiy — O'zbekiston suv tizimiga xos emas | — |

**Amuflow AI** — internetda topilmadi. Egasi: sayt / Telegram kanal / kim tomonidan ekanini yozib qo'ying.

### Xulosa

- **Apollo** va **Growz** ikkalasi ham **rasm tashxisi** atrofida qurilgan. U yerda raqobat qilmang —
  bu ularning maydoni, ularda ma'lumot ko'p.
- Hech biri **sun'iy yo'ldosh orqali butun dalani avtomatik kuzatish + suv navbatiga moslashgan sug'orish**ni
  asosiy qilib qo'ymagan. Dropy'ning bo'sh joyi shu yerda: **"fermer savol bermaydi, ilova o'zi aytadi"**
  (roadmap 1-tamoyil).
- Growz "marketplace + kredit" bilan ekotizim quryapti, Apollo bank API'si bilan. Ikkalasi ham **B2B
  pul shu yerda** ekanini tasdiqlaydi (5-bo'lim).
- Ularning raqamlari (94%, 23%) — ichki. Dropy **ochiq, tekshiriladigan** aniqlik bilan chiqsa
  (L2), bu ishonch bo'yicha farq beradi.

---

## 3. Pozitsiya

> **Dropy — dalangizni sun'iy yo'ldoshdan o'zi kuzatadi va faqat harakat kerak bo'lganda
> o'zbek tilida, Telegram'da aytadi: qayerga borish, nima qilish. Suv navbatingizga moslab.**

Kimga emas: "barg rasmini yuborib, kasallik nomini bilmoqchi" bo'lgan fermerga asosiy mahsulot sifatida —
bu Apollo/Growz. Dropy'da rasm tashxisi **qo'shimcha** (H6, tayyor modelga tayanib), markaz emas.

| Ular | Dropy |
|---|---|
| Fermer rasm oladi → javob | Ilova o'zi dalani ko'radi → "shimoli-sharqiy burchakka boring" |
| Barg darajasi | Butun dala, zona, vaqt ichida o'zgarish |
| Umumiy sug'orish maslahati | **Navbat** keladigan kunga moslangan reja |
| Ichki aniqlik da'vosi | Ochiq aniqlik sahifasi (L2) |
| Signal berib to'xtaydi | Signal → "Bajardim" → natija o'lchanadi |

---

## 4. Olti yo'nalish — batafsil

### 4.1 O'zbekiston reallagiga moslik (eng kuchli qurol)

**Suv navbati.** Ko'p hududda fermer suvni xohlagan payt ololmaydi: kanal, suv iste'molchilari uyushmasi,
grafik. "Ertaga sug'oring" — suv 5 kundan keyin kelsa, foydasiz. To'g'ri savol:

- "Navbatingiz qachon?" — fermer bir marta aytadi (yoki uyushma grafigi).
- "Navbat kelganda **qaysi dalaga birinchi**, qaysi biri kutsa bo'ladi" — namlik tanqisligi bo'yicha tartib.
- "Navbatgacha stress xavfi bor dala" — oldindan ogohlantirish.

> 🔴 Gipoteza, tekshirilmagan: navbat qaysi hududda qanchalik qattiq — intervyu savoli (7.4).
> Agar fermerlarning ko'pchiligi quduq/nasos bilan o'zi sug'orsa, bu yo'nalish kuchsizlanadi.

**Boshqa mahalliy xususiyatlar:**
- **Lalmi va sug'oriladigan** — onboarding'da allaqachon ajratilgan (`87b787b`). Lalmida suv emas, ekish
  muddati va namlik zaxirasi muhim.
- **Paxta–bug'doy almashlab ekish, klaster va davlat buyurtmasi** — ekin kalendari (H2) shu tizimga mos.
- **Til va kanal** — o'zbek (lotin/kirill), rus; Telegram; ovozli xabar (H8). Fermer ilova o'rnatmaydi,
  Telegram'i bor. Growz ilovaga tayanadi — bu Dropy uchun eshik.
- **Sho'rlanish** — ba'zi hududlarda asosiy muammo; sun'iy yo'ldoshda ko'rinadigan, ko'p yillik signal.

### 4.2 Aniqlikni isbotlab, ochiq e'lon qilish

L2 (ground truth → aniqlik) — texnik vazifa emas, **marketing quroli**.

- Admin/fermer kuzatuvi + xulosaga baho (✅/➖/❌) → xulosa turi bo'yicha to'g'ri %, yolg'on signal, o'tkazib yuborilgan.
- Ochiq sahifa: "Oxirgi 90 kunda 'sust zona' xulosalarimizning X% i dalada tasdiqlandi (N ta tekshiruv)".
  N kichik bo'lsa ham — halol raqam.
- **Halollik brend sifatida:** "Sun'iy yo'ldosh kasallik nomini aytmaydi — qayerga borishni aytadi.
  Nomini rasm aniqlaydi." Ko'p va'da beradigan raqobatchilardan ajratadi.
- B2B (bank, sug'urta, klaster) uchun aniqlik raqami — **sotuv sharti**, ular so'raydi.

### 4.3 Yopiq tsikl: maslahat → bajarildi → natija

```
signal ("shimolda sust")  →  fermer "Bajardim" (H3)  →  +7..14 kun NDVI qayta  →  "o'g'itingiz ishladi ✅"
                                                                              yoki "o'zgarmadi — boshqa sabab?"
```

- Fermer qiymatni **o'z ko'zi bilan** ko'radi — ushlab qolish (retention) asosiy dvigateli.
- Dropy'da hech kimda yo'q ma'lumot to'planadi: qaysi harakat, qaysi sharoitda, qanday natija. Bu
  keyinchalik maslahat sifatini oshiradi — ko'chirib bo'lmaydigan **data moat**.
- "O'zgarmadi" holati ham qimmatli: L2 aniqlik o'lchoviga avtomatik kuzatuv bo'lib tushadi.

### 4.4 Pul tilida gapirish

NDVI grafigi fermerga hech narsa demaydi. Kerak:

- "Bu mavsum Dropy 2 ta ortiqcha sug'orishning oldini oldi ≈ X so'm (yoqilg'i/suv/mehnat)".
- "Sust zona 1,2 ga — o'z vaqtida harakat qilinmasa taxminiy yo'qotish Y so'm".
- Dala daftari (H9: xarajat, hosil, foyda) — **kechiktirmang**, tarif uchun to'lov sababi shu bo'ladi.

> 🔴 Taxminiy pul raqami — eng xavfli joy. Formula ochiq bo'lsin ("qanday hisobladik"), oraliq bilan,
> mo'tadil. Bir marta oshirib aytilsa, ishonch yo'qoladi.

### 4.5 B2B2C daromad modeli

Kichik fermerning to'lov qobiliyati past — faqat fermer obunasiga suyanish xavfli.
Fermer **bepul / arzon**, to'lovni boshqa tomon qiladi:

| Mijoz | Nima uchun to'laydi | Dropy'da nima tayyor | Nima yetishmaydi |
|---|---|---|---|
| **Klaster / katta xo'jalik** | Minglab ga, yuzlab fermer — bitta panelda kim qayerda orqada | Admin panel, monitoring, tarix (L1b) | Ko'p-tashkilot (tenant), klaster roli, hisobotlar |
| **Bank** (agro kredit) | Dala haqiqatan ekilganmi, holati qanday — kreditni kuzatish, skoring | Tahlil, tarix, ko'p yillik ma'lumot (L3) | API, hisobot formati, aniqlik raqami (L2) |
| **Sug'urta** | Zarar haqiqatan bo'ldimi, qachon, qancha maydon | Holat tarixi, sahnalar, debug PNG | Zarar hisoboti, hujjat sifatida eksport |
| **O'g'it/urug' sotuvchisi** | Kanal — "azot kerak" signali ortidan xaridor | Tavsiya qatlami | Hamkor modeli; **halollik chegarasi** (quyida) |
| **Fermer** | Premium: barcha muammolar ochiq, tez-tez kuzatish | Bepul/pullik tarif (`billing/`) | Narx tekshiruvi (7-bo'lim) |

**Halollik chegarasi (sotuvchilar bilan):** tavsiya hech qachon homiy tomonidan sotib olinmaydi. Signal
mustaqil hisoblanadi; sotuvchi faqat "qayerdan olsa bo'ladi" bo'limida ko'rinadi va bu belgilanadi.
Aks holda 4.2 dagi ishonch yo'qoladi.

**Tavsiya ketma-ketligi:** avval fermer (feedback va aniqlik uchun), parallel ravishda **1–2 ta B2B suhbat**
(bank yoki klaster) — ular nima so'rashini bilish uchun, sotish uchun emas.

### 4.6 Hududiy tarmoq effekti

- **Qo'shnilar bilan solishtirish** (H10): "Tumaningizdagi bug'doy dalalarining 70% idan orqadasiz".
- **Qo'shni signali**: "3 km radiusda 4 ta dalada bir vaqtda sustlashish — zararkunanda bo'lishi mumkin,
  tekshirib ko'ring". Bitta fermer ko'rmaydi, tarmoq ko'radi.
- Bitta tumanda fermer qancha ko'p — xizmat shuncha aniq. Raqobatchi kodni ko'chiradi, **tarmoqni ko'chira olmaydi**.
- Natija: **zichlik > kenglik.** 1 tumanda 100 fermer > 10 viloyatda 100 fermer.

---

## 5. Mijoz segmenti — hali noma'lum, qanday aniqlaymiz

Egasi asosiy mijozni hali bilmaydi — bu normal. Segmentni **taxmin emas, tajriba** tanlaydi.

| Segment | Gipoteza | Nimasi bilan tekshiriladi |
|---|---|---|
| A. Kichik fermer (1–10 ga) | Ko'p, Telegram'da, lekin to'lamaydi | Reklamaga javob ko'p, to'lovga tayyorlik past bo'lsa — tasdiq |
| B. O'rta fermer xo'jaligi (10–200 ga) | Bir nechta dala, ko'zdan qochadi — kuzatuvdan eng ko'p foyda | Dala soni, "barcha dalani bir joyda ko'rish" qiziqishi |
| C. Klaster / katta xo'jalik (1 000+ ga) | Eng ko'p to'laydi, lekin sotuv uzoq | 1–2 ta suhbat, reklama emas |
| D. Bank / sug'urta | Ma'lumot uchun to'laydi | 1 ta suhbat: "qanday hisobot kerak?" |

**Kutilgan natija (gipoteza):** B segment — eng yaxshi boshlang'ich nuqta: muammo og'riqli (ko'p dala),
to'lov qobiliyati bor, sotuv tez (bitta qaror qiluvchi). Tajriba buni tasdiqlashi yoki rad etishi kerak.

---

## 6. Hudud tanlash — mezonlar

Hudud ham noma'lum. Tanlashdan oldin **mezonlar**, keyin tajriba (7-bo'lim, 3 hudud parallel).

| Mezon | Nega muhim | Qanday baholanadi |
|---|---|---|
| Suv tanqisligi / navbat qattiqligi | 4.1 ning kuchi shunga bog'liq | Intervyu, ochiq ma'lumot |
| Dala o'lchami | Sentinel-2 10 m: juda kichik (< 0,5 ga) uchastkada signal shovqinli | Reklama javobidagi maydon |
| Asosiy ekin | Bitta ekinda boshlash — motor sozlash va kalendar oson | Hudud tuzilmasi |
| Bulutlilik | Bulutda sun'iy yo'ldosh ko'rmaydi — vegetatsiya davrida ochiq kun soni | `analysis` debug: `scenes_rejected_cloud` |
| Telegram faolligi va reklama narxi | Jalb qilish narxi | Reklama tajribasi (CPL) |
| Egasining yaqinligi | Dalaga borib ground truth olish (L2) | Ish kunida borib kelish mumkinmi |

**Oxirgi mezon kam baholanadi, lekin muhim:** L2 uchun dalaga borish kerak. Birinchi hudud —
egasi **jismonan borib turadigan** joy bo'lgani ma'qul.

**Ekin:** birinchi bosqichda **bitta** ekin (bug'doy yoki paxta). Ikkalasi ham keng tarqalgan, sun'iy
yo'ldoshda yaxshi ko'rinadi (yirik, bir xil maydon). Bog' va polizdan boshlamang — kichik, aralash, signal qiyin.

---

## 7. Target reklama orqali feedback — tajriba rejasi

### 7.1 Muammo: bot to'xtatilgan, lekin fermer kerak

Yo'nalish (2026-10-26): botga vaqt sarflanmaydi. Yechim — **concierge MVP**, mavjud kod bilan:

```
Reklama  →  Telegram kanal/akkaunt (oddiy, bot shart emas)  →  fermer lokatsiya/kontur yuboradi
        →  admin Lab dala chizadi (L1 — tayyor)  →  tizim tahlil qiladi (L1b — tayyor)
        →  admin natijani qo'lda, sodda tilda fermerga yuboradi  →  intervyu + "aslida nima bor?"
```

Uch ishni birdan bajaradi:
1. **Feedback** — fermer nima istashini eshitasiz.
2. **L2 ground truth** — fermer "ha, o'sha burchakda suv yetmagan" desa — tayyor kuzatuv. HANDOFF'dagi
   "3–5 ta holati ma'lum real dala" muammosi shu yo'l bilan yechiladi.
3. **Segment va hudud tanlovi** — kim javob berdi, kim qaytib keldi, kim to'lashga tayyor.

> Qo'lda ishlash miqyos qilmaydi — bu ataylab. 20–50 fermergacha qo'lda; avtomatlashtirish faqat
> nima ishlashi ma'lum bo'lgach.

### 7.2 Reklama tuzilishi

- **Taklif:** "Dalangizni sun'iy yo'ldoshdan **bepul** tekshirib beramiz — qaysi qismi sust ekanini
  ko'rsatamiz." Sotuv emas, foyda.
- **Kanal:** Telegram Ads / Instagram / Facebook — qaysi biri arzonroq fermer olib kelishini o'lchang.
- **Tajriba matritsasi:** 3 hudud × 2 xabar. Har yacheykaga bir xil kichik byudjet.
  - Xabar 1 — **suv:** "Suv navbatingizni qaysi dalaga sarflashni bilasizmi?"
  - Xabar 2 — **holat:** "Dalangizning qaysi qismi orqada qolyapti — kosmosdan ko'ring."
- Har bir murojaatda yozib boring: hudud, xabar, ekin, maydon, dala soni, sug'orish usuli (navbat/nasos/lalmi).

### 7.3 Nima o'lchanadi

| Ko'rsatkich | Nima deydi |
|---|---|
| Murojaat narxi (CPL) hudud × xabar bo'yicha | Qayerda va qaysi og'riq kuchliroq |
| Kontur yuborganlar % | Qiziqish haqiqiymi yoki shunchaki bosish |
| Natijadan keyin javob bergan % | Natija tushunarli va foydalimi |
| "Xulosa to'g'rimi?" — ✅/➖/❌ | **L2 aniqligi** |
| 2-hafta qaytib so'ragan % | Haqiqiy qiymat (eng muhim raqam) |
| "Oyiga X so'm to'larmidingiz?" — ha % | To'lovga tayyorlik (so'zda — ehtiyot bilan) |
| Do'stini taklif qilgan | Tarmoq effekti boshlanishi (`referral/` bor) |

### 7.4 Intervyu savollari (har fermer bilan 10–15 daqiqa)

Faqat **o'tgan voqea** haqida so'rang ("qilarmidingiz?" emas, "oxirgi marta qanday qildingiz?"):

1. Oxirgi mavsumda eng katta yo'qotish nima bo'lgan? Qachon bilib qoldingiz?
2. Suvni qanday olasiz — navbat, nasos, quduq? Navbat qancha vaqtda bir keladi? Oxirgi marta suv yetmay qolganmi?
3. Dalalaringizni qanchalik tez-tez aylanasiz? Qaysi biriga borolmay qolasiz?
4. Hozir maslahatni kimdan olasiz — agronom, qo'shni, sotuvchi, Telegram guruh? Unga to'laysizmi?
5. Biror agro ilova ishlatganmisiz? (Growz, Apollo, boshqa) Nima yoqdi / nega to'xtatdingiz?
6. Bizning natijamiz: to'g'rimi? Qaysi qismi tushunarsiz bo'ldi? Bu bilan nima qilasiz?
7. Kim qaror qiladi — o'zingizmi, klaster, xo'jayin?
8. O'g'it, yoqilg'i, suvga mavsumda qancha sarflaysiz? (4.4 uchun asos)

### 7.5 Qaror qoidasi (tajribadan keyin)

Natija shu faylning **10-bo'limiga** yoziladi. Qaror:
- **Hudud:** CPL × qaytib kelish % bo'yicha eng yaxshi + egasi borib tura oladigan.
- **Segment:** 2-hafta qaytib kelish va to'lovga tayyorlik eng yuqori bo'lgan.
- **Xabar (suv yoki holat):** mahsulot asosiy ekrani va bot birinchi xabari shunga quriladi.
- 20+ fermerdan keyin ham "qaytib kelish" < 20% bo'lsa — mahsulot qiymati yetarli emas,
  funksiya qo'shish emas, **xabar va formatni** qayta ko'rib chiqing.

---

## 8. Narx modeli (dastlabki)

Kelishilgan qaror (roadmap, 2026-10-26) saqlanadi: 1-tahlil ochiq, 1 muammo to'liq, qolganlari 🔒 soni bilan;
soni faqat haqiqiy. Qo'shimcha:

- **Fermer tarifi gektarga emas, dalaga/xo'jalikka** — kichik fermerga sodda, tushunarli.
- Narxni **tajribada** aniqlang (7.3): 2–3 narx nuqtasini turli guruhga taklif qiling.
- **B2B — gektar bo'yicha yillik** (klaster, bank) — ular gektar bilan o'ylaydi.
- Birinchi pullik mijoz — fermerdan ko'ra **B segment** yoki **klaster** bo'lishi ehtimoli katta.

---

## 9. Texnik roadmap'ga ta'siri

| Faza | Strategiyadagi o'rni | Tavsiya |
|---|---|---|
| L2 aniqlik | 4.2 — marketing quroli; 7.1 — concierge bilan ground truth | **Davom etsin, birinchi o'rinda.** Concierge MVP bilan birga |
| L3 motor | Aniqlik o'lchovi qaysi xulosa adashishini ko'rsatgach | O'zgarishsiz |
| Suv navbati (yangi) | 4.1 — asosiy ajralish | Intervyu tasdiqlasa — yangi faza, spec avval |
| H3 "Bajardim" | 4.3 — yopiq tsikl | L2 dan keyin oldinga surish |
| H9 dala daftari | 4.4 — pul tili | Oldinga surish (hozir oxirgi bloklarda) |
| H10 qo'shnilar | 4.6 — tarmoq | Zichlik bo'lgach (1 tumanda 30+ fermer) |
| H6 rasm tashxisi | Raqobatchilar maydoni | Tayyor model/AI bilan, **o'z modeli noldan qurilmaydi** |
| Ko'p-tashkilot (tenant) | 4.5 — klaster/bank | B2B suhbatdan keyin, talab aniq bo'lsa |
| Bot / Mini App | Kanal | Concierge'dan qaysi xabar ishlagani ma'lum bo'lgach |

---

## 10. Tajriba natijalari (to'ldiriladi)

| Sana | Hudud | Xabar | Murojaat | Kontur | Javob | 2-hafta qaytdi | Izoh |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

---

## 11. Xavflar

| Xavf | Belgisi | Javob |
|---|---|---|
| Fermer to'lamaydi | Qaytib keladi, lekin "pul yo'q" | B2B2C (4.5) ga tezroq o'tish |
| Sun'iy yo'ldosh signali kichik dalada shovqinli | L2 da ❌ ko'p, < 1 ga dalalarda | Minimal maydon chegarasi, B segmentga fokus |
| Raqobatchi Telegram + sun'iy yo'ldoshni qo'shadi | Apollo allaqachon Telegram'da | Ajralish texnologiyada emas: aniqlik, tsikl, tarmoq, suv navbati |
| Growz kapitali va hamkorlari katta | — | To'qnash raqobat qilmang; tor hudud + chuqur moslik |
| Oshirilgan pul va'dasi ishonchni buzadi | Fermer "aytgandek bo'lmadi" | Ochiq formula, oraliq, mo'tadil (4.4) |
| Concierge vaqt yeydi | Egasi kodga vaqt topolmaydi | 20–50 fermer chegarasi; keyin faqat ishlagan qismni avtomatlashtirish |

---

## 12. Ochiq savollar (egasi uchun)

- [ ] **Amuflow AI** — sayt, kanal, nima qiladi?
- [ ] Growz va Apollo'ni o'zingiz sinab ko'ring (fermer ko'zi bilan): nima yoqdi, nima jahlni chiqardi — 2-bo'limga yozing.
- [ ] Reklama byudjeti va muddati (7.2).
- [ ] Qaysi hududlarga jismonan borib tura olasiz (6-bo'lim, oxirgi mezon)?
- [ ] Bank/klasterdan tanish bormi — 1 ta suhbat uchun (4.5)?
