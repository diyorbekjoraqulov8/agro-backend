# CLAUDE.md — auth-rbac-starter

> **project-v1** dan olingan boilerplate: auth + RBAC + production hardening.
> Unda to'plangan qarorlar, tuzoqlar va ishlaydigan kod bor.
> Maqsad — har yangi loyihada o'sha yo'lni **qaytadan bosib o'tmaslik**.

## Yangi loyihaga moslashtirish — birinchi qadam

Kod `uz.app.starter` paketida. GitHub'dan klon qilingach:

1. **Paket** — IntelliJ: `uz.app.starter` ustida *Refactor → Rename* (yoki `Shift+F6`).
   `WebConfig` dagi `startsWith("uz.app.starter")` va yml'dagi `logging.level.uz.app.starter`
   satr bo'lgani uchun refactor ularni topmasligi mumkin — `grep -rn "uz.app.starter" src`.
2. **Nomlar** — `settings.gradle` (`rootProject.name`), `build.gradle` (`description`),
   `application.yml` (`spring.application.name`, baza nomi, `jwt.issuer`),
   `OpenApiConfig` (sarlavha), main klass `StarterApplication`.
3. **Sirlar** — `cp .env.example .env` va DB ma'lumotlarini yozing.
4. **RSA kalitlari** — hech narsa qilish shart emas: birinchi build'da Gradle
   `generateJwtKeys` task'i `src/main/resources/keys/` ga yangi juftlik yasaydi.

> 🔴 **Kalitlarni loyihalar orasida ko'chirmang** va commit qilmang (`.gitignore` da).
> Ikki loyiha bir xil RSA kaliti bilan token imzolasa, biridagi token ikkinchisida
> ham yaroqli bo'ladi.

---

## 1. ISH USLUBI

Loyiha egasi auth/RBAC ni **project-v1 da to'liq o'rgangan**. Ya'ni:

- ✅ **Kodni to'g'ridan-to'g'ri yoz** — skelet berib kutish shart emas.
  Repo'dagi kod tayyor, uni moslashtirish kifoya.
- ✅ Yangi, **bu skeletda yo'q** narsa qilinayotganda — avval qisqa
  spetsifikatsiya, keyin kod.
- ✅ Har fazadan keyin **qo'lda tekshirish buyruqlari** ber (`curl`, `psql`) —
  8-bo'limdagi retseptlar.
- ❌ **Avtomatik test yozishni taklif qilma.** Loyiha egasining qarori
  (project-v1 ADR #39): yakka ishlaydigan loyiha, CI yo'q. Jamoa yoki CI
  paydo bo'lsa — o'shanda gaplashiladi.
- ❌ Boot 4 API mavjudligiga **ishonchsiz bo'lsang — aytib qo'y**, taxmin qilib
  "ishonchli" tarzda berma. Internetdagi misollarning aksariyati Boot 2/3 uchun.
- ✅ Tushuntirish **o'zbekcha**, texnik atamalar inglizcha qoladi.

---

## 2. STACK

| | |
|---|---|
| Spring Boot | **4.1.1** (→ Spring Framework 7, Spring Security 7) |
| Java | **26** |
| Build | Gradle, Groovy DSL |
| DB | PostgreSQL 16+, Hibernate/JPA, Flyway |
| Auth | JWT, `spring-boot-starter-oauth2-resource-server` (**jjwt EMAS**) |
| Mapping | MapStruct 1.6.3, `unmappedTargetPolicy = ERROR` |
| Docs | springdoc-openapi **3.0.3** (3.x = Boot 4; 2.x = Boot 3) |
| API prefiks | `/api/v1` — `WebConfig` qo'shadi |

### Boot 4 da ko'chgan/o'zgargan narsalar

| Boot 3 | **Boot 4** |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `flyway-core` yetarli | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`) |
| `o.s.b.actuate.autoconfigure.security.servlet.EndpointRequest` | `o.s.b.security.autoconfigure.actuate.web.servlet.EndpointRequest` |
| `o.s.b.actuate.health.HealthEndpoint` | `o.s.b.health.actuate.endpoint.HealthEndpoint` |
| springdoc 2.x | springdoc **3.0.3** |
| Testcontainers `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` (2.x) |

---

## 3. PAKET TUZILISHI

**Package-by-feature** — `controllers/`, `services/` degan global paketlar yo'q.
**Bitta fayl uchun papka ochilmaydi** — entity, mapper, controller, service feature
paketining o'zida turadi. Alohida papka faqat bir nechta fayl bo'lganda: `dto/`.

```
uz.app.starter
├── common/          BaseEntity, PageResponse, MdcFilter
│   ├── config/      WebConfig, JpaAuditingConfig, AuditorConfig, OpenApiConfig
│   └── exception/   AppException, NotFoundException, ConflictException, GlobalExceptionHandler
├── security/        SecurityConfig, JwtConfig, JwtService, CookieService, Session, brute-force
├── auth/            AuthController, AuthService  + dto/
├── user/            UserEntity, UserController, UserService, UserRepository, UserMapper  + dto/
├── rbac/            Role, Permission, RoleController, RoleService, RoleMapper, Permissions, RoleNames  + dto/
└── audit/           AuditLog, AuditService (fluent), AuditWriter, AuditController, AuditLogResponse
```

Yangi feature: `XEntity`, `XController`, `XService`, `XRepository`, `XMapper` — feature
paketida; DTO'lar ko'p bo'lsa `dto/`. Papka fayl soni o'sganda ochiladi, oldindan emas.

---

## 4. KOD KONVENSIYALARI

- Entity nomi: `UserEntity` (izchil bo'lsin)
- DTO: `record`
- Controller **hech qachon** Entity qaytarmaydi — faqat DTO
- Service interfeysi **shart emas** — ikkinchi implementatsiya paydo bo'lgunga qadar
- Lombok: `@Getter/@Setter/@RequiredArgsConstructor`.
  **`@Data` va `@AllArgsConstructor` entity'da ishlatilmaydi** (equals/hashCode tuzoq)
- DI — **faqat constructor** (`@RequiredArgsConstructor`), `@Autowired` field'ga qo'yilmaydi
- Service klassida `@Transactional(readOnly = true)`, yozuvchi metodda `@Transactional`
- `@PreAuthorize` — **service** qatlamida, controller'da emas
- Tekshiruv metodlari: `assert*` / `guard*` prefiksi

---

## 5. 🔴 TUZOQLAR — jimgina ishlamay qoladigan narsalar

Bu bo'lim faylning eng qimmatli qismi. Hammasi project-v1 da **real** uchragan,
har biri **xato bermagan**.

### 5.1 Annotatsiya yozilgan, lekin ishlamaydi

| Annotatsiya | Ishlashi uchun kerak | Bo'lmasa |
|---|---|---|
| `@PreAuthorize` | `@EnableMethodSecurity` | endpoint **hammaga ochiq** |
| `@NotBlank` / `@Pattern` (`@ConfigurationProperties`da) | `@Validated` | sozlama `null` bo'lib o'tadi |
| `@Transactional` | metod **`public`** bo'lishi | tranzaksiya **umuman yo'q** |
| `@Transactional`, `@PreAuthorize`, `@Cacheable` | **proxy** orqali chaqirilishi | self-invocation'da ishlamaydi |
| `.cors()`, `.headers()`, `.csrf()` | **har bir** `SecurityFilterChain` da | boshqa zanjirda yo'q |

Tasdiq: `spring-tx` da `AnnotationTransactionAttributeSource()` → `publicMethodsOnly = true`;
`computeTransactionAttribute` → `!isPublic(method)` bo'lsa `null` qaytaradi.

**Qoida: yangi xavfsizlik/tranzaksiya annotatsiyasi qo'shganda uni BUZIB SINA**
(yo'q qilib, kutilgan xato chiqishini ko'r).

### 5.2 Spring Security

- **CSRF jimgina o'chadi.** `oauth2ResourceServer` CSRF'ning "himoya talab qiladi"
  matcher'iga `BearerTokenRequestMatcher` istisnosini qo'shadi. Token cookie'dan
  o'qilsa — *har* so'rov istisnoga tushadi. Yechim: `CookieCsrfEnforcementFilter`.
- **`setJwtValidator` standart tekshiruvlarni ALMASHTIRADI** — `exp` va `iss`
  validatorlarini qo'lda qo'shish shart.
- **Clock skew 60s** — `JwtValidators.createDefaultWithIssuer` defaulti.
  Muddati o'tgan token 60 soniya ishlayveradi. `new JwtTimestampValidator(Duration.ofSeconds(5))`.
- **Eski cookie `permitAll` endpoint'ni ham 401 qiladi** — autentifikatsiya
  avtorizatsiyadan **oldin** ishlaydi. Shuning uchun `login/register/refresh`,
  `swagger`, `actuator` zanjirlarida `oauth2ResourceServer` **yo'q**.
- **O'z klassingga Spring sinfi bilan bir xil nom berma**
  (`JwtAuthenticationConverter`, `BearerTokenResolver`) — u Spring'nikini soya qiladi.
- **XOR CSRF handler cookie bilan mos kelmaydi** — `CsrfTokenRequestAttributeHandler`
  + `setCsrfRequestAttributeName(null)` kerak.

### 5.3 JPA / Hibernate / Flyway

- **`ddl-auto: validate` tekshirmaydi:** ustun uzunligi, index, unique, FK, check.
  Faqat jadval/ustun nomi va tipi.
- **Soft delete + oddiy `UNIQUE` = tuzoq.** O'chirilgan qiymat abadiy band bo'ladi.
  **Qisman index** kerak: `CREATE UNIQUE INDEX ... WHERE deleted = FALSE`.
  Natijada entity'da `unique = true` **YOZILMAYDI**.
- **Qisman index bilan `ON CONFLICT (name)` ishlamaydi** —
  `ON CONFLICT (name) WHERE deleted = FALSE DO NOTHING` deb yoziladi.
- **`@SQLRestriction` bulk `UPDATE` ga qo'llanishiga ishonma** — JPQL'da
  `AND u.deleted = false` ni qo'lda yoz.
- **`open-in-view: true` N+1 ni yashiradi** — `application.yml` da `false`,
  faqat prodda emas (dev/prod parity).
- **Bulk `@Modifying` dan keyin persistence context eski qiymatni qaytaradi** —
  `clearAutomatically = true, flushAutomatically = true`.
- **Read-modify-write = lost update.** Sanoqni `SET x = x + 1` bilan atomik oshir.
- **`V10` glob tartibida `V2` dan oldin keladi** — qo'lda sinashda `sort -V`:
  `for f in $(ls db/migration/V*.sql | sort -V); do psql -v ON_ERROR_STOP=1 -f "$f"; done`
- **MapStruct `unmappedTargetPolicy = ERROR`** — entity'ga maydon qo'shilsa build
  yiqiladi. Bu **foydali** tuzoq: `@Mapping(target="...", ignore=true)` qo'shasiz
  va mass-assignment xavfi yo'qoladi.

### 5.4 Tranzaksiya

**Xavfsizlik amali biznes tranzaksiyasiga bog'liq bo'lmasligi kerak.**

Exception bilan tugaydigan yo'llarda (`login` xatosi, refresh token o'g'irligi)
oddiy `@Transactional` bo'lsa — yozuv **rollback bo'ladi va yo'qoladi**.
`@Transactional(propagation = REQUIRES_NEW)` kerak.

Qoida:
- `REQUIRES_NEW` — chaqiruvchi rollback bo'lishi **kutilgan** joyda
  (`LoginAttemptService`, audit'ning `recordIndependently`)
- `MANDATORY`/`REQUIRED` — biznes o'zgarishi bilan **atomik** bo'lishi kerak joyda
- Metod allaqachon `REQUIRES_NEW` bo'lsa — ichidagi audit `record()` (qo'shiladi), yetarli

### 5.5 Konfiguratsiya

- **`yml` da vaqt birligi MAJBURIY.** `3` = **3 millisekund**. `3d`, `15m`, `30s`.
- **Yechilmagan `${VAR}` exception tashlamaydi** — literal satr bo'lib bog'lanadi.
  Tasdiq: `PropertySourcesPlaceholdersResolver` → `ignoreUnresolvablePlaceholders = true`.
  Shuning uchun env sozlamasiga `@NotBlank` emas, **`@Pattern`** (shakl tekshiruvi).
- **Xavfsiz default** — `application.yml` da qattiq qiymat (`secure: true`),
  `application-dev.yml` bo'shashtiradi. Profil unutilsa prod sizib ketmasin.
- **`show-sql: true` ishlatma** — `System.out` ga yozadi, MDC/vaqt belgisi yo'q.
  O'rniga `logging.level.org.hibernate.SQL: DEBUG`.
- **`logging.pattern.correlation`** — butun pattern'ni qayta yozma, Boot'ning
  `CONSOLE_LOG_PATTERN` ichida `${LOG_CORRELATION_PATTERN:-}` bo'sh joyi bor.

### 5.6 Java / kutubxona

- **`@Value` importi:** `org.springframework.beans.factory.annotation.Value`.
  `lombok.Value` — klass annotatsiyasi, `Cannot find @interface method 'value()'`.
- **`Map.of` null qiymatni qabul qilmaydi → NPE.** Ixtiyoriy maydonli xarita
  uchun `HashMap` + `put`.
- **`UUID.fromString(null)` → NPE**, `IllegalArgumentException` emas. Null tekshiruvi avval.
- **Enum'da `EnumType.ORDINAL` ishlatma** (ayniqsa audit'da) — tartib o'zgarsa
  butun tarix ma'nosini o'zgartiradi. `EnumType.STRING`.
- **`MDC` — `ThreadLocal`, Tomcat thread'ni qayta ishlatadi.** `finally { MDC.clear(); }`
  bo'lmasa oldingi so'rovning `userId` si keyingisiga yopishadi.
- **Mijozdan kelgan header log'ga tushsa — sanitizatsiya qil** (`[A-Za-z0-9._-]`, uzunlik).
  Aks holda `\n` bilan soxta log qatori yasaladi (log forging, CWE-117).
- **Xavfsizlik taqqoslashida `equals` emas, `MessageDigest.isEqual`** (doimiy vaqt).

### 5.7 springdoc / actuator

- **`addPathPrefix` predikati paket bilan cheklansin.** springdoc'ning
  `OpenApiWebMvcResource` ham `@RestController` — aks holda `/v3/api-docs`
  `/api/v1/v3/api-docs` bo'lib ketadi va Swagger UI spec'ni topa olmaydi.
- **CSP `default-src 'none'` Swagger UI'ni buzadi** — unga alohida zanjirda
  `'unsafe-inline'` beriladi, `/api/**` da qattiq qoladi.
- **Cookie auth'da Swagger "Authorize" tugmasi ishlamaydi** — `Cookie` brauzerda
  *forbidden header name*, JS uni o'rnata olmaydi. `addSecurityItem` qo'shilmaydi.
- **`exposure.include: "*"` HECH QACHON** — `/env`, `/configprops`, `/heapdump`.
- **Actuator zanjirida `oauth2ResourceServer` yo'q** — health check qiluvchi
  (nginx, Docker, k8s) autentifikatsiya qila olmaydi.

---

## 6. ARXITEKTURA QARORLARI

| # | Qaror | Sabab |
|---|---|---|
| 1 | JWT uchun `oauth2-resource-server`, jjwt emas | Kam kod, standart xato boshqaruvi |
| 2 | Rol/permission **DB'da**, enum emas | Yangi rol uchun deploy kerak emas |
| 3 | Endpoint **permission** tekshiradi, rol emas | Huquqni kodsiz o'zgartirish |
| 4 | `ddl-auto: validate` + Flyway | Takrorlanuvchi, versiyalangan sxema |
| 5 | MapStruct `unmappedTargetPolicy = ERROR` | Unutilgan maydon build'ni yiqitadi |
| 6 | Soft delete: `@SQLDelete` + `@SQLRestriction` | Default xavfsiz, admin uchun eshik ochiq |
| 7 | **Qisman** unique index | Soft delete bilan oddiy unique qiymatni abadiy band qiladi |
| 8 | `GlobalExceptionHandler extends ResponseEntityExceptionHandler` | MVC'ning ~20 standart xatosi 500 bo'lib ketmasin |
| 9 | JWT **cookie**da, `Authorization` header'da emas | `HttpOnly` ni XSS o'qiy olmaydi. Narxi: CSRF majburiy, CORS'da `*` yo'q |
| 10 | CSRF: plain handler + `setCsrfRequestAttributeName(null)` | XOR handler cookie'dagi xom token bilan mos kelmaydi |
| 11 | Token bekor qilish: **`sid` + `sessions` jadvali** (`token_version` emas) | Bir xil narx, ko'proq imkoniyat: qurilma bo'yicha chiqarish, "faol seanslarim" |
| 12 | Access token'ni DB'da saqlash **rad etildi** | Bitta qurilma bilan cheklaydi; token — bearer credential |
| 13 | 🔴 **Huquqlar token'da EMAS, har so'rovda DB'dan** | Rol o'zgarishi **keyingi so'rovdayoq** kuchga kiradi. `token_version`, `ver` claim, `SessionValidator` — hech biri kerak emas. Narx: +1 so'rov/request |
| 14 | Refresh token — **JWT emas**, `SecureRandom` 256 bit, DB'da SHA-256 hash | 256 bit tasodifiy qiymatga brute force yo'q, bcrypt ortiqcha |
| 15 | Rotatsiya + `previous_refresh_token_hash` tuzog'i | Qayta ishlatilsa — o'g'irlik, barcha sessiya bekor |
| 16 | IDOR'da **404**, 403 emas | Begona resurs borligini ham oshkor qilmaslik |
| 17 | `@PreAuthorize` **service**da | Service'ni boshqa controller/scheduler ham chaqiradi |
| 18 | SpEL ifodalari `Permissions` da **konstanta** | Runtime'da baholanadi, typo jimgina endpoint'ni yopadi |
| 19 | Blok `UserDetails.isAccountNonLocked()` orqali | Parol solishtirilmaydi ham; `LockedException` → mavjud handler 401 beradi (enumeration yo'q) |
| 20 | Bloklaganda `failed_attempts = 0` | Aks holda blok tugagach bitta xato darhol qayta bloklaydi |
| 21 | Brute-force: **hisob** ilovada, **IP** nginx'da | Password spraying vs credential stuffing — ikki xil hujum |
| 22 | `AuditLog` **`BaseEntity`dan meros olmaydi** | Append-only: `deleted` bo'lsa audit'dan iz yashirish mumkin |
| 23 | `actor_email` denormalizatsiya, `actor_id` da FK yo'q | Audit — immutable snapshot, user o'chsa ham tarix buzilmasin |
| 24 | Audit tranzaksiya chegarasi **alohida bean**da (`AuditWriter`) | Builder `this` orqali chaqirsa self-invocation, proxy chetlanadi |
| 25 | **CORS sozlanmaydi** — dev proxy + prodda bitta origin | `SameSite=Strict` saqlanadi (cross-origin bo'lsa `None` ga tushardi) |
| 26 | Xavfsizlik header'lari **har bir** zanjirda | Zanjirlar orasida sozlama meros bo'lmaydi |

---

## 7. QURISH TARTIBI

Har faza tugagach **to'xta** va tekshirish buyruqlarini ber.

| Faza | Nima | Natija |
|---|---|---|
| 0 | `BaseEntity`, auditing, exception qatlami, DTO, MapStruct | `/users` 200, `/users/999` 404, `/users/abc` 400 |
| 1 | `rbac/`: Role, Permission, `Permissions`, `RoleNames`; `UserEntity.roles` | `GET /users` → **1 so'rov** (`@EntityGraph`) |
| 2 | Flyway V1–V5 + dev seed (`db/seed`), `ddl-auto: validate`, qisman unique index | Migratsiyalar toza bazada o'tadi |
| 2.5 | Sirlar env'ga (`DB_PASSWORD` default'siz) | — |
| 3 | `PasswordEncoder`, `CustomUserDetails(Service)`, `SecurityConfig` | Basic auth bilan 200 |
| 4 | `JwtConfig` (RSA, cookie resolver), `JwtService` | JWT bilan `GET /users/1` → 1 so'rov |
| 4.5 | 🔴 **`DbAuthenticationConverter`** — huquqlar DB'dan | `DELETE FROM user_roles` → o'sha token bilan darhol 403 |
| 5A | register / login / logout / me | — |
| 5B | Refresh token rotatsiya + o'g'irlik aniqlash | Qayta ishlatilgan token → barcha sessiya bekor |
| 5C | `GET /auth/sessions`, `DELETE /auth/sessions/{id}` | Begona sessiya → 404 |
| 5D | `CookieCsrfEnforcementFilter` + `publicChain` | CSRF token'siz POST → 403 |
| 6 | `@EnableMethodSecurity` + `@PreAuthorize` + egalik | Rol matritsasi 200/403 |
| 7A | Profillar, `open-in-view: false`, Hikari, log | `open-in-view` ogohlantirishi yo'qoladi |
| 7B | CSP / Referrer-Policy / Permissions-Policy / HSTS | Har zanjirda 3/3 header |
| 7C | Brute-force: `failed_attempts`, `locked_until` | 5 xato → to'g'ri parol ham 401 |
| 7D | Audit log + `GET /audit` | `LOGIN_FAILURE` yozuvi saqlanadi |
| 7F | springdoc + `docsChain` | `/v3/api-docs` 200, `/api/v1/v3/api-docs` 404 |
| 7G | `MdcFilter` — `requestId` + `userId` | Bir thread'da auth→anonim: `userId` bo'shaydi |
| 7H | Actuator `/health` | Buzuq cookie bilan ham 200 |

### Filter chain tartibi (MUHIM)

```
@Order(-1) managementChain   /actuator/**              oauth2ResourceServer YO'Q
@Order(0)  docsChain         /swagger-ui, /v3/api-docs oauth2ResourceServer YO'Q, CSP bo'sh
@Order(1)  publicChain       /auth/login|register|refresh  oauth2ResourceServer YO'Q
@Order(2)  apiChain          qolgan hammasi            JWT + CSRF + @PreAuthorize
```

Uchtasida `oauth2ResourceServer` **ataylab yo'q** — muddati o'tgan `AUTH-TOKEN`
cookie `permitAll` endpoint'ni ham 401 qilib qo'yadi.

---

## 8. TEKSHIRISH RETSEPTLARI

### Migratsiyalarni ilovasiz sinash

```bash
dropdb --if-exists mig_test; createdb mig_test
for f in $(ls src/main/resources/db/migration/V*.sql | sort -V) src/main/resources/db/seed/R__*.sql; do
  psql -q -v ON_ERROR_STOP=1 -d mig_test -f "$f"
done
psql -d mig_test -c "\d users"
dropdb mig_test
```

### Login → CSRF → himoyalangan so'rov

```bash
J=/tmp/cj.txt && rm -f $J
curl -s -o /dev/null -c $J -X POST localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@app.co","password":"Parol12345"}'

# XSRF-TOKEN faqat apiChain'dan keladi — bitta GET kerak
curl -s -o /dev/null -b $J -c $J localhost:8081/api/v1/auth/me
CSRF=$(awk '/XSRF-TOKEN/ {print $7}' $J)

curl -s -o /dev/null -w "%{http_code}\n" -b $J -X POST localhost:8081/api/v1/roles \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF" \
  -d '{"name":"TESTER","permissionIds":[]}'
```

### Huquqlar DB'dan o'qilishini isbotlash (ADR #13)

```bash
psql -d <db> -c "DELETE FROM user_roles WHERE user_id=1;"
curl -s -b $J localhost:8081/api/v1/auth/me        # roles: []  — token o'zgarmadi
psql -d <db> -c "INSERT INTO user_roles ... ;"
curl -s -b $J localhost:8081/api/v1/auth/me        # qaytdi
```

### Brute-force

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -X POST localhost:8081/api/v1/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"x@app.co","password":"xato"}'
  psql -At -d <db> -c "SELECT failed_attempts||' '||coalesce(locked_until::text,'-') FROM users WHERE email='x@app.co';"
done
# 5-dan keyin: TO'G'RI parol bilan ham 401 bo'lishi kerak
```

### Xavfsizlik header'lari (har zanjirda)

```bash
for p in /api/v1/users /api/v1/auth/login /swagger-ui/index.html; do
  printf "%-28s %s\n" "$p" "$(curl -si localhost:8081$p | grep -ic 'content-security\|referrer-policy\|permissions-policy')"
done
```

### MDC tozalanishi

```bash
curl -s -o /dev/null -b $J -H 'X-Request-Id: A1' localhost:8081/api/v1/auth/me
curl -s -o /dev/null    -H 'X-Request-Id: B1' localhost:8081/api/v1/users
# log'da: [A1, 1]  keyin  [B1, ]   ← ikkinchisida userId BO'SH bo'lishi SHART
```

### So'rov sonini o'lchash

`application-dev.yml` da `org.hibernate.SQL: DEBUG` yoqilgan holda konsoldagi
`select` qatorlarini sana. Kutilgan: `GET /users` → 3, `/auth/me` → 2,
`/roles/detailed` → 1.

---

## 9. ISHGA TUSHIRISH

```bash
createdb auth_rbac_starter
cp .env.example .env          # DB_USERNAME / DB_PASSWORD ni yozing
./gradlew bootRun             # birinchi marta: RSA kalitlari avtomatik yasaladi
```

### Migratsiyalar

| Joy | Nima | Qachon ishlaydi |
|---|---|---|
| `db/migration/V1–V5` | Sxema + permission/rol seed | Har doim (prod ham) |
| `db/seed/R__dev_users.sql` | Dev foydalanuvchilar | Faqat `dev` profil (`application-dev.yml` → `spring.flyway.locations`) |

- Yangi o'zgarish — **yangi** `V6__...sql`. Qo'llangan migratsiyani tahrirlamang
  (Flyway checksum xatosi beradi).
- Dev seed **repeatable** (`R__`) — versiyalangan bo'lsa, prodga keyin qo'shilgan `V6`
  dev bazada "out of order" bo'lib qolardi. Har INSERT idempotent (`ON CONFLICT`).
- Yangi permission qo'shilsa: `Permissions.java` + yangi `V*` migratsiya — ikkalasi ham.

Dev foydalanuvchilar: `admin@app.co`, `user@app.co`, `manager@app.co`,
`operator@app.co` — paroli **`Parol12345`**.

> 🔴 Prod profili `db/seed` ni o'qimaydi. Lekin prod bazasini dev profilda
> ishga tushirsangiz — seed tushadi. `SPRING_PROFILES_ACTIVE=prod` ni unutmang.

---

## Kod qayerda

To'liq kod — repo'ning o'zida (`src/main/java/uz/app/starter`). Bu faylda faqat
qarorlar va tuzoqlar; kod nusxasi ataylab yo'q — ikki joyda tursa, biri eskiradi.
Qarorlar tarixi va 50 ta ADR: `docs/reference/project-v1-roadmap.md`.
