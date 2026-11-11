# LOGYX Web — guía para agentes

Astro 7 (SSR, `@astrojs/node` standalone) · Tailwind 4 · islas React + primitivas estilo shadcn/ui · lucide · MapLibre GL (OpenFreeMap positron) · Geist Sans/Mono (fontsource).
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
│   ├── operador/           # index (panel), incidencias, solicitudes/[id], envios/[id] (solo rol OPERATOR)
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
- Accesibilidad: todo input con `<Label>`/`Field`, errores con `role="alert"`, foco visible global, enlace "Saltar al contenido", navegación lateral colapsable en tablet/móvil.

## Módulos fase 3
- Paneles del envío (`shipments/components/ShipmentExtras`): pestañas Documentos / Calificación / Incidencias + banner `hasActiveIncident`; cada panel carga sus datos por Astro Actions.
- Polling: chat ~8 s, campana ~30 s, alertas del operador ~30 s; todos pausan con la pestaña oculta. Descarga de documentos solo por `/documentos/<id>` (el token no sale del servidor).
- Enlaces de notificaciones: `lib/notification-links.ts`. Operador: `/operador/*` solo OPERATOR (middleware), `homeFor` → `/operador`.
- e2e: specs por módulo (`negotiation`, `reputation`, `documents`, `incidents`, `notifications`, `operator`, `a11y-*`); helpers `openBidScenario`, `deliveredShipment` (envío entregado vía API), `operatorToken`. Backend de pruebas: `MAVEN_OPTS=-Xmx512m ./mvnw quarkus:dev -Dquarkus.http.port=8180 -Dlogyx.operator.sla=PT0S` (SLA 0 para poder asignar manualmente; con 24 h ese test se salta). Si `astro dev` ya tiene lock, levanta uno con `--ignore-lock` y usa `E2E_BASE_URL`. Usa `--workers=1`.

## Módulos fase 4 (viajes)
- `features/trips/` (server, actions, components): publicar viaje, detalle con reservas, retornos, Smart Load, buscar viajes (PYME), mis reservas. Rutas: `/transportista/{index,viajes,viajes/nuevo,viajes/[id],retornos,smart-load}`, `/pyme/{viajes,reservas}`.
- Componentes nuevos compartidos: `ui/time-select`, `domain/CapacityBar`, `StatusPill kind=trip|booking|smart` (ver `references/web.md`). Notificaciones: `TRIP` y `SMART_LOAD` en `lib/notification-links.ts`.
- El login del transportista sigue yendo a `/transportista/marketplace`; `/transportista` es "Tu actividad".
- e2e: `trips.spec.ts`, `a11y-trips.spec.ts` (helpers `tripScenario`, `publishTrip`, `bookTrip`); las capturas de retornos dependen de los datos demo (carrier@logyx.pe).

## Pendientes conocidos
Modo oscuro, tiempo real (hoy polling), polling de tracking, vista global de solicitudes/envíos del operador (falta endpoint), mapa con ruta por carretera (hoy línea recta entre ciudades). La suite e2e no cubre aún el registro de conductor desde Flota ni el estado de vehículo; capturas en `docs/screenshots/`.
