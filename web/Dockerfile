# syntax=docker/dockerfile:1
# LOGYX Web (Astro SSR, adaptador node standalone). Build: docker build -t logyx-web web/
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# postinstall copia el worker de MapLibre a public/maplibre
COPY scripts ./scripts
RUN npm ci

FROM deps AS build
COPY . .
RUN npm run build

FROM node:22-alpine AS prod-deps
WORKDIR /app
COPY package.json package-lock.json ./
COPY scripts ./scripts
# El postinstall necesita maplibre-gl (dependencia de producción); el build ya generó public/maplibre dentro de dist/client
RUN npm ci --omit=dev

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=4321
COPY --from=prod-deps --chown=node:node /app/node_modules ./node_modules
COPY --from=build --chown=node:node /app/dist ./dist
COPY --chown=node:node package.json ./
USER node
EXPOSE 4321
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://127.0.0.1:4321/login >/dev/null || exit 1
CMD ["node", "dist/server/entry.mjs"]
