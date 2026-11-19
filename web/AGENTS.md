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
│   ├── index, login, olvide, restablecer, registro, privacidad, terminos, precios, contacto, conductor, cuenta, sin-acceso (403), logout(POST)
│   ├── organizacion/       # index (perfil de la empresa), usuarios (solo MANAGE_USERS)
│   ├── pyme/               # index (panel), solicitudes/{index,nueva,[id]}, envios
│   ├── transportista/      # marketplace, ofertas, flota, envios
│   ├── operador/           # index (panel), incidencias, organizaciones (verificación RN01), auditoria, parametros, solicitudes/{index,[id]}, envios/{index,[id]} (solo rol OPERATOR)
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
├── lib/permissions/        # FUENTE ÚNICA de permisos: index.ts (puro: `can`, etiquetas, `WRITE_ROUTES`, `openRequestAccess`) y react.tsx (`<Can>`, `useCan`, `useRole`)
├── lib/use-can.ts          # compatibilidad: re-exporta `useCan`/`usePermissions`/`useRole` de lib/permissions/react
├── lib/request-context.ts  # solo servidor: AsyncLocalStorage con permisos y rol de la petición (`runWithSession`, lo activa el middleware)
├── lib/actions.ts          # requireToken(ctx) y run(fn) → ApiError→ActionError con el mensaje en español de la API (+ `field` y código anexos e invisibles)
├── lib/action-error.ts     # splitActionError(error) → {message, field, code}; toFormErrors(error, campos); RN07_FIELD_MESSAGE
├── lib/limits.ts           # LIMITS: longitudes máximas de textos libres (espejo de los @Size del backend)
├── lib/contact.ts          # SUPPORT_EMAIL (soporte@oncargo.tech), PRIVACY_EMAIL, SUPPORT_HOURS, WHATSAPP_NUMBER/whatsappUrl() (solo con PUBLIC_WHATSAPP_NUMBER), notifyCityHref()
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

## Solicitudes de la PYME: duplicar, direcciones, búsqueda y CSV (V17, Ola 1 zona A)
Contrato: `docs/api/CHANGELOG.md` (2026-11-17, V17 zona A).
- **Lista en el servidor.** `/pyme/solicitudes` (`RequestsTable`, una isla): filtros, orden y paginación (20) en el servidor (`GET /shipment-requests/mine`), con la URL como estado (`estado` = `PUBLISHED|MATCHED|CANCELLED|REVIEW`, `origen`, `destino`, `desde`, `hasta`, `q`, `orden`, `pagina`); `features/requests/filters.ts` traduce URL → API (lo comparten la página y el proxy CSV). Contadores de las pestañas: `GET /mine/counts`. Mismo patrón que `OperatorListing` (navegar con `window.location`).
- **Exportar CSV.** `components/ui/export-button` (`<ExportButton href="/exportar/solicitudes?…" />`) baja el archivo y, si la API lo rechaza (más de 10 000 filas), lo explica en un aviso. `pages/exportar/[recurso].ts` es el proxy autenticado (cookie → Bearer): para exportar otro recurso agrega su entrada a `RESOURCES` (path de la API + traducción de la URL).
- **Duplicar.** Menú de fila y botón del detalle → `/pyme/solicitudes/nueva?from=<id>`: `nueva.astro` carga la solicitud, precarga todo menos la fecha (`features/requests/dates.ts#nextWeekSameDay`: mismo día de la semana, la semana siguiente, nunca pasada) y `RequestForm` recibe `initial` + `duplicatedFrom`; la cotización se recalcula sola al abrir. Falta el botón en `TrackingSplit` (es de `shipments`).
- **Doble publicación.** `RequestForm` genera una `Idempotency-Key` (`crypto.randomUUID`) por formulario y la manda a `requests.create`; antes de publicar pregunta `requests.similar` y, si hay una igual de las últimas 24 h, muestra «Ya publicaste una igual hoy» con «Publicar de todos modos».
- **Libreta.** `features/places/` (`server`, `actions`, `PlacesManager`) y `/pyme/direcciones` (entrada «Mis direcciones» en `AppShell`). En el formulario, cada extremo ofrece «Mis direcciones» (combobox filtrado por ciudad y tipo; elegir una copia dirección, contacto, ventana e instrucciones) y la casilla «Guardar esta dirección» (se guarda tras publicar). Escribir a mano la dirección suelta la dirección elegida. Escrituras con `<Can>` (VIEWER solo lee, con el aviso de solo lectura).
- **Contactos y horarios** (`<details>` nativo, sin encabezados dentro del `summary`): privados hasta adjudicar; el detalle de la PYME los muestra en su tarjeta. Los errores del backend (`LOGYX-VAL-002` teléfono, `LOGYX-REQ-006` ventana) caen bajo su campo y abren la sección.
- **Ctrl+K** (`components/layout/CommandMenu`): además de secciones y tema, busca de verdad (`actions.search.query`, 200 ms de espera, `shouldFilter={false}` y filtrado propio de secciones). Grupos Solicitudes / Envíos / Contrapartes con el código en mono; `Enter` abre el `href` que entrega la API (según rol).
- e2e: `request-details.spec.ts` (duplicar, doble clic, duplicado reciente, contactos privados, libreta completa, lector, filtros + CSV + paginación, Ctrl+K, axe claro/oscuro). Corre contra el `astro dev` y la API que ya tengas: `E2E_BASE_URL=http://localhost:4321 E2E_API_URL=http://localhost:8080 npx playwright test tests/e2e/request-details.spec.ts --workers=1`.

## Ola 1 · agente E: operador, visitante y legal (2026-11-17)
Contrato: `docs/api/CHANGELOG.md` (V18). Roadmap: `docs/product/2026-11-17-roadmap-v2.md` ítems 1.13, 1.14, 2.17 y «Decisiones del dueño».
- **Páginas públicas** (`PUBLIC` y `OPEN_WITH_SESSION` del middleware): `/terminos` (borrador pendiente de revisión legal; texto en `features/legal/terms-content.ts`, versión y estado desde `GET /public/legal` vía `features/legal/server.ts`), `/precios` (comisión + IGV + ejemplo real de `POST /public/pricing/quote`, cacheado 10 min), `/contacto`. Enlaces en `layouts/Public.astro` (cabecera «Precios»; pie completo y compacto).
- **Registro**: `RegisterForm` con segunda casilla `acceptTerms` + `termsVersion` (props desde `registro.astro`) y `next` (solo `/pyme/solicitudes/nueva?…`; lo usa el cotizador). La API responde 409 `LOGYX-AUTH-012` si el texto cambió; el formulario lo muestra como error general.
- **Cotizador de la landing**: `CorridorExplorer` calcula local al instante y confirma con la acción pública `landing.quote` (`features/landing/actions.ts`; reenvía la IP del visitante en `X-Forwarded-For`: el límite del backend es por IP). Los parámetros de la nueva solicitud son `origen`, `destino`, `peso`, `tipo`; la pantalla de nueva solicitud (zona A) puede leerlos para precargar.
- **Operador**: `/operador/parametros` (`features/operator/components/ParametersEditor`, `parameters.ts`: % ↔ fracción sin coma flotante), `SearchInput` (`components/ui/search-input`) en los listados con `q` en la URL, `OperatorChat` (detalle de solicitud y de envío; el chat se pide a demanda porque cada lectura se audita), `ageTone(hours, active, {warn, breach})` con los umbrales vigentes de `/operator/metrics`.
- e2e: `operator-params.spec.ts`; `public.spec.ts`/`auth.spec.ts`/`a11y-public.spec.ts` ampliados. Helpers: `registerAccount` envía `acceptTerms`. Con el backend de :8080 usa `E2E_API_URL=http://localhost:8080`.

## Roles en la interfaz, avisos por persona y autoría (V16, 2026-11-17, Ola 1 zona D)
Contrato: `docs/api/CHANGELOG.md` (zona D, V16) y ADR-0009 §8.
- **`<Can>` (fuente única).** `import { Can, useCan, useRole } from '@/lib/permissions/react'`. `<Can>` oculta sin `OPERATE` (por omisión);
  `permission="MANAGE_USERS"`; `readOnly` (o `readOnly="No puedes …"`) pone en su lugar `ReadOnlyNotice` con «Pedir acceso»; `fallback`
  propio. En `.astro`: `can(Astro.locals.session, p)`. Ocultar acciones, nunca información. `Base.astro` escribe `data-perms` y `data-role`.
- **Rutas de escritura**: `WRITE_ROUTES` (`lib/permissions`) → `middleware.ts` redirige al listado con `?sinPermiso=<clave>`; `AppShell`
  muestra el aviso. Hoy: `/pyme/solicitudes/nueva`, `/transportista/viajes/nuevo`.
- **«Pedir acceso»**: `features/access` (action `access.request`, `RequestAccessDialog`, `RequestAccessButton`, `RequestAccessHost`
  montado en `AppShell` para quien no es ADMIN). `openRequestAccess(p)` lo abre desde cualquier sitio; `toast.error` de un `AUTH-007`
  agrega la acción «Pedir acceso». `UserMenu`: rol de la persona en el topbar, «Mis avisos» y «Pedir acceso» (Lector).
- **Avisos por persona**: campana y `/notificaciones` cuentan y marcan solo lo tuyo; «Tuyo» (`mine`) y la pestaña «Solo míos»;
  `/cuenta/avisos` (`NotificationPreferences`, actions `notifications.preferences|updatePreferences`). `ACCESS_REQUESTED` →
  `/organizacion/usuarios?usuario=<id>` (`lib/notification-links.ts`).
- **Autoría**: `features/activity` (`ActivityTrail`, `AuthorLine`, action `activity.get`, etiquetas en `labels.ts` sobre las de la
  auditoría). Montado en `/pyme/solicitudes/[id]` y `/incidencias/[id]`.
- e2e: `roles-v16.spec.ts` (Lector: redirección con aviso, menú con rol, pedir acceso → aviso al ADMIN, avisos por persona, preferencias, autoría; axe claro y oscuro).

## Envíos, contactos y seguimiento público (V19, 2026-11-18, Ola 1 zona B)
Contrato: `docs/api/CHANGELOG.md` (zona B, V19) y ADR-0011.
- **Lista en el servidor.** `/pyme/envios` y `/transportista/envios` piden `GET /shipments` con los filtros de la URL (`features/shipments/filters.ts`:
  `filtersFromUrl`, `apiQuery`, `toUrlQuery`; `estado` vacío = «En curso» = `PENDING,IN_TRANSIT`, `todos` = sin filtro) y 20 por página; la
  misma traducción la usa el proxy CSV (`/exportar/envios`, entrada `envios` en `pages/exportar/[recurso].ts`). Un `?id=` que no cumple el
  filtro se agrega al principio (`withSelected`), así los enlaces de avisos abren cualquier envío. `TrackingSplit` refresca su página con la
  action `shipments.page` (misma consulta) y conserva el envío abierto. Los KPIs del transportista se calculan con consultas aparte (sin los
  filtros de la lista).
- **Detalle**: código y referencia, «Duplicar» (PYME, `<Can>`), `ShareTrackingButton` (PYME), `StopContacts` (contacto con `tel:+51…`,
  ventana, instrucciones; no se pinta si la API no los manda), `StopTimeline` marca «registrado con retraso (sin señal)» con
  `lateSync(occurredAt, recordedAt)` (> 10 min), `ActivityTrail SHIPMENT`.
- **Compartir seguimiento** (`ShareTrackingDialog`): actions `shipments.shareLinks|createShareLink|revokeShareLink`; el enlace solo se ve al
  crearlo (`CopyButton` + WhatsApp `https://wa.me/?text=`); el Lector ve la lista con `<Can readOnly>`.
- **Página pública `/s/[token]`** (sin sesión, también con sesión): SSR con `features/shipments/server.ts#publicTracking` (reenvía la IP en
  `X-Forwarded-For`), `Base` con `privateUrl` (`noindex`, `referrer no-referrer`, sin canónica), isla `PublicTracking` (hitos, ETA en
  ventana, `PublicRouteMap` con la polyline de la propia respuesta: no llama a `/routes`, que exige sesión) y consulta cada 60 s
  (`/s/[token]/estado`, solo con la pestaña visible). No muestres nada que no venga en `PublicTrackingView`. `middleware.ts`: `isTrackingLink`
  (pública con y sin sesión, `Referrer-Policy: no-referrer`, `X-Robots-Tag`).
- **`/conductor`** muestra `StopContacts` de su viaje.
- e2e: `shipments-b.spec.ts` (contactos y `tel:`, retraso, Duplicar, filtros en la URL, CSV, compartir/copiar/WhatsApp/revocar, página
  pública sin datos sensibles y con `noindex`, Lector en solo lectura, conductor, axe claro y oscuro incluida la página pública).

## Pendientes conocidos
Mapa con ruta por carretera (hoy línea recta entre ciudades). La suite e2e no cubre aún el registro de conductor desde Flota ni el estado de vehículo; capturas en `docs/screenshots/`.

## Ola 1 zona C: oferta rápida, precio con IGV, flota y viajes (2026-11-18, agente C)
Contrato: `docs/api/CHANGELOG.md` (2026-11-18, V20). Reglas: `docs/domain/business-rules.md` (RN02 por fecha, comisión, «apoyo 1.9, 1.10, 1.12»).
- **`BidSheet` (oferta rápida, 1.10).** Preselecciona el último vehículo usado si es apto y está libre esas fechas (si no, el apto de menor capacidad), recuerda nota y atajo en `localStorage` (`features/marketplace/quick-bid.ts`, siempre `try/catch`), atajos **Sugerido / −5 % / −10 % / Piso** (nunca bajan del piso, RN03; no hay «mejor oferta − paso»: RN04) y **no recarga**: `onPlaced(bid, advance)` deja que `MarketplaceTable` marque la fila «Ofertada» en sitio, mueva el foco a la siguiente carga (`[data-bid-row]`) o abra la siguiente («Enviar y ofertar la siguiente carga», `hasNext`). `ReturnLoadsList` usa la misma hoja y marca «Ya ofertaste» en sitio. Sin `onPlaced` la hoja recarga (compatibilidad).
- **RN02 por fecha.** `actions.marketplace.eligibility` → `GET /shipment-requests/{id}/vehicle-eligibility` (agenda por vehículo, `CAP-005/007` bloquean, `CAP-006` avisa). `features/fleet/compatibility.ts` ya no bloquea por «en ruta» (solo mantenimiento); `usable` del marketplace, de `PublishTripForm` y de `marketplace.astro` también. Texto corregido: «Tu flota no lleva carga general» (`cargoPhrase`).
- **«Cobras S/ X neto».** `lib/pricing.ts` espeja `CommissionPolicy` (la comisión es sobre el valor de venta sin IGV y lleva su IGV; ejemplo S/ 1,180 al 8 % → 1,085.60); tasa por `features/marketplace/commission.ts`. Detalle plegable con `<details>`. En Mis ofertas: «Cobrarías S/ X neto» en las pendientes y botón «Historial» (`ActivityTrail subject="BID"` en un `Sheet`).
- **Precio adjudicado.** `components/domain/PriceBreakdown` (`ShipmentView.priceBreakdown`): IGV desglosado para la PYME (detalle del envío en `TrackingSplit` y tarjeta «Precio adjudicado» en `/pyme/solicitudes/[id]`), comisión y neto para el transportista.
- **Flota (1.12).** `FleetManager`: Editar (`VehicleDialog` con `vehicle`), Archivar, sin «Marcar en ruta»; errores `FLT-004/005/006` por toast o bajo el campo (`licenseNumber`, `weight`, `volume`).
- **Viajes.** «Duplicar» (tabla y detalle) → `/transportista/viajes/nuevo?from=<id>` (`PublishTripForm initial`, fecha +7 días a la misma hora de Lima). El detalle del viaje monta `ActivityTrail subject="TRIP"`.
- Tras añadir actions con el `astro dev` levantado: `touch src/actions/index.ts`. e2e: `tests/e2e/ola1-c.spec.ts` (oferta encadenada sin recargar, agenda, atajo al piso, axe claro y oscuro, precio con IGV, historial, flota, duplicar viaje).

## Ola 2 zona P: plantillas, preferidos, segunda oferta, bandeja y acciones masivas (2026-11-19, agente P)
Contrato: `docs/api/CHANGELOG.md` (2026-11-19, V21). Roadmap v2: 2.1, 2.9, 2.10 (lado PYME).
- **Plantillas** (`features/templates/`: `server`, `actions` → `actions.templates.*`, `labels.ts`, `components/{TemplatesManager,ScheduleDialog,PublishTemplateDialog,SaveTemplateDialog}`; página `/pyme/plantillas`). «Guardar como plantilla» en `RequestForm` y en el detalle (`RequestDemandActions`); «Abrir en el formulario» = `/pyme/solicitudes/nueva?plantilla=<id>` (precarga todo menos la fecha y muestra «Actualizar plantilla»). La programación (frecuencia, día, anticipación, fin, feriados, publicar o dejar borrador) se edita en `ScheduleDialog`, que calcula el primer recojo con `firstPickup` (espejo de `RecurrencePolicy`). Los borradores del modo `DRAFT` salen arriba en «Borradores por confirmar». Todo lo que escribe va en `<Can>`.
- **Bandeja de ofertas** (`/pyme/ofertas`, `requests/components/OffersInbox`, `actions.requests.inbox`): una fila por solicitud, mejor monto y mejor Trust Score, `Countdown`, «Aceptar la mejor» con `confirm()` que muestra transportista, vehículo y monto. En vivo con `useLiveRefresh` (`bid.placed`, `auction.updated`…).
- **Acciones masivas** (`requests/components/BatchActions`): selección con checkbox por fila y «todas» en `RequestsTable` (solo con `OPERATE`; se pierde al cambiar de página/filtro) → barra con Cancelar (`ReasonDialog` con motivo común), Reprogramar (fecha) y Duplicar para la semana siguiente (`Idempotency-Key` por selección). **No es atómico**: `BatchResultDialog` lista por solicitud lo que falló (`message` de la API) y recarga al cerrar. `actions.requests.batchCancel|batchReschedule|batchDuplicate`.
- **Preferidos y bloqueados** (`features/preferences/`: `CarrierMarkMenu` en cada oferta de `BidComparison` (`marks` desde el servidor), `/pyme/transportistas` con `CarrierPreferencesManager`). Bloquear pide `confirm` y oculta «Aceptar oferta» de ese transportista; la marca es privada. `RequestForm` y las plantillas tienen «Avisar a mis transportistas preferidos» (`invitePreferred`); el detalle trae «Invitar a ofertar».
- **Segunda oferta** (`requests/components/DemandPanels#BackupBidsPanel`): aparece en una solicitud `CANCELLED` con respaldos: pedir confirmación → esperar → «Adjudicar carga» (`actions.backup.*`). El transportista responde en `/transportista/confirmaciones` (`preferences/components/BackupRequests`, entrada «Confirmaciones» en el menú del transportista y enlace del aviso `BACKUP_REQUESTED`).
- Avisos: `TEMPLATE` → `/pyme/plantillas`; metadatos de `TEMPLATE_RUN`, `REQUEST_INVITATION`, `BACKUP_REQUESTED`, `BACKUP_RESPONDED` en `lib/notification-links.ts`.
- Tras añadir actions con el `astro dev` levantado: `touch src/actions/index.ts`. e2e: `tests/e2e/ola2-p.spec.ts` (claro y oscuro, axe). El modo borrador y los fallos del job se prueban en el backend (`TemplatesFlowTest`): el e2e no puede adelantar el reloj del job.

## Ola 2 · zona T: alertas de carga, mejorar oferta, documentos y agenda de flota (2026-11-19, agente T)
Contrato: `docs/api/CHANGELOG.md` (2026-11-19, V22). Roadmap: `docs/product/2026-11-17-roadmap-v2.md` ítems 2.3, 2.4, 2.8 y 2.13. Componentes: `.claude/skills/logyx-design-system/references/web.md` («Ola 2 · zona T»).
- **2.3 Búsquedas guardadas.** `marketplace/components/SavedSearches` (en la barra de `MarketplaceTable`): guardar los filtros actuales (+ vehículo opcional), «Mis búsquedas» (aplicar, pausar/reanudar, borrar), todo lo que escribe bajo `<Can>`. Actions `marketplace.savedSearches|saveSearch|updateSavedSearch|deleteSavedSearch`. Canales en `/cuenta/avisos` (la página pinta las categorías `ALERTS` y `FLEET` tal como las manda la API). `lib/notification-links.ts`: `NEW_LOAD_MATCH` → `/transportista/marketplace?solicitud=<id>` (abre la hoja de oferta; si ya no está abierta, un `Alert`), `VEHICLE`/`DRIVER` → `/transportista/flota?vehiculo=|conductor=<id>` (abre sus documentos), `BID_IMPROVED` → la solicitud.
- **2.8 Mejorar oferta.** `ImproveBidDialog` desde «Mis ofertas» (solo PENDING con la subasta abierta; actualiza la fila en sitio), historial por `marketplace.bidRevisions`; posición aproximada en `standingText().position`; `BidSheet` muestra «Precio de esta ruta» (`marketplace.laneStats`, nada si `null`) y las advertencias de documentos (`LOGYX-FLT-007/008`, bloqueo `FLT-007` como «Bloqueado: SOAT vencido…»). La PYME ve `BidSeals` («Mejorada», «SOAT vigente», «Revisión técnica vigente») en `BidComparison`.
- **2.4 Flota.** `FleetManager` suma columna «Documentos» (vehículos y conductores), `FleetDocumentsSheet`, `VehicleConfigDialog` («Configurar vehículo» en el menú) y `ExpirySummary` arriba. Proxies autenticados: `pages/flota/documentos/[id].ts` (GET, descarga) y `subir.ts` (POST multipart). Categoría de licencia y DNI: `fleet.patchDriver` bajo `<Can permission="MANAGE_DRIVERS">`. Operador: `fleet_docs_block` (`FLAG`) en `ParametersEditor`.
- **2.13 Agenda.** `/transportista/agenda` (`FleetAgenda`, `AssignSheet`; entrada «Agenda» en `AppShell`): filas vehículos/conductores × 7 o 14 días (`?desde=&dias=`), «Sin conductor» con «Asignar» y «Asignar en lote». Actions `fleet.schedule|unassigned|suggestions|assign|documents|expiries|vehicleConfig|patchDriver`. `lib/lima-date.ts` para fechas calendario.
- e2e: `tests/e2e/ola2-t.spec.ts` (todo lo anterior + Lector + axe en claro y oscuro). Corre con `E2E_BASE_URL=http://127.0.0.1:4321 E2E_API_URL=http://localhost:8080 npx playwright test ola2-t.spec.ts --workers=1`. El precio de la ruta con datos (≥ 5 envíos de ≥ 3 transportistas) no se prueba con datos reales: solo que no aparece sin ellos.


## Ola 2 zona X: ejecución y operación (V23, 2026-11-19, agente X; roadmap 2.5, 2.6, 2.7, 2.15)
Contrato: `docs/api/CHANGELOG.md` (zona X). ADR-0012 (ajustes y precio final).
- **Envío (`ShipmentExtras`)**: ahora guarda el envío en estado local y lo refresca con `actions.shipments.get` tras una acción. `ExecutionPanel`:
  la PYME elige «Qué pide el recojo» (switches foto/peso, solo PENDING) y el transportista «Registrar llegada/carga recogida/entrega» desde la
  oficina (`ReasonDialog` con motivo + peso o receptor; la foto de entrega no la sube la oficina). Pestaña **Ajustes** (`AdjustmentsPanel`,
  también en `/operador/envios/[id]` con `perspective="OPERATOR"`): resumen acordado/aceptados/final + `PriceBreakdown totalLabel="Precio final"`,
  sugerencias de estadía/peso con «Proponer», diálogo con `RadioGroup` de tipos, `MoneyInput`, «Es un descuento», parada y nota (clave de
  idempotencia por diálogo); PYME acepta (`confirm`) o rechaza (`ReasonDialog`, `reasonField="note"`), transportista retira. Tiempo real:
  `shipment.adjustment_updated`. `ShipmentFacts` muestra «Precio final» y «N ajustes por responder». `StopTimeline` agrega peso medido, quién
  recibió y «registrada por la oficina: motivo».
- **Incidencias v2**: `CATEGORY_OPTIONS` (14, con «Detiene el viaje»/«Informativa»), `SEVERITY_OPTIONS`, `INFORMATIVE_CATEGORIES`, `CANNED_REPLIES`
  (`features/incidents/lib.ts`). Alta con severidad, monto reclamado y adjunto (se sube primero por `/documentos/subir` como `OTHER` y se pasa en
  `attachmentIds`) e `Idempotency-Key`; el botón ya no se bloquea por una activa (la API responde `INC-001`). Detalle: aviso «El envío está
  detenido» solo si `holdsShipment`, «Retirar incidencia» (`canWithdraw`), plazo, adjuntos, ubicación; operador: «Tomar incidencia»/«Dejar sin
  responsable», nota interna (casilla) y respuestas frecuentes, «Reabrir incidencia». `/operador/incidencias` usa `OperatorIncidentInbox`
  (`GET /operator/incidents`, filtros Activas/Mías/Sin responsable/Plazo vencido + severidad, «Pasar a revisión» y «Tomar»; el «Vencido» sale de
  un reloj local, no de re-pedir).
- **Operador 360**: `/operador/organizaciones/[id]` (`Organization360`: KPIs, pestañas Resumen/Usuarios/Flota/Envíos/Incidencias/Verificación/
  Auditoría, suspender/reactivar usuario, «Cambiar razón social o RUC» por el proxy multipart `/operador/organizaciones/[id]/cambio-legal`;
  descarga del sustento por `/operador/cambios-legales/[id]`). Enlace «Ver ficha completa» desde la hoja de `OrganizationsInbox`.
  `/operador/senales` (`SignalsList`, etiquetas en `features/operator/signals.ts`) y `/operador/ajustes` (`EscalatedAdjustments`); entradas
  «Ajustes» y «Señales» en el `AppShell` del operador. `ParametersEditor` acepta `kind = AMOUNT` (`MoneyInput`, `S/`).
- **Registro**: tras `LOGYX-ORG-002`, `RequestOrgAccess` (bajo el RUC) abre «Pedir acceso a la empresa» → action pública `orgAccess.request`.
- `StatusPill kind="adjustment" | "severity"`. Avisos nuevos en `lib/notification-links.ts` (`ADJUSTMENT_*`, `INCIDENT_ASSIGNED`; el escalado
  lleva al operador a `/operador/ajustes`).
- **Trampa (dev):** con el `astro dev` levantado, una action nueva que importa `LIMITS` de `lib/limits.ts` recién editado puede quedarse con el
  módulo viejo (`max(undefined)` → «Cannot read properties of undefined»): en las actions de esta zona los topes van como literales.
- e2e: `tests/e2e/ola2-x.spec.ts` (ajuste propuesto/aceptado/rechazado, recojo, registro por la oficina, informativa retirada, bandeja y nota
  interna del operador, 360 con suspender/reactivar, señales y ajustes; axe en claro y oscuro). `incidents.spec.ts` y `shipment-actions.spec.ts`
  se ajustaron a que `DELAY` es informativa.

## Ola 2 zona F: tarifario, parámetros y calendario (V24, 2026-11-19, agente F; roadmap 2.14)
- `features/config/` (acciones `actions.config.*`): `server.ts` (tarifario `/pricing/tariffs*`, calendario `/catalog/calendar`),
  `components/TariffEditor.tsx` (vigente, versiones, «Nueva versión» ya o programada con `ReasonDialog`, anular), `CalendarEditor.tsx`
  (feriados, paros y avisos por ciudad), `CalendarBanner.astro` (aviso global de hoy a pasado mañana, montado en `AppShell`; si la API
  falla no muestra nada) y `HolidayHint.tsx` (aviso bajo la fecha de recojo en `RequestForm`, no bloquea).
- `/operador/parametros` suma las secciones «Tarifario» y «Feriados y paros» (anclas `#tarifario`, `#calendario`). `ParametersEditor` y
  `formatParam` entienden los tipos `MINUTES` («90 min», «3 h»), `COUNT` («5 ofertas») y `DAYS` («30 días»).
- Reportes y cobros (2.11/2.12): ver la sección de reportes y cobros.


## Ola 2 zona F: preferencias, correo y términos (V24, 2026-11-19; roadmap 2.2 y 2.17)
- **`/cuenta/avisos`** suma `features/notifications/components/EmailSettingsCard` (tarjeta «Correo»: `Switch` correo sí/no, `RadioGroup`
  al momento / resumen diario con la hora en `Select`, horario de silencio con dos `TimeSelect`; la API devuelve `HH:mm:ss`, la tarjeta
  trabaja con `HH:mm`). Actions `notifications.emailSettings|updateEmailSettings`. Cualquier rol (también Lector).
- **`/organizacion/preferencias`** (`features/organization/components/OrganizationPreferencesForm`, actions
  `organization.preferences|updatePreferences`; enlace desde `/organizacion` y entrada «Preferencias de la empresa» del menú): rutas (filas
  `Combobox` origen/destino, ≤ 20), ciudades y tipos (`MultiSelect`); transportista: «Alertas por mis rutas» y precio mínimo por km
  (`MoneyInput`); PYME: horario de recojo (`TimeSelect`) y plazo de pago (`NumberInput suffix="días"`). Escribe solo
  `<Can permission="MANAGE_ORGANIZATION">`; los demás ven un resumen (`DescriptionList`). Las claves de las filas salen del índice para que
  los ids coincidan entre el SSR y la hidratación.
- **`/baja`** (pública, también con sesión; sin JS): `?t=<token>` → `features/notifications/unsubscribe.ts` (`inspectUnsubscribe`,
  `unsubscribe`, reenvía la IP) → confirmar con un `<form method="post">` a la misma página (`scope` CATEGORY o ALL). **`/baja/one-click`**
  (`POST`, RFC 8058, sin cookies; `GET` redirige a `/baja`). `middleware.ts`: públicas, sin `Referer`, `noindex`; `robots.txt`: `Disallow: /baja`.
- **Términos (2.17).** `components/domain/TermsGate` (Radix AlertDialog que no se cierra con Esc ni clic fuera; «Cerrar sesión» por
  `POST /logout`) lo monta `AppShell` si `/auth/me` trae `termsAccepted = false` (PYME y transportista; no conductor ni operador). Action
  `auth.acceptTerms`; si la API responde `AUTH-012` (la versión cambió otra vez) recarga. La invitación (`ResetPasswordForm`, `/restablecer`)
  tiene una segunda casilla «Acepto los Términos y condiciones» y envía `acceptTerms` + `termsVersion` (de `GET /public/legal`).
- **`/seguridad`** («Seguridad y seguro», borrador; texto en `features/legal/safety-content.ts`): pie de `Public.astro`, sitemap e
  `INDEXABLE_PATHS`. `VerificationBanner` muestra el plazo «1 día hábil» y «Qué revisamos» (`VERIFICATION_CHECKLIST` en
  `features/verification/copy.ts`). El panel de login/registro (`AuthShell`) ya no tiene testimonio «de ejemplo».
- e2e: `tests/e2e/ola2-f-prefs.spec.ts` (axe claro y oscuro en `/cuenta/avisos`, `/organizacion/preferencias`, `/baja`, el diálogo de términos y
  `/seguridad`). Para ver el diálogo con una cuenta de prueba: `api.forgetTerms(email)` (`POST /api/v1/dev/terms/forget`, solo dev).
  `api.resetPasswordApi(token, pass, true)` acepta ahora privacidad **y** términos.


## Ola 2 zona F: reportes y cobros (2026-11-19, agente web F; roadmap 2.11 y 2.12)
Contrato: `docs/api/CHANGELOG.md` (zona F), ADR-0010 (la PYME nunca ve la comisión) y ADR-0012 (precio final = acordado + ajustes aceptados).
- **Reportes** (`features/reports/`: `server`, `labels` (`monthLabel`, `routeLabel`, `formatPercent`), `components/{ReportView,MonthlyBars,PrintButton}`). Una isla `ReportView` con `perspective` para `/pyme/reportes`, `/transportista/reportes` y `/operador/reportes`; el rango va en la URL (`?desde=&hasta=`, sin fechas = 6 meses; la API responde `VAL-001` si pasa de 400 días y la página lo muestra en un `Alert`). Las columnas salen de `COLS[perspective]`: la de la PYME no incluye comisión ni neto. Gráfico de barras de una serie (`MonthlyBars`, CSS, `role="img"` con el detalle; la tabla «Por mes» trae los mismos datos). `ExportButton` → `/exportar/reportes` (entrada nueva en `pages/exportar/[recurso].ts`: `desde/hasta` → `from/to`).
- **Liquidación imprimible** `pages/reportes/liquidacion/[periodo].astro` (PYME «Resumen de costos», transportista «Liquidación de comisión»; operador con `?org=<id>`, enlazada desde su tabla por transportista): sin librería PDF. La página se ve dentro de `AppShell`; `@media print` (en `styles/global.css`: oculta `#sidebar`, `#app-topbar` y `.no-print`, vuelve a tokens claros aunque el tema sea oscuro y fuerza el logo claro; en la página: tabla en blanco y negro, `@page` A4 horizontal para la liquidación de comisión y vertical para el resumen) más `PrintButton` (`window.print()`). El pie lleva el `disclaimer` de la API.
- **Cobros** (`features/settlements/`: `server`, `actions` → `actions.settlements.*`, `labels`, `filters`, `upload`, `components/`). `SettlementCard` («Cobro») se monta en `ShipmentExtras` (PYME y transportista, sobre las pestañas) y en `/operador/envios/[id]`; no pinta nada si el envío no está `DELIVERED` o la API responde `LOGYX-STL-001` (`getSettlement` → `null`). Los botones salen de `SettlementView.actions` (`INVOICE`, `PAY`, `CONFIRM`, `DISPUTE`, `WITHDRAW_DISPUTE`, `RESOLVE`) dentro de `<Can>`. Diálogos en `SettlementDialogs`: comprobante y pago suben el archivo antes por `/documentos/subir` (`uploadProof`; el proxy ahora acepta `PAYMENT_PROOF` y `CARRIER_INVOICE`) y pasan el `documentId`; el pago exige número de operación salvo efectivo; disputar usa `ReasonDialog`; resolver (operador) elige estado + nota (≥ 3 caracteres); retirar la disputa propia es `confirm()` + `resolve` sin estado. La PYME ve «Dónde pagar» (`PayoutAccountsList`, con `CopyButton` del CCI/celular).
- **Listados**: `/pyme/pagos` («Por pagar»), `/transportista/cobros` («Por cobrar» + cobros de comisión) y `/operador/cobros` (disputas, `OperatorCommissionPanel` con «Registrar cobro de comisión» = `CommissionCollectDialog`: transportista → envíos con comisión pendiente marcados con total → forma de pago, operación y fecha; el monto lo calcula la API). `SettlementsList` (una isla con `perspective`; filtros en la URL `estado` = estado o `VENCIDOS`/`COMISION`, `desde`, `hasta`, `pagina`; 20 por página) y `SettlementKpis` (**en archivos separados**: dos componentes del mismo módulo, uno por defecto y otro nombrado, hacen que Astro no pinte el segundo). La API limita `size` a 100.
- **Datos de cobro** `/organizacion/datos-de-cobro` (`PayoutAccountsManager`; solo transportista, otro tipo → `/sin-acceso`; enlace en `/organizacion` y en el menú «Organización»): agregar (CCI de 20 dígitos o celular de 9), principal, archivar con `confirm()`. Escribe `<Can permission="MANAGE_ORGANIZATION">` (el ADMIN del transportista); los demás ven `•••• last4`.
- Menú (`AppShell`): grupo «Dinero» (PYME: Por pagar, Reportes; transportista: Por cobrar, Reportes), «Cobros» y «Reportes» del operador, «Datos de cobro» en «Organización» del transportista. `StatusPill kind="settlement" | "commission"`. `lib/lima-date.ts#limaDateOf(iso)` da el día de Lima de un instante (`formatDate` solo acepta fechas sin hora: con un instante lanza `Invalid time value` y corta el SSR sin error visible).
- **Trampa (dev):** si una isla falla durante el SSR, Astro corta la respuesta tras el HTML ya enviado (status 200) y el resto de la página no aparece: mira `web/.astro/dev.log`.
- e2e: `tests/e2e/ola2-f.spec.ts` (11 tests: flujo factura → pago → confirmación, disputa y resolución, retirar disputa, Lector, listados y filtros, cobro de comisión del operador, reportes por rol, rango/400 días/CSV, liquidación en pantalla e impresión (`emulateMedia print`), datos de cobro; axe en claro y oscuro en cada pantalla y en los diálogos). Corre con `E2E_BASE_URL=http://127.0.0.1:4321 E2E_API_URL=http://localhost:8080 npx playwright test ola2-f.spec.ts --workers=1`.
