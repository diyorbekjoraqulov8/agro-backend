# nginx + admin panel (Vue build). Kontekst — repo ildizi (deploy/compose.prod.yaml: context: ..)
FROM node:24-alpine AS admin
WORKDIR /admin
COPY admin/package.json admin/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY admin ./
RUN npm run build

FROM nginx:1.27-alpine
# envsubst: ${DOMAIN} — /etc/nginx/templates/*.template → conf.d (nginx image o'zi qiladi)
COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/nginx/proxy.inc /etc/nginx/conf.d/proxy.inc
COPY --from=admin /admin/dist /usr/share/nginx/admin
