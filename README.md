# Agro — Spring Boot 4 (Auth + RBAC asosida)

`project-v1` dan olingan, **ishlab turgan** auth/RBAC boilerplate.
Klon qiling, nomini almashtiring, ishlating. Barcha qarorlar va tuzoqlar — **`CLAUDE.md`** da.

Stack: Spring Boot **4.1.1**, Java **26**, PostgreSQL 16+, Gradle.

---

## Tez boshlash

```bash
git clone <repo-url> my-app && cd my-app

createdb agro
cp .env.example .env              # DB_USERNAME / DB_PASSWORD
./gradlew bootRun
```

Shu bilan tamom. Birinchi build'da:
- **RSA kalitlari** avtomatik yasaladi (`src/main/resources/keys/`, git'ga tushmaydi);
- Flyway sxemani yaratadi, `dev` profilda test foydalanuvchilarni qo'shadi.

```bash
curl -s localhost:8081/actuator/health
open http://localhost:8081/swagger-ui.html
```

Dev foydalanuvchilar, paroli `Parol12345`:
`admin@app.co` · `user@app.co` · `manager@app.co` · `operator@app.co`

---

## O'z loyihangizga moslashtirish

| Nima | Qayerda |
|---|---|
| Paket `uz.app.agro` | IntelliJ → *Refactor → Rename*. Keyin `grep -rn "uz.app.agro" src` — `WebConfig` va `application-*.yml` dagi satrlar |
| Main klass | `AgroApplication` |
| Loyiha nomi | `settings.gradle`, `build.gradle` (`description`) |
| Ilova / baza / issuer | `application.yml` → `spring.application.name`, `DB_URL` default, `jwt.issuer` |
| Swagger sarlavhasi | `common/config/OpenApiConfig` |

> 🔴 Boshqa loyihadan `keys/` papkasini **ko'chirmang** — bir xil kalit bilan
> imzolangan token ikkala loyihada ham yaroqli bo'ladi.

---

## Ichida nima bor

| Qism | Tafsilot |
|---|---|
| **Auth** | JWT **HttpOnly cookie**da, RSA RS256, `oauth2-resource-server` |
| **Sessiya** | `sessions` jadvali + `sid` claim → token'ni darhol bekor qilish |
| **Refresh** | `SecureRandom` 256 bit, DB'da SHA-256 hash, **rotatsiya + o'g'irlik aniqlash** |
| **RBAC** | `users ──< user_roles >── roles ──< role_permissions >── permissions` |
| **Huquqlar** | 🔴 Token'da **EMAS** — har so'rovda DB'dan. Rol o'zgarsa keyingi so'rovdayoq kuchga kiradi |
| **Avtorizatsiya** | `@PreAuthorize` service qatlamida + egalik sharti |
| **CSRF** | double-submit cookie + `CookieCsrfEnforcementFilter` |
| **Brute-force** | `failed_attempts` / `locked_until`, vaqtinchalik blok |
| **Audit** | `audit_logs` (JSONB), fluent API, `GET /api/v1/audit` |
| **Header'lar** | CSP, Referrer-Policy, Permissions-Policy, HSTS — **har bir** zanjirda |
| **Logging** | `MdcFilter` — `requestId` + `userId`, prodda ECS JSON |
| **Docs** | springdoc 3.0.3, `/swagger-ui.html` (prodda yopiq) |
| **Actuator** | `/actuator/health` ochiq, qolgani `denyAll` |
| **Migration** | Flyway V1–V5 + dev seed alohida (`db/seed`), qisman unique index'lar |

---

## Endpoint'lar

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login            → AUTH-TOKEN + REFRESH-TOKEN cookie
POST   /api/v1/auth/refresh          → ikkala token yangilanadi
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
GET    /api/v1/auth/sessions
DELETE /api/v1/auth/sessions/{id}

GET    /api/v1/users                 user:read
GET    /api/v1/users/{id}            user:read yoki o'zi
GET    /api/v1/users/find-by-email   user:read

GET    /api/v1/roles                 role:read
GET    /api/v1/roles/detailed        role:read
GET    /api/v1/roles/{id}            role:read
POST   /api/v1/roles                 role:create
PUT    /api/v1/roles/{id}            role:update

GET    /api/v1/audit                 audit:read
GET    /actuator/health
GET    /swagger-ui.html              (dev)
```

### So'rov tartibi (CSRF tufayli)

```
POST /auth/login   →  AUTH-TOKEN keladi, XSRF-TOKEN KELMAYDI
GET  /auth/me      →  XSRF-TOKEN shu yerda keladi
POST/PUT/DELETE    →  X-XSRF-TOKEN header bilan
```

---

## Fayl tuzilishi

```
src/main/java/uz/app/agro/
    AgroApplication.java
    common/     BaseEntity, PageResponse, MdcFilter  + config/, exception/
    security/   SecurityConfig, JWT, Session, cookie, brute-force
    auth/       AuthController, AuthService  + dto/
    user/       UserEntity, UserController, UserService, UserRepository, UserMapper  + dto/
    rbac/       Role, Permission, RoleController, RoleService, RoleMapper  + dto/
    audit/      AuditLog, AuditService, AuditController, ...
src/main/resources/
    application.yml  application-dev.yml  application-prod.yml
    db/migration/    V1 … V5        ← har doim
    db/seed/         R__dev_users   ← faqat dev profil
    keys/            ← build'da yasaladi, git'da yo'q
.env.example       ← cp .env.example .env
CLAUDE.md          ← qarorlar, tuzoqlar, qurish tartibi, tekshirish retseptlari
docs/reference/project-v1-roadmap.md   ← 50 ta ADR va o'tilgan yo'l tarixi
```

Paket qoidasi: **package-by-feature**, bitta fayl uchun papka ochilmaydi.

---

## Ataylab YO'Q narsalar

| | Sabab |
|---|---|
| Avtomatik testlar | `CLAUDE.md` → ADR #39 |
| `PUT /users/{id}/roles` (rol biriktirish) | ADR #50 — rol `psql` orqali beriladi |
| `DELETE /roles/{id}`, `DELETE /users/{id}` | ADR #50 |
| CORS | ADR #25 — dev proxy + prodda bitta origin |

---

## Prod

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=... DB_USERNAME=... DB_PASSWORD=... \
JWT_PRIVATE_KEY=file:/etc/app/keys/private.pem \
java -jar build/libs/agro-0.0.1-SNAPSHOT.jar
```

Prod profilida: dev seed o'qilmaydi, Swagger yopiq, cookie `Secure`, log ECS JSON.

[//]: # (Docker)
docker compose up -d

[//]: # (Vercel)
npx vercel deploy --prod