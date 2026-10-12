# Agro — admin panel

Vue 3 + TypeScript + Vite + Tailwind 4 + shadcn-vue (reka-ui).

```bash
cd admin
npm install
npm run dev          # http://localhost:5173  (backend: ./gradlew bootRun → :8081)
npm run build        # vue-tsc + vite build → dist/
```

- **Bitta origin.** Dev'da Vite `/api` ni `localhost:8081` ga proxy qiladi (`vite.config.ts`);
  prod'da nginx xuddi shunday. CORS sozlanmaydi, cookie `SameSite=Strict` (ADR #25).
- **Auth** — HttpOnly cookie (JS token'ni ko'rmaydi). CSRF: `XSRF-TOKEN` cookie → `X-XSRF-TOKEN`
  header (`src/api/http.ts`). 401 → `/auth/refresh` BIR marta (parallel so'rovlar bitta refresh'ni
  kutadi — aks holda rotatsiya "o'g'irlik" deb barcha sessiyani bekor qiladi).
- **Huquqlar** (`/auth/me` → `permissions`) faqat UI'ni yashiradi; haqiqiy tekshiruv backend'da.
- Dev login: `admin@app.co` / `Parol12345` (backend `db/seed`).
- `localhost` cookie'lari port bo'yicha ajratilmaydi — Swagger (:8081) va panel (:5173) bitta sessiya.

| Papka | |
|---|---|
| `src/api/` | `http.ts` (fetch, CSRF, refresh), `types.ts` (backend DTO), `index.ts` (endpoint'lar) |
| `src/views/` | sahifalar (router: `src/router/index.ts`, `meta.perm` — kerakli huquq) |
| `src/components/app/` | layout, sahifa sarlavhasi, paginator, status belgisi, xarita (Leaflet) |
| `src/components/ui/` | shadcn-vue (CLI generatsiya qiladi — `npx shadcn-vue@latest add <nom>`) |
