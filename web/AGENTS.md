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
│   ├── index, login, olvide, restablecer, registro, privacidad, conductor, cuenta, sin-acceso (403), logout(POST)
│   ├── organizacion/       # index (perfil de la empresa), usuarios (solo MANAGE_USERS)
│   ├── pyme/               # index (panel), solicitudes/{index,nueva,[id]}, envios
│   ├── transportista/      # marketplace, ofertas, flota, envios
│   ├── operador/           # index (panel), incidencias, organizaciones (verificación RN01), auditoria, solicitudes/{index,[id]}, envios/{index,[id]} (solo rol OPERATOR)
│   └── notificaciones, incidencias/{index,[id]}, organizaciones/[id], documentos/{[id],subir} (proxy autenticado de archivos)
├── features/<feature>/     # auth, organization, catalog, requests, shipments, marketplace, fleet (compatibility.ts), verification, dashboard, negotiation, reputation, documents, incidents, notifications, operator, trips
│   ├── server.ts           #   llamadas a la API (solo servidor)
│   ├── actions.ts          #   Astro Actions (mutaciones), registradas en src/actions/index.ts
│   └── components/         #   islas React de la feature
├── components/ui/          # biblioteca compartida (ver sección Componentes): button, field, input, password-input, select, combobox, date-picker, number-input, confirm-dialog, reason-dialog, toaster, dialog, sheet, tabs, tooltip, dropdown-menu, pagination, table, filter-bar, timeline, auth-card, card, badge, alert, skeleton, avatar, kbd, form-controls, star-rating, file-dropzone
├── components/domain/      # StatusPill, RouteLine, KpiTile, EmptyState, Countdown, VerifiedBadge, TrustScore, VerificationBanner, VerificationBlock (React; sirven en SSR y en islas)
├── components/layout/      # AppShell (sidebar + topbar por rol), PageHeader, Logo
├── components/dev/         # UiCatalog (página /dev/ui)
├── layouts/                # Base.astro (monta <Overlays/>: Toaster + ConfirmHost), Public.astro
├── lib/api/                # schema.d.ts (GENERADO), client.ts (openapi-fetch + unwrap + ApiError), server.ts (tipos)
├── lib/session.ts          # cookie httpOnly, claims del JWT (+ `permissions`), homeFor(rol)
├── lib/permissions.ts      # permisos por rol (espejo de OrgAccessPolicy), `can(session, p)`, etiquetas de rol, aviso Lector
├── lib/use-can.ts          # `useCan('OPERATE')` / `usePermissions()` para islas React (SSR + cliente)
├── lib/request-context.ts  # solo servidor: AsyncLocalStorage con los permisos de la petición (lo activa el middleware)
├── lib/actions.ts          # requireToken(ctx) y run(fn) → ApiError→ActionError con el mensaje en español de la API (+ `field` y código anexos e invisibles)
├── lib/action-error.ts     # splitActionError(error) → {message, field, code}; toFormErrors(error, campos); RN07_FIELD_MESSAGE
├── lib/limits.ts           # LIMITS: longitudes máximas de textos libres (espejo de los @Size del backend)
├── lib/contact.ts          # SUPPORT_EMAIL (a quién escribir por verificación/suspensión)
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

## Huecos P1 de la revisión de flujos (2026-11-16, agente web-A)
Contrato: `docs/api/CHANGELOG.md` (2026-11-16, V13). Hallazgos F04–F16, F35–F37, F40, F50, F62 de `docs/reviews/2026-11-16-flow-gaps.md`.
- **Errores de la API → formulario (F10/F37).** `run()` (`lib/actions.ts`) anexa al mensaje el `field` y el código `LOGYX-XXX-NNN` **en caracteres de ancho cero** (un `toast.error(error.message)` directo se ve limpio). En la isla: `splitActionError(error)` → `{ message, field, code }`; `toFormErrors(error, ['amount','vehicleId','notes'])` (o un mapa `campoApi → claveLocal`) devuelve `{ errors, formError, failure }`: el campo conocido queda bajo su control (con el texto de RN07 si es `LOGYX-NEG-001`), los `LOGYX-CAP-*` caen siempre en `vehicleId` y el resto en `formError` (un `Alert`). Cubre solicitud, oferta, contra-oferta, chat, viaje, reserva, rechazo de reserva, cancelaciones, reasignaciones, incidencia (descripción y comentario), reseña y razón social. `lib/limits.ts` (`LIMITS`) alinea los `maxLength` y los `z.max` de las actions con los `@Size` del backend (notas de oferta/viaje 300, no 500). Tras tocar `lib/actions.ts`/`lib/action-error.ts` o añadir actions con el `astro dev` ya levantado, haz `touch src/actions/index.ts` (si no, Vite sirve el módulo viejo: "encodeActionMeta is not a function" o 404 en la action nueva).
- **Verificación (RN01, F04/F05).** `AppShell` ya pide `/auth/me`: con `verificationStatus` ≠ `VERIFIED` (PYME/transportista) monta `components/domain/VerificationBanner` (isla; se actualiza con `notification.created` de `relatedType=ORGANIZATION` vía la action `auth.verification`). Copy en `features/verification/copy.ts`. Bloqueos `LOGYX-ORG-003/004` al publicar solicitud, ofertar, reservar, publicar viaje y aceptar oferta: `VerificationBlock` dentro del formulario o `reportFailure(failure)` (diálogo de aviso) en acciones sin formulario; nunca un toast genérico. `VerifiedBadge` = "Verificado por OnCargo" (`rucVerified`; `rucValid` no da sello). Operador: `/operador/organizaciones` (`OrganizationsInbox`: KPIs, `QuickFilters` por estado, filtro de tipo, tabla `stack`, `Sheet` de detalle con `DescriptionList`, verificar/rechazar/suspender con `ReasonDialog`; filtros en la URL `estado`, `tipo`, `pagina`; atajo en el panel del operador). Notificación `ORGANIZATION_VERIFICATION` y los avisos nuevos (`AUCTION_CLOSED`, `REQUEST_CANCELLED`, `SHIPMENT_CANCELLED`, `BID_WITHDRAWN`) en `lib/notification-links.ts` (el operador enlaza a `/operador/*`).
- **`ReasonDialog`** (`components/ui/reason-dialog.tsx`): diálogo con motivo (obligatorio u opcional, contador, error de campo `reason`, RN07, `Alert` para el resto, `hideReason`, `children` para campos extra, `canSubmit`, `onFailure`). Sustituye a `confirm()` cuando hace falta texto: cancelar solicitud/envío, rechazar reserva, cambiar vehículo, relevo de conductor, decisiones del operador.
- **Compatibilidad vehículo ↔ carga (RN02, F09/F35).** Un único helper `features/fleet/compatibility.ts` (`vehicleFit`, `partitionFleet`, `fleetCanCarry`, `fleetBlocker`, `requestNeed`) con la matriz que llega en `RequestView.compatibleVehicleTypes` + kg + m³. Lo usan la hoja de oferta (`BidSheet`: vehículos no aptos deshabilitados con el motivo visible en `UnfitList`, estado vacío con enlace a Flota), el marketplace (etiqueta "Compatible"/"Tu flota no lleva carga X", con "Mi vehículo" las compatibles van primero) y el cambio de vehículo de un envío. La API sigue siendo la fuente de verdad (`CAP-001..005` en `vehicleId`).
- **Negociación (RF26/RF28, F08, F40, F62).** `NegotiationSheet` (transportista): contraofertar, aceptar/rechazar la de la PYME (`proposedByCarrier=false`), ver la propia sin botones, historial (incluye `Vencida`), **Retirar oferta** (`marketplace.withdrawBid`, `BID-005`); `BidComparison` (PYME): acepta/rechaza la del transportista, "Responder con otro monto" (reemplaza la pendiente), ofertas `WITHDRAWN` al final y fuera del "mejor precio". `StatusPill` suma `WITHDRAWN` (Retirada) y `kind="verification"`.
- **Envío (F06/F36).** `shipments/components/ShipmentActions` (montado por `ShipmentExtras` y en `/operador/envios/[id]`): cancelar con motivo (PYME/transportista en `PENDING`, operador también en ruta; `SHP-003`, `TRK-005` deshabilitado con explicación), transportista: cambiar vehículo (`PUT /shipments/{id}/vehicle`, motivo obligatorio en ruta) y asignar/relevar conductor (`SHP-004` inline). `ShipmentFacts` muestra recojo, "Sin conductor", motivo/fecha/autor de la cancelación; `ShipmentListNote` (una línea en `TrackingSplit`) lo resume en la lista. **Toqué `TrackingSplit.tsx` solo de forma mínima** (import + una línea de `ShipmentListNote` + se quitó el uso de `AssignDriver`, que ahora vive en `ShipmentActions`; la función quedó sin uso).
- **Solicitud PYME (F07/F16/RF18).** `CancelRequestDialog` (motivo opcional, `cancelReason`), `RescheduleDialog` (`reopen` con `auction.closeReason=TIMEOUT` y `republish` de una cancelada → navega a la nueva con "es una republicación de…"), en el detalle y en el menú de la lista; Mis ofertas muestra "La PYME canceló la solicitud" + motivo si la API devuelve la solicitud cancelada.
- **Smart Load (F12).** `AcceptSmartLoadDialog`: "Ofertar este plan" con un `MoneyInput` por carga (piso RN03 = 75 % del sugerido visible), nota, todo o nada (`TRP-011`/`TRP-012`), y al terminar lista las ofertas creadas (`SmartLoadView.bids`) con enlace a Mis ofertas.
- e2e: `verification`, `negotiation-p1`, `shipment-actions`, `request-recovery`, `rn07`, `a11y-p1` (claro y oscuro). Helpers nuevos: `orgIdOf`, `suspendOrg`/`verifyOrg`/`rejectOrg`, `orgInboxPage`, `counterOffer`, `withdrawBid`, `notificationsOf` (api) y `expectNoSeriousViolations(page, selector?)`, `fillRequestForm` (ui). Limitación: la subasta dura 72 h en dev, así que "Reabrir subasta" solo se prueba simulando el estado vivo (evento de ventana) y el error `AUC-003`; el éxito lo cubren los tests de backend.

## Cuentas, roles, perfil y lecturas (V14/V15, 2026-11-17, agente web-B)
Contrato: `docs/api/CHANGELOG.md` (V14 y V15) y ADR-0009.
- **Esconder lo que el rol no puede hacer.** `Session.permissions` sale de los claims `role` + `org_type` del JWT (`lib/permissions.ts` espeja `OrgAccessPolicy.permissions`; `roles.spec.ts` lo comprueba contra la API, así no hay una llamada a `/auth/me` por página). En `.astro`: `can(Astro.locals.session, 'OPERATE')`. En islas React: `useCan('OPERATE')` (`lib/use-can.ts`), que funciona en el SSR de la isla (el middleware guarda los permisos en un `AsyncLocalStorage`, `lib/request-context.ts`) y en el cliente (`<html data-perms>`, escrito por `Base.astro`), vía `useSyncExternalStore` sin avisos de hidratación. `components/domain/ReadOnlyNotice` muestra el aviso discreto "Tu rol Lector solo permite consultar…" (no renderiza nada si puede operar). Aplicado en: solicitudes (lista, detalle, nueva), ofertas (`BidComparison`), negociación (`NegotiationSheet`, `ChatPanel` en modo lectura), marketplace, retornos, Smart Load, viajes (lista, nuevo, detalle, reservas, buscar), Flota, envíos (`ShipmentActions`), documentos, calificación, incidencias; la entrada "Nueva solicitud" del menú (`needs: 'OPERATE'` en `AppShell`). `MANAGE_USERS`: entrada y página "Usuarios y roles"; `MANAGE_ORGANIZATION`: edición del perfil; `MANAGE_DRIVERS`: alta, desactivar/reactivar y restablecer contraseña de conductores. Es solo presentación: el backend responde `AUTH-007` (Lector) / `AUTH-008` (solo ADMIN) con el mensaje en español, y las actions lo muestran tal cual. Tras agregar un componente con una acción de escritura, ocúltala con `useCan` y cubre el caso en `roles.spec.ts`.
- **403 amable (F58).** `middleware.ts` ya no redirige en silencio a quien entra a la sección de otro tipo de cuenta (`/pyme`, `/transportista`, `/operador`, `/organizacion`): reescribe a `pages/sin-acceso.astro` (status 403, mismo URL). Las páginas con permiso propio hacen `return Astro.rewrite('/sin-acceso')` (`/organizacion/usuarios`). El conductor sigue redirigido a `/conductor` (su único sitio, además de `/cuenta`).
- **Contraseñas (F02).** `features/auth/password.ts` (llamadas), `password-actions.ts` (actions `password.forgot|inspect|reset|change|logoutAll`), componentes `ForgotPasswordForm`, `ResetPasswordForm`, `ChangePasswordForm`, `LogoutAllButton`, `PasswordRequirements`, `AccountView.astro`. `/olvide` (confirmación siempre igual) y `/restablecer` están en `PUBLIC` del middleware y se ven también con sesión. `/restablecer` valida el token en el servidor (`inspect`, 400 `AUTH-005` → "Este enlace ya no sirve" + "Pedir otro enlace"), responde `Cache-Control: no-store` y `Referrer-Policy: no-referrer`, y la isla quita `?token=` de la URL (`history.replaceState`). INVITE exige aceptar la política de privacidad; éxito → `/login?restablecida=1|activada=1` (mensaje en `LoginForm`). `change-password` reemplaza la cookie `logyx_session` con el token nuevo; las demás sesiones reciben 401 (`sv`) y el middleware las manda a `/login?expired=1` ("Tu sesión se cerró o venció"). `/cuenta` sirve a todos los roles; el conductor la ve en el layout público (el AppShell no sirve: su campana da 403).
- **Usuarios y perfil (B2/B3).** `features/organization/` (`server`, `actions`, `UsersManager`, `RoleChoice`, `OrganizationForm`). Estado de usuario con `StatusPill kind="account"` (Activo, Invitación pendiente, Desactivado). Cambiar el propio rol cierra la sesión (la isla lo avisa y va al login). Perfil: razón social y RUC solo editables con `legalDataEditable`; desde `REJECTED` se avisa que vuelve a revisión.
- **Operador.** `/operador/auditoria` (`AuditViewer`; filtros en la URL `accion` (coma), `entidad`, `actor`, `organizacion`, `entidadId`, `desde`, `hasta`, `pagina`; etiquetas en `features/operator/audit-labels.ts`: lo que no esté se muestra con su código). Ofertas en el detalle de la solicitud (`OperatorBids`, solo lectura). GMV en `MetricsGrid` (la tarjeta "Adjudicadas" duplicada de F59 ya no existía: queda como contexto de "Solicitudes abiertas").
- **Mis ofertas.** `features/marketplace/bid-state.ts`: `bidState(solicitud)` → Pendiente / Aceptada / **Perdida** / **Cancelada** / Retirada, y `standingText(myBidStanding)` ("Vas 2.º de 5", "A menos de 5 % de la mejor"; la API nunca entrega montos ajenos, RN04). El cierre de la subasta (`closeReason`, `closedAt`) sale bajo el estado. También en la fila "Ofertada" del marketplace.
- **Conductor.** `/conductor` suma "Viajes anteriores" (`GET /driver/shipments?scope=HISTORY`) y el acceso a `/cuenta`.
- e2e: `account`, `roles`, `organization`, `operator-audit`, `bid-standing` (todas con axe en claro y oscuro). Helpers nuevos en `helpers/api.ts`: `mailbox`/`resetLinkFor` (buzón dev, el más reciente primero), `inviteUser`, `userWithRole` (crea y activa un usuario OPS/VIEWER/ADMIN por API), `raw` (códigos de error), `orgUsers`. Las cuentas seed `pyme.ops@logyx.pe` y `pyme.viewer@logyx.pe` solo se usan para leer (login), nunca se les cambia la contraseña.

## Pendientes conocidos
Mapa con ruta por carretera (hoy línea recta entre ciudades). La suite e2e no cubre aún el registro de conductor desde Flota ni el estado de vehículo; capturas en `docs/screenshots/`.
