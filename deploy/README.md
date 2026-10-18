# Dropy — prodga chiqarish (bitta VPS)

Hammasi bitta serverda, Docker Compose bilan:

| Servis | Nima | Tashqariga |
|---|---|---|
| `nginx` | HTTPS, admin panel (`/`), API proksi (`/api/`) | 80, 443 |
| `backend` | Spring Boot (bot ham shu yerda — long polling) | yo'q |
| `satellite` | Python tahlil xizmati | yo'q |
| `db` | PostgreSQL 16 | yo'q |
| `certbot` | Let's Encrypt sertifikatini yangilaydi | yo'q |
| `backup` | Har kuni `pg_dump`, 14 kun saqlanadi (`deploy/backups/`) | yo'q |

Mini App (`miniapp/`) — Vercel'da qoladi (statik, bepul).

## Server o'lchami

Hisob: 1000+ fermer, har biri 1–5 dala. Yuk asosan **Python tahlil** (bitta tahlil 30–90 s, 1–1,5 GB RAM
cho'qqisi) va kunduzgi **monitoring** (soatiga 40 dala). Bot va admin — yengil.

| | Pilot (≤ 300 fermer) | **1000+ fermer (tavsiya)** | 5000+ |
|---|---|---|---|
| vCPU | 2 | **4** | 8 |
| RAM | 4 GB | **8 GB** | 16 GB |
| Disk | 40 GB SSD | **80 GB SSD** | 160 GB |
| Taxminiy narx | ~$7–12/oy | **~$15–30/oy** | ~$50/oy |

- Misollar: Hetzner CPX31 (4 vCPU / 8 GB, ~€15), DigitalOcean 4 vCPU / 8 GB (~$48), Contabo VPS M.
- Mintaqa: Yevropa (Germaniya/Finlandiya) — O'zbekistondan ping yaxshi, Sentinel-2 (AWS us-west-2) ga ham yaqinroq
  bo'lgani yaxshi. O'zbekistondagi hosting ham bo'ladi, faqat Docker va 8 GB RAM bo'lsin.
- 8 GB nima uchun: Python 2 ta parallel tahlil (≈3 GB) + Java (≈1 GB) + Postgres (≈1 GB) + zaxira.
- Disk o'sishi: tahlil rasmlari ~0,5 MB/tahlil → 1000 fermer × oyiga 4 tahlil ≈ 2 GB/oy.

## 1. Server tayyorlash (bir marta)

```bash
# Ubuntu 24.04
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable

git clone <repo> dropy && cd dropy/deploy
```

DNS: domen (masalan `dropy.uz`) A yozuvi → VPS IP. Tekshirish: `dig +short dropy.uz`.

> Barcha `docker compose` buyruqlari `deploy/` papkasida va **`--env-file .env.prod`** bilan
> (aks holda compose `.env` ni qidiradi va `DB_PASSWORD is missing` deydi). Qisqartma:
> `alias dc='docker compose -f compose.prod.yaml --env-file .env.prod'`

## 2. Sirlar

```bash
cp .env.prod.example .env.prod && chmod 600 .env.prod
openssl rand -hex 24      # → DB_PASSWORD
openssl rand -hex 24      # → SATELLITE_INTERNAL_KEY
nano .env.prod            # DOMAIN, TELEGRAM_BOT_TOKEN (PROD bot!), ADMIN_EMAIL/ADMIN_PASSWORD, OPEN_METEO_API_KEY ...

# JWT kalitlari — FAQAT serverda yasaladi (dev kalitlarini ko'chirmang!)
mkdir -p secrets/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/keys/private.pem
openssl rsa -in secrets/keys/private.pem -pubout -out secrets/keys/public.pem
chmod 600 secrets/keys/private.pem
sudo chown -R 10002 secrets/keys        # backend konteyneri uid 10002 bilan o'qiydi
```

## 3. Birinchi sertifikat

nginx sertifikatsiz ishga tushmaydi — avval vaqtinchalik certbot (standalone, 80-port):

```bash
docker run --rm -p 80:80 -v $PWD/certbot/conf:/etc/letsencrypt certbot/certbot:v3.0.1 \
  certonly --standalone -d dropy.uz --email siz@mail.uz --agree-tos --no-eff-email
```

Keyin `certbot` servisi har 12 soatda yangilaydi (webroot). nginx yangi sertifikatni o'qishi uchun
haftada bir: `docker compose -f compose.prod.yaml --env-file .env.prod exec nginx nginx -s reload` (cron'ga qo'ying).

## 4. Ishga tushirish

```bash
docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
docker compose -f compose.prod.yaml --env-file .env.prod logs -f backend     # "Started AgroApplication", "telegram bot long polling boshlandi"
```

- Flyway barcha migratsiyalarni toza bazaga qo'yadi. Dev seed foydalanuvchilar prodga **tushmaydi**.
- Birinchi admin — `.env.prod` dagi `ADMIN_EMAIL`/`ADMIN_PASSWORD` bilan yaratiladi. `https://dropy.uz` ga kiring,
  **Profil → parolni almashtiring**, keyin `.env.prod` dan bu ikki qatorni o'chirib `up -d` qiling.
- Ochiq ro'yxatdan o'tish (`/auth/register`) prodda o'chiq — xodimlarni admin "Xodimlar" sahifasidan qo'shing.

## 5. Tekshirish

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://dropy.uz/                    # 200 — admin panel
curl -s -o /dev/null -w "%{http_code}\n" https://dropy.uz/api/v1/auth/me      # 401 — API ishlayapti
curl -s -o /dev/null -w "%{http_code}\n" https://dropy.uz/actuator/health     # 404 — tashqaridan yopiq
docker compose -f compose.prod.yaml --env-file .env.prod ps                                         # hammasi healthy/running
```

Admin → **Muammolar** — xizmatlar holati (Python, bot, ob-havo, navbatlar) yashil bo'lishi kerak.

Telegram: botga `/start` → menyu, "🛰 Tahlil" (chat yonidagi tugma) va "🗺 Yangi dala" ishlashi kerak.
`MINIAPP_URL` Vercel'dagi Mini App; u `PUBLIC_API_URL` (= `https://DOMAIN`) ga dala yuboradi.

## 6. Yangilash (yangi versiya)

```bash
cd dropy && git pull
cd deploy && docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
```

Migratsiyalar ishga tushishda o'zi qo'llanadi. Mini App: `miniapp/` o'zgargan bo'lsa — Vercel'ga deploy.

## 7. Zaxira va tiklash

- DB: `deploy/backups/agro-YYYY-MM-DD.dump` (har kuni, 14 kun). **Serverdan tashqariga ham** ko'chiring
  (masalan haftada bir `rsync` yoki Hetzner Storage Box) — server yo'qolsa zaxira ham yo'qolmasin.
- Fayllar (rasmlar, PDF): `files` volume — `docker run --rm -v dropy_files:/f -v $PWD:/b alpine tar czf /b/files.tgz -C /f .`
- Tiklash:
  ```bash
  docker compose -f compose.prod.yaml --env-file .env.prod exec -T db pg_restore -U agro -d agro --clean --if-exists < backups/agro-2026-10-15.dump
  ```

## 8. Prodga chiqishdan oldin (checklist)

- [ ] PROD uchun **alohida** Telegram bot tokeni (dev bot bilan bir xil token — ikki poller, xabarlar yo'qoladi)
- [ ] `OPEN_METEO_API_KEY` (tijorat; bepul API faqat notijorat)
- [ ] `ADMIN_PASSWORD` kirgandan keyin almashtirildi va `.env.prod` dan o'chirildi
- [ ] Zaxira serverdan tashqariga ko'chirilyapti
- [ ] UptimeRobot (bepul): `https://DOMAIN/` har 5 daqiqada — server o'chsa SMS/Telegram
- [ ] Admin → Sozlamalar: tariflar (Bepul — jami 20, Pro — jami 200 gektar, Agro Biznes — narx kelishiladi), bot menyusi bo'limlari
- [ ] Admin → Sozlamalar → Yo'riqnoma videosi (uz/ru/en) yuklangan
