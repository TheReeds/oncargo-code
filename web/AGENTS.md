# LOGYX Web — guía para agentes

Astro 7 (SSR, `@astrojs/node` standalone) · Tailwind 4 · islas React + primitivas estilo shadcn/ui · lucide · MapLibre GL (OpenFreeMap positron/dark) · Geist Sans/Mono (fontsource).
Contexto general: `../AGENTS.md`. Sistema de diseño: `../docs/design/DESIGN.md`. Contrato: `../docs/api/openapi.yaml`.

> **ANTES de crear o modificar UI lee la skill `../.claude/skills/logyx-design-system/SKILL.md`** (+ `references/web.md`: catálogo de componentes, ejemplos, do/don't, trampas). Copy: `../docs/design/CONTENT.md`. Catálogo visual vivo: http://localhost:4321/dev/ui (solo dev, 404 en prod).

## Cómo correr
```bash
cd web && cp .env.example .env     # API_URL=http://localhost:8080
npm install                        # postinstall copia el worker de MapLibre a public/maplibre
npm run dev                        # http://localhost:4321
npm run check && npm run build     # obligatorio antes de dar algo por terminado
npm start                          # producción (node dist/server/entry.mjs); en local sin HTTPS usa INSECURE_COOKIES=1
npm run test:e2e                   # suite Playwright (ver "Tests e2e")
```
Cuentas demo: ver `../AGENTS.md`. Solo el servidor de Astro habla con la API (`API_URL`); el JWT vive en la cookie httpOnly `logyx_session`.

## Estructura
```
src/
├── pages/                  # Rutas finas: leen sesión, llaman a features/*/server.ts, renderizan
│   ├── index, login, registro, privacidad, conductor, logout(POST)
│   ├── pyme/               # index (panel), solicitudes/{index,nueva,[id]}, envios
│   ├── transportista/      # marketplace, ofertas, flota, envios
│   ├── operador/           # index (panel), incidencias, solicitudes/{index,[id]}, envios/{index,[id]} (solo rol OPERATOR)
│   └── notificaciones, incidencias/{index,[id]}, organizaciones/[id], documentos/{[id],subir} (proxy autenticado de archivos)
├── features/<feature>/     # auth, catalog, requests, shipments, marketplace, fleet, dashboard, negotiation, reputation, documents, incidents, notifications, operator
│   ├── server.ts           #   llamadas a la API (solo servidor)
│   ├── actions.ts          #   Astro Actions (mutaciones), registradas en src/actions/index.ts
│   └── components/         #   islas React de la feature
├── components/ui/          # biblioteca compartida (ver sección Componentes): button, field, input, password-input, select, combobox, date-picker, number-input, confirm-dialog, toaster, dialog, sheet, tabs, tooltip, dropdown-menu, pagination, table, filter-bar, timeline, auth-card, card, badge, alert, skeleton, avatar, kbd, form-controls, star-rating, file-dropzone
├── components/domain/      # StatusPill, RouteLine, KpiTile, EmptyState, Countdown, VerifiedBadge, TrustScore (React; sirven en SSR y en islas)
├── components/layout/      # AppShell (sidebar + topbar por rol), PageHeader, Logo
├── components/dev/         # UiCatalog (página /dev/ui)
├── layouts/                # Base.astro (monta <Overlays/>: Toaster + ConfirmHost), Public.astro
├── lib/api/                # schema.d.ts (GENERADO), client.ts (openapi-fetch + unwrap + ApiError), server.ts (tipos)
├── lib/session.ts          # cookie httpOnly, claims del JWT, homeFor(rol)
├── lib/actions.ts          # requireToken(ctx) y run(fn) → ApiError→ActionError con el mensaje en español de la API
├── lib/format.ts           # S/ 1,850.00 · 28 nov · 08:00 · etiquetas de enums
├── middleware.ts           # protege rutas, redirige por rol (PYME / CARRIER / DRIVER→/conductor), 401→/login?expired=1
└── styles/global.css       # tokens de DESIGN.md en @theme de Tailwind
```

## Cliente API
`npm run gen:api` regenera `src/lib/api/schema.d.ts` desde `../docs/api/openapi.yaml`. Úsalo así:
```ts
const data = await unwrap(apiFor(token).GET('/api/v1/vehicles'));   // desenvuelve {data}; lanza ApiError(status, code, message, field)
```
Si el backend cambia el contrato: `npm run gen:api` y corrige lo que rompa `npm run check`.

## Agregar una pantalla / feature (paso a paso)
1. `features/<f>/server.ts`: funciones que llaman a la API con `unwrap(apiFor(token)...)`.
2. Si hay mutaciones: `features/<f>/actions.ts` con `defineAction({ accept: 'json', input: z..., handler: (i, ctx) => run(() => fn(requireToken(ctx), i)) })` y regístralo en `src/actions/index.ts`.
3. Componentes interactivos en `features/<f>/components/*.tsx` (islas). Desde el cliente: `const { data, error } = await actions.<f>.<accion>(input)`; muestra `error.message` (ya viene en español).
4. Página en `src/pages/...astro`: obtén `Astro.locals.session!.token`, carga datos en el frontmatter, envuelve en `<AppShell title active>` + `<PageHeader>`, monta la isla con `client:load` (o `client:only="react"` si usa APIs del navegador, p. ej. mapas).
5. Agrega el enlace en `AppShell.astro` (objeto `NAV`) y, si es una ruta nueva de rol, revisa `middleware.ts`.
6. Estados vacío / carga / error en español (`EmptyState`, `Alert`). `npm run check && npm run build`.

Trampas conocidas:
- En `.astro`, `<Componente />` pasado como **prop** a un componente React NO es un elemento React (rompe el SSR con "htmlParts"). Pasa la referencia del componente (`icon={FileText}`) o usa slots/children.
- Componentes React usados en `.astro` reciben `className` (no `class`).
- Datos que cruzan a una isla deben ser serializables (sin funciones ni Date).
- MapLibre 6 necesita su worker servido por nosotros (`scripts/copy-maplibre-worker.mjs` → `public/maplibre/`, ignorado por git; `setWorkerUrl` en `TrackingMap.tsx`).
- Los formularios React llevan `method="post"` para que, si se envían antes de hidratar, las credenciales no acaben en la URL.

## Componentes compartidos (`components/ui`)
Estilo shadcn/ui con código propio sobre primitivas MIT: Radix UI (select, popover, dialog, alert-dialog, dropdown-menu, tabs, tooltip, checkbox, radio-group, switch, label), **cmdk** (Combobox/MultiSelect), **react-day-picker 10** + date-fns (DatePicker/DateRangePicker, locale es), **sonner** (toasts). Sin SweetAlert.
- **Regla:** no uses `<select>`, `<input type=date>`, `window.confirm/alert`. Usa `Select`/`Combobox`, `DatePicker`/`DateRangePicker`, `confirm({...})` (Promise<boolean>), `toast.success/error/info` y `flash()` (toast tras redirigir/recargar).
- Formularios: `Field` (label + hint + error `role="alert"`) con `id={htmlFor}` en el control; `MoneyInput` (`S/`), `NumberInput` (`suffix="kg"`).
- Listados: `Table` + `Pagination` (`meta.page/size/total`), `SkeletonRows`, `EmptyState`.
- Si falta un componente, créalo en `components/ui`, agrégalo a `components/dev/UiCatalog.tsx` y a `.claude/skills/logyx-design-system/references/web.md`; nunca una copia local en una feature.
- Tras instalar dependencias nuevas reinicia `npm run dev` (Vite: 504 "Outdated Optimize Dep").

## Tests e2e (Playwright)
`web/tests/e2e/` (`auth`, `flows`, `a11y` + `helpers/api.ts` y `helpers/ui.ts`), config en `web/playwright.config.ts`. Cada test crea sus propias cuentas (RUC válido aleatorio, correos únicos) vía API, así que es repetible; solo lee las cuentas demo para login. Las pantallas se prueban por UI; los datos de arranque (vehículos, solicitudes, ofertas) se crean por API.
```bash
# 1) Backend en perfil dev (datos demo), p. ej. en otro puerto si el 8080 está ocupado:
cd backend && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./mvnw quarkus:dev -Dquarkus.http.port=8180
# 2) Suite (levanta su propio `astro dev` en :4322 si no defines E2E_BASE_URL):
cd web && E2E_API_URL=http://localhost:8180 npm run test:e2e
```
Variables: `E2E_API_URL` (API para preparar datos, default `http://localhost:8180`), `E2E_BASE_URL` (web ya levantada; entonces no se arranca servidor — esa web debe tener el mismo `API_URL`), `E2E_SERVER=prod` (build + `node dist/server/entry.mjs`). Primera vez: `npx playwright install chromium`.
Reglas: espera la hidratación con `goto()`/`hydrated()` de `helpers/ui.ts` (islas `astro-island[ssr]`) antes de interactuar; localiza por rol/label (los botones de fecha/combobox se nombran por su `<Label>`); un control con `aria-label` que contenga "Contraseña" choca con `getByLabel('Contraseña')`, usa `{ exact: true }`. `a11y.spec.ts` falla con violaciones axe `serious`/`critical` (WCAG 2.1 AA): úsalo al agregar pantallas.

## Convenciones de UI (ver DESIGN.md)
- Tokens (Tailwind): `ink-900/700/500/400`, `steel-100/200`, `surface`, `petrol-700/800/50`, `emerald-500/700/50`, `amber-500/700/50`, `red-600/50`. `ink-500` (`#5b6b7f` en web, ≥4.5:1 sobre blanco, steel-100 y petrol-50) y `emerald-700`/`amber-700` son variantes con contraste AA para texto (los hex de DESIGN.md no llegan a AA en texto pequeño).
- Dinero y oportunidad = emerald (`.money`: mono, tabulares, emerald-700). Cifras técnicas (placas, km, toneladas, montos en tabla) en `font-mono`.
- Radio: tarjetas `rounded-card` (10px), inputs `rounded-input` (8px), chips `rounded-full`.
- Siempre `StatusPill` para estados, `RouteLine` para rutas, `KpiTile` (máx. 4 por fila), `EmptyState` con camión.
- Formatos: `formatMoney` (`S/ 1,850.00`), `formatDateTime` (`28 nov · 08:00`, zona America/Lima), `formatDate`, `formatTons`, `formatKm`.
- Modo oscuro: `html[data-theme=dark]` redefine los tokens (ver DESIGN.md). Sin hex/`bg-white`/`text-white` sobre rellenos (usa `text-on-solid`; `bg-chrome` para fondos oscuros fijos; `bg-scrim`). Toggle `ui/theme-toggle` (topbar y Public), preferencia en `localStorage logyx-theme`, script anti-parpadeo en `Base.astro`, `lib/theme.ts` (`useResolvedTheme`). Mapas: estilo `dark` de OpenFreeMap. Spec `a11y-dark.spec.ts`.
- Accesibilidad: todo input con `<Label>`/`Field`, errores con `role="alert"`, foco visible global, enlace "Saltar al contenido", navegación lateral colapsable en tablet/móvil.

## Módulos fase 3
- Paneles del envío (`shipments/components/ShipmentExtras`): pestañas Documentos / Calificación / Incidencias + banner `hasActiveIncident`; cada panel carga sus datos por Astro Actions.
- Tiempo real: ver la sección "Tiempo real" (reemplaza al polling de chat, campana y operador). Descarga de documentos solo por `/documentos/<id>` (el token no sale del servidor).
- Enlaces de notificaciones: `lib/notification-links.ts`. Operador: `/operador/*` solo OPERATOR (middleware), `homeFor` → `/operador`.
- e2e: specs por módulo (`negotiation`, `reputation`, `documents`, `incidents`, `notifications`, `operator`, `a11y-*`); helpers `openBidScenario`, `deliveredShipment` (envío entregado vía API), `operatorToken`. Backend de pruebas: `MAVEN_OPTS=-Xmx512m ./mvnw quarkus:dev -Dquarkus.http.port=8180 -Dlogyx.operator.sla=PT0S` (SLA 0 para poder asignar manualmente; con 24 h ese test se salta). Usa `--workers=1`. El `webServer` de Playwright arranca `astro dev --config astro.e2e.config.mjs --ignore-lock` (misma config sin la barra de herramientas de dev de Astro, para que no salga en `docs/screenshots/`, y sin chocar con el `astro dev` que ya tengas en :4321; con `VITE_CACHE_DIR=<dir>` usa otra caché de Vite). Las capturas de `docs/screenshots/` se regeneran al correr la suite; la evidencia de entrega usa `tests/e2e/fixtures/evidencia.png` (`EVIDENCE_PHOTO`, no un PNG de 1x1).

## Listados globales del operador
- `/operador/solicitudes` y `/operador/envios` (`features/operator/components/OperatorListing.tsx`, una isla con `kind`): tabla paginada en el servidor (`GET /api/v1/operator/requests|shipments`, `listRequests`/`listShipments` en `features/operator/server.ts`), filtros en la URL (`estado`, `origen`, `destino`, `desde`, `hasta`, `pagina`; 20 por página) y enlace a los detalles `/operador/{solicitudes,envios}/[id]`. Entradas "Solicitudes" y "Envíos" en la navegación del operador (`AppShell`, ids `operador-solicitudes|envios`).
- e2e: `operator-listings.spec.ts` (tabla, filtros, detalle, PYME/transportista redirigidos, axe).

## Módulos fase 4 (viajes)
- `features/trips/` (server, actions, components): publicar viaje, detalle con reservas, retornos, Smart Load, buscar viajes (PYME), mis reservas. Rutas: `/transportista/{index,viajes,viajes/nuevo,viajes/[id],retornos,smart-load}`, `/pyme/{viajes,reservas}`.
- Componentes nuevos compartidos: `ui/time-select`, `domain/CapacityBar`, `StatusPill kind=trip|booking|smart` (ver `references/web.md`). Notificaciones: `TRIP` y `SMART_LOAD` en `lib/notification-links.ts`.
- El login del transportista sigue yendo a `/transportista/marketplace`; `/transportista` es "Tu actividad".
- e2e: `trips.spec.ts`, `a11y-trips.spec.ts` (helpers `tripScenario`, `publishTrip`, `bookTrip`); las capturas de retornos dependen de los datos demo (carrier@logyx.pe).

## Tiempo real (SSE, ADR-0007)
- `GET /eventos` (`pages/eventos.ts`): proxy autenticado (cookie `logyx_session` → Bearer) hacia `/api/v1/events/stream`; el JWT no sale del servidor. `middleware.ts` lo deja pasar a todos los roles (incluido conductor). Mismo origen: la CSP (`connect-src 'self'`) no cambia.
- `lib/realtime.ts`: una sola `EventSource` por pestaña (singleton), reconexión con espera y `Last-Event-ID`, estado `connecting|live|reconnecting|offline` (también en `html[data-realtime]`). API: `subscribe`, `onStatus`, `onResync`, hooks `useRealtime(types, fn)`, `useRealtimeStatus()` y **`useLiveRefresh({ types, match, refresh, fallbackMs, debounceMs })`**: refresca con el evento (agrupado), al (re)conectar y con un polling de respaldo lento (60 s; 30 s en el chat) **solo cuando no está "live"**; pausado con la pestaña oculta.
- `components/layout/LiveIndicator` en el topbar de `AppShell` ("En vivo" / "Reconectando…"). Páginas solo-SSR: `ui/live-region` + `lib/live-region.ts` (reemplazo suave de `[data-live-region]`).
- Los eventos son avisos con payload mínimo: la isla vuelve a pedir los datos por Astro Action (`requests.detail`, `shipments.list`, `incidents.get`, `trips.myBookings|smartLoads|detail`, `notifications.*`, `negotiation.*`).
| Pantalla | Eventos |
|---|---|
| Campana (`NotificationBell`) | `notification.created` (usa `unreadCount`; recarga la lista si está abierta). `/notificaciones`: igual |
| Chat / contra-ofertas (`ChatPanel`, `NegotiationPanel`, `useCounterOffers`) | `message.posted` (mismo `requestId` + `carrierOrgId`), `counter_offer.updated`, `bid.placed`, `request.matched`; `NEGOTIATION_EVENT` tras acción propia |
| Detalle de solicitud PYME (`RequestLive`, `BidComparison`, `AuctionCard`) | `bid.placed`, `counter_offer.updated`, `request.matched` (toast), `auction.updated` del `requestId` |
| Marketplace | `auction.updated` (conteo de ofertas y cierre en sitio) |
| Mis ofertas (`LiveRegion`) | `bid.accepted`, `bid.rejected`, `bid.placed`, `counter_offer.updated` |
| Envíos (`TrackingSplit`), `ShipmentExtras > IncidentsPanel`, `/conductor` (`LiveRegion`) | `shipment.status_changed|stop_updated|driver_assigned`, `incident.reported|updated` |
| Incidencias (`IncidentsList`, `IncidentDetail`) y panel del operador | `incident.reported|updated` (el operador además refresca cada 60 s: el SLA depende del reloj) |
| Viajes (`MyBookings`, `TripDetail`, `/transportista/viajes`) y Smart Load | `trip_booking.updated`, `trip.cancelled`; `smart_load.suggested|accepted` |
| Buscar viajes (`/pyme/viajes`, `LiveRegion` `viajes`) | `trip_booking.updated`, `trip.cancelled` |
| Cargas de retorno (`/transportista/retornos`, `LiveRegion` `retornos`) | `shipment.status_changed`, `auction.updated`, `trip_booking.updated`, `trip.cancelled` |
- Agregar un evento a una pantalla: `useLiveRefresh` con `types` + `match`; si no hay action de lectura, créala en `features/<f>/actions.ts`. Los `setInterval` solo se aceptan para relojes (`Countdown`) o respaldo vía `useLiveRefresh`.
- e2e: `realtime.spec.ts` (indicador, oferta, chat y campana en vivo, sin recargar y sin depender del respaldo).

## Producción: contenedor, entorno y seguridad
- `web/Dockerfile` (multi-stage, node:22-alpine, usuario `node`, puerto 4321, healthcheck `GET /login`): `docker build -t logyx-web web/`. Runtime = `dist/` + `node_modules` de producción.
- Variables de runtime: `API_URL` (en `lib/api/client.ts` `process.env.API_URL` tiene prioridad sobre `import.meta.env`, así no se congela en el build), `HOST`/`PORT`, `COOKIE_SECURE` (`true|false`; por defecto `true` en prod; usa `false` si se sirve por http, p. ej. compose local; `INSECURE_COOKIES=1` sigue funcionando), `HSTS=true` (fuerza `Strict-Transport-Security` aunque la petición no llegue por https; si no, se envía solo en prod y con https / `X-Forwarded-Proto: https`).
- Cabeceras (`middleware.ts`, respuestas HTML y redirecciones): `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy` (cámara/micrófono/geolocalización desactivadas: la web no las usa) y CSP **solo en producción**. CSP por cabecera, no `security.csp` de Astro (sus hashes anulan `'unsafe-inline'` y rompen los estilos inline de Radix). `script-src 'self' 'unsafe-inline'` es necesario por los scripts inline de hidratación de Astro (islas); `style-src 'unsafe-inline'` por Radix/Tailwind; MapLibre: `worker-src blob:`, `img-src/connect-src https://tiles.openfreemap.org`. Si agregas otro origen externo, añádelo a `CSP`.
- Sesión: cookie `logyx_session` httpOnly, SameSite=Lax, `secure` según `COOKIE_SECURE`.
- `X-Forwarded-For`: login y registro (`features/auth`) reenvían a la API la IP del navegador (`ctx.clientAddress`) para el rate limiting por cliente. Detrás de un proxy, Astro solo usa el `X-Forwarded-For` entrante si el host está validado (`security.allowedDomains`); de lo contrario `clientAddress` es la IP del proxy.

## Pendientes conocidos
Mapa con ruta por carretera (hoy línea recta entre ciudades). La suite e2e no cubre aún el registro de conductor desde Flota ni el estado de vehículo; capturas en `docs/screenshots/`.
