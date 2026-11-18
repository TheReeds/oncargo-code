# Referencia web — componentes compartidos

Stack: Astro 7 SSR · React 19 islas · Tailwind 4 (tokens en `web/src/styles/global.css`) · primitivas Radix UI (MIT) · cmdk · react-day-picker 10 (locale es) · sonner · lucide-react. Código propio estilo shadcn/ui en `web/src/components/ui/`. Catálogo vivo: `/dev/ui` (solo dev).
Importa siempre con alias: `import { Button } from '@/components/ui/button'`.

## Catálogo (ruta → exports)
| Archivo | Exports | Notas |
|---|---|---|
| `button` | `Button`, `buttonVariants` | variants: default, money, outline, ghost, destructive, destructive-solid, link; size: default, sm, lg, icon; `loading`, `asChild` |
| `input` | `Input`, `Textarea`, `fieldClass` (+ re-export `Field`, `Label`) | `aria-invalid` pinta rojo |
| `field` | `Field`, `Label` | `<Field label htmlFor hint error required>`; el control lleva `id={htmlFor}`; error con `role="alert"` |
| `select` | `Select`, tipo `Option` | `value`, `onValueChange`, `options=[{value,label,hint?}]`, `placeholder`, `invalid`; `''` = sin selección |
| `combobox` | `Combobox`, `MultiSelect` | búsqueda cmdk; `allowClear` para filtros; MultiSelect `value: string[]` |
| `date-picker` | `DatePicker`, `DateRangePicker`, `DATE_PRESETS`, `parseISODate`, `toISODate` | valores `'YYYY-MM-DD'`; rango `{from,to}` con `''` vacío; atajos Hoy / Próximos 7 días / Este mes / Próximos 30 días; `min`/`max`; `className` va al contenedor y el botón "Quitar" es hermano del disparador (nunca un control dentro de otro: axe `nested-interactive`) |
| `number-input` | `NumberInput`, `MoneyInput` | `value` string crudo ("1850.5"), muestra `1,850.5`; Money: prefijo `S/`, 2 decimales; `suffix="kg"` |
| `form-controls` | `Checkbox`, `RadioGroup`, `RadioGroupItem`, `Switch`, `InlineLabel` | Radix; `onCheckedChange` / `onValueChange` |
| `confirm-dialog` | `confirm()`, `ConfirmDialog`, `ConfirmHost` | ver abajo |
| `reason-dialog` | `ReasonDialog` | diálogo con **motivo** (cancelar, rechazar, suspender, reasignar): `required`, `maxLength` (de `lib/limits`), `tone="destructive"`, `hideReason`, `children` (campos extra), `canSubmit`, `onFailure`; `onSubmit(reason)` devuelve `null` (éxito) o `splitActionError(error)`: el error de `reason` y `LOGYX-NEG-001` salen bajo el campo, el resto en un `Alert` |
| `toaster` | `toast`, `flash`, `Toaster`, default `Overlays` | `Overlays` ya está montado en `layouts/Base.astro` |
| `alert` | `Alert` | `tone`: info, success, warning, error; `title`, `action` |
| `dialog` / `sheet` | `Dialog` / `Sheet` | modal centrado con footer / panel lateral |
| `tabs` | `Tabs`, `TabsList`, `TabsTrigger`, `TabsContent` | Radix |
| `tooltip` | `Tooltip` | `<Tooltip content="..."><Button/></Tooltip>` |
| `dropdown-menu` | `DropdownMenu`, `…Trigger`, `…Content`, `…Item` (`destructive`), `…Label`, `…Separator` | menú de acciones |
| `pagination` | `Pagination` | `page` (base 1), `size`, `total`, `onPageChange` — directo de `meta` |
| `table` | `Table`, `Th`, `Td`, `Tr` | usar con `font-mono tabular-nums` en cifras. **Ver "Paneles de datos"**: `label`, `stack`, `sticky`, `dense`, `Th sort/onSort`, `Td label/primary/actions` |
| `description-list` | `DescriptionList` | datos clave–valor de un detalle (`items=[{label,value,hint?,mono?,money?,span?}]`, `cols` 1–3 por container query) |
| `quick-filters` | `QuickFilters` | segmentos con contador sobre un listado (`aria-pressed`, scroll horizontal en móvil) |
| `password-input` | `PasswordInput` | `Input` con botón mostrar/ocultar; dentro de `Field`, `id={htmlFor}` (su botón se llama "Mostrar contraseña": en tests usa `getByLabel('Contraseña', { exact: true })`) |
| `filter-bar` | `FilterBar` | barra de filtros de listados: hijos = `Field`s (rejilla), `extra` = 2.ª fila, `sort`, `count`+`noun`, `active`+`onClear` ("Limpiar filtros"); `role="search"` |
| `timeline` | `Timeline`, tipo `TimelineItem` | pasos/paradas: `items=[{id,title,detail?,meta?,status:'done'\|'current'\|'pending'}]`, `orientation` horizontal (apila en móvil) o vertical, `label` obligatorio. Paradas de envío: `StopTimeline`/`stopsToItems` (features/shipments) |
| `auth-card` | `AuthCard` | tarjeta de login/registro: `title`, `subtitle`, `footer` (en `.astro`: `<Fragment slot="footer">`), `className` para el ancho (`max-w-md` default) |
| `star-rating` | `StarRating` | entrada 1–5 accesible (radiogroup, teclado) y `readOnly` con decimales |
| `file-dropzone` | `FileDropzone` | arrastrar/soltar + botón; `accept`, `maxBytes` (10 MB), error por archivo |
| `card`, `badge`, `skeleton`, `avatar`, `kbd` | `Card`/`CardHeader`/`CardTitle`/`CardDescription`/`CardBody`/`CardFooter`, `Badge`, `Skeleton`/`SkeletonRows`/`SkeletonList`, `Avatar`, `Kbd` | |
Dominio (`components/domain`): `StatusPill`, `RouteLine`, `KpiTile`, `EmptyState`, `Countdown`, `TrustScore` (`variant="chip"` en listas | `"meter"` en detalle; 75 = sin calificaciones; `trustTone`), `StatusPill` (también incidencias OPEN/IN_REVIEW/RESOLVED/CLOSED y `kind="counter"`), `VerifiedBadge` (= "Verificado por OnCargo" (`rucVerified`); `variant="badge"` chip | `"icon"` escudo con tooltip para listas densas; no renderiza si `verified` es falso; `rucValid` no da sello). `VerificationBanner` (estado `PENDING/REJECTED/SUSPENDED` con nota y a quién escribir; lo monta `AppShell`) y `VerificationBlock` / `reportFailure()` (bloqueo `LOGYX-ORG-003/004` dentro de un formulario o tras un botón). `StatusPill kind="verification"` (En revisión/Verificada/Rechazada/Suspendida) y estado `WITHDRAWN` (Retirada). `features/fleet/components/UnfitList` (vehículos no aptos con su motivo). Formatos: `lib/format.ts`.

## Ejemplos
```tsx
// Formulario con select, combobox, fecha, monto
<Field label="Ciudad de origen" htmlFor="origin" error={errors.origin}>
  <Combobox id="origin" value={v.origin} onValueChange={(x) => set('origin', x)} options={cities} invalid={!!errors.origin} />
</Field>
<Field label="Fecha de recojo" htmlFor="pickup" error={errors.pickup}>
  <DatePicker id="pickup" value={v.pickup} onValueChange={...} min={today} invalid={!!errors.pickup} />
</Field>
<Field label="Tu oferta" htmlFor="amount" hint={`Mínimo ${formatMoney(floor)}`}><MoneyInput id="amount" value={amount} onValueChange={setAmount} /></Field>

// Acción con confirmación + toast + error
async function cancel() {
  if (!(await confirm({ title: '¿Cancelar esta solicitud?', description: 'Se cerrará la subasta. No se puede deshacer.', confirmLabel: 'Cancelar solicitud', tone: 'destructive' }))) return;
  setBusy(true);
  const { error } = await actions.requests.cancel({ id });
  if (error) { toast.error(error.message); setBusy(false); return; }
  flash('Solicitud cancelada');          // aparece tras recargar/redirigir
  window.location.reload();
}

// Filtros con rango de fechas (el valor viaja como strings)
<DateRangePicker value={range} onValueChange={setRange} placeholder="Fecha de recojo" />

// Paginación con meta de la API
<Pagination page={meta.page} size={meta.size} total={meta.total} onPageChange={load} />
```
Errores de Astro Actions: validación de campo → `Field error`; fallo de la acción completa → `toast.error(error.message)`; fallo que bloquea la página (carga de datos) → `Alert tone="error"`. Éxito → `toast.success` (o `flash` antes de navegar).

Errores por campo de la API (`error.field`): `run()` los codifica en el mensaje; en la isla usa `splitActionError(error)` (`@/lib/action-error`):
```tsx
const { message, field } = splitActionError(error);
if (field === 'amount') setErrors({ amount: message }); else toast.error(message);
```
Campos que envía la API: `ruc`, `email`, `plate`, `amount`, `vehicleId`, `reason`, `notes`, `message`, `body`, `comment`, `description`, `cargoDescription`, `originAddress`, `destinationAddress`, `legalName` (RN07, `LOGYX-NEG-001`) y los parámetros de listado. `splitActionError` también devuelve `code` (`LOGYX-XXX-NNN`). Para un formulario completo usa `toFormErrors(error, ['amount','vehicleId','notes'])` → `{ errors, formError, failure }` (`errors` por campo con el texto de RN07 si es `NEG-001`; `LOGYX-CAP-*` siempre en `vehicleId`; el resto en `formError` para un `Alert`; bloqueos `ORG-003/004` → `isVerificationFailure` + `VerificationBlock`). Las longitudes salen de `lib/limits.ts` (espejo de los `@Size` del backend). Registro (`RegisterForm`) y nueva solicitud (`RequestForm`) ya mapean `field` → control (nunca parsees el texto del mensaje). `error.message` directo sigue funcionando (el separador es invisible).

## Do / Don't
- DO `Field` + `id` para todo control. DON'T `<label>` ni `<select>`/`<input type="date">` sueltos.
- DO `confirm()` para aceptar/cancelar/eliminar. DON'T `window.confirm`, banners "¿Seguro?" en línea ni librerías externas (SweetAlert).
- DO `loading` en el botón del submit. DON'T cambiar solo el texto.
- DO `Combobox` para ciudades (11+ opciones) y filtros con `allowClear`; `Select` para listas cortas (tipo de carga, estado).
- DO valores de fecha `YYYY-MM-DD` tal cual los espera la API. DON'T `new Date('2026-11-28')` (corre un día por zona horaria): usa `parseISODate`.
- DO tokens (`text-ink-500`, `bg-petrol-50`). DON'T hex, `gray-*`, `blue-*`, sombras pesadas.
- Si falta un componente: créalo en `components/ui`, agrégalo a `/dev/ui` y a esta tabla.

## Accesibilidad (verificada con axe en `web/tests/e2e/a11y.spec.ts`)
- Texto pequeño: `text-ink-500` (#5b6b7f) ya cumple AA sobre blanco/steel-100/petrol-50; no uses `ink-400` para texto.
- Un control con `aria-label` debe contener el texto visible (regla `label-content-name-mismatch`): mejor `sr-only` dentro del botón que `aria-label` distinto.
- `Tabs` sin `TabsContent` (solo filtros): pasa `aria-controls={undefined}` al `TabsTrigger`.
- `<dl>` solo con `dt`/`dd` directos (en un `div` envolvente no cabe nada más); para tarjetas de dato con ícono usa `ul/li`.
- Contenedores con controles internos (mapa) no llevan `role="img"`; usa `role="region"` + `aria-label`.
- `Select` acepta `aria-label` cuando no hay `Field` visible (p. ej. orden de un listado).

## Trampas
- **Radix Select no admite `value=""`**: por eso `Select` convierte `''` en "sin valor" y muestra placeholder.
- `toast`, `confirm` y `flash` funcionan desde cualquier isla porque `Overlays` (Toaster + ConfirmHost) se monta una vez en `Base.astro` con `client:load`. En páginas que no usen `Base.astro` no habrá toasts; `confirm()` cae a `window.confirm`.
- Las islas son raíces React separadas: no compartas estado vía contexto entre islas; usa props o eventos.
- Popovers/menús usan portal y z-index 60–80 (Sheet 50, ConfirmDialog 70). No pongas z-index propios por encima.
- `NumberInput`/`MoneyInput` entregan string; conviértelo con `Number(value)` solo al validar/enviar.
- `react-day-picker` v10: importa locale con `import { es } from 'react-day-picker/locale'` (ya lo hace `Calendar`). No importes su CSS: está tematizado con clases.
- En `.astro`, no pases `<Icon />` como prop a un componente React; pasa la referencia (`icon={Truck}`).
- Servidor de dev: tras instalar dependencias nuevas reinicia `npm run dev` (Vite reoptimiza y puede dar 504 "Outdated Optimize Dep").

## Fase 3 (negociación, reputación, documentos, incidencias, notificaciones, operador)
- Chat reutilizable: `features/negotiation/components/ChatPanel` (`readOnly`, polling). Errores de contacto (RN07) inline bajo el campo.
- Evita respuestas obsoletas en islas con polling/filtros: contador `useRef` y descarta la respuesta si cambió (ver `NotificationsList`).
- `role="log"` va en un `div` que contiene el `ul` (axe `listitem`). El tono rojo de `Badge` usa un rojo más oscuro para AA.

## Fase 4 (viajes, reservas, retornos, Smart Load)
- `time-select` (`TimeSelect`, `timeOptions`): hora 24 h cada 30 min (`'HH:mm'`) junto a `DatePicker`; para armar `departureAt` usa `${fecha}T${hora}:00-05:00`.
- `domain/CapacityBar`: ocupación `used/total` en kg, `role="meter"` con nombre (`label`); ámbar desde 90 %.
- `StatusPill kind="trip" | "booking" | "smart"` (TripStatus, BookingStatus, SmartLoadStatus); sus claves chocan con las de incidencias/envíos, por eso requieren `kind`.
- Precio de reserva: `estimateBookingPrice` y `formatTripRate` en `lib/format.ts` (misma regla que el backend).
- Patrón: `features/trips/*`; la carga de retorno reutiliza `BidSheet` (prop `defaultVehicleId`; solo preselecciona si el vehículo está disponible).

## Modo oscuro
- `html[data-theme="dark"]` (en `styles/global.css`) **redefine los tokens** (`ink-*`, `steel-*`, `surface`, `petrol-*`, `emerald-*`, `amber-*`, `red-*`) más `chrome`, `on-solid`, `scrim`. Valores y contrastes: `docs/design/DESIGN.md`.
- Escribe componentes solo con tokens: **nunca** hex, `bg-white`, `text-black`, `bg-black` ni `bg-ink-900/40`. Texto sobre un relleno sólido (`bg-petrol-700`, `bg-emerald-700`, `bg-red-600`, `bg-emerald-500`) = `text-on-solid` (no `text-white`: en oscuro esos rellenos son claros). Fondos oscuros fijos en ambos temas (sidebar, tooltip, hero) = `bg-chrome text-white`. Velo de overlays = `bg-scrim`. SVG: `style={{ fill: 'var(--color-surface)' }}`. Variante `dark:` disponible (`@custom-variant`), pero casi nunca hace falta.
- Selector: `components/ui/theme-toggle.tsx` (Claro/Oscuro/Sistema, `aria-label="Tema"`), ya montado en el topbar de `AppShell` y en `Public.astro`. Lógica en `lib/theme.ts` (`useResolvedTheme()` para islas que dependen del tema: sonner, mapas).
- Sin parpadeo: script `is:inline` en el `<head>` de `Base.astro` fija `data-theme` leyendo `localStorage['logyx-theme']` (light|dark|system) antes de pintar. La CSP de producción ya permite `script-src 'unsafe-inline'`; no la endurezcas sin hash.
- Mapas MapLibre: estilo `positron` en claro, `https://tiles.openfreemap.org/styles/dark` en oscuro; al cambiar el tema `map.setStyle` y se re-agregan fuente/capa/marcadores (ver `TrackingMap.tsx`).
- **Mapa de ruta por carretera (ADR-0008).** Nunca dibujes rectas entre ciudades a mano. Patrón (ver `TrackingMap.tsx`):
  `useRouteGeometries(rutas)` (`features/routing/use-route-geometries`, action `routing.route` → `GET /api/v1/routes`, caché por
  pestaña; `toRouteGeometry` mapea `polylinePrecision` → `precision`) + utilidades de `@/lib/routing`:
  `ensureRoutePathLayer(map, { id, color })` en `load` y en cada `style.load` (idempotente; declara la atribución
  "Rutas © OpenStreetMap" en la fuente, que sale en el control de atribución), `routePathFeature({ id, selected, geometry,
  fallback })` y `setRoutePaths`, y `boundsOf(features)` para `fitBounds`. Mientras carga o si falla, la ruta es la recta
  **punteada** entre ciudades (`approx`); con geometría, línea continua. Colores: `#0B4F6C` claro / `#38A3C9` oscuro; seleccionada
  4 px opaca, el resto 2 px al 45 %. Marcadores en los centros de las ciudades (origen círculo hueco, destino cuadrado).
- e2e: `tests/e2e/a11y-dark.spec.ts` (axe en oscuro). Revisa cada pantalla nueva en ambos temas en `/dev/ui`.
- Catálogo: `ui/theme-toggle` (`ThemeToggle`, sin props obligatorias) en el encabezado de `/dev/ui`.

## Cuentas, roles y contraseñas (V14, agente web-B)
- **Ocultar por rol:** `useCan('OPERATE' | 'MANAGE_USERS' | 'MANAGE_ORGANIZATION' | 'MANAGE_DRIVERS')` (`@/lib/use-can`) en islas; `can(Astro.locals.session, 'OPERATE')` (`@/lib/permissions`) en `.astro`. No muestres un botón de escritura "deshabilitado": escóndelo y, donde falte la acción, deja `<ReadOnlyNotice>No puedes …</ReadOnlyNotice>` (`components/domain`, aviso discreto "Tu rol Lector solo permite consultar…", no renderiza si puede operar; también como isla `client:load` en `.astro`). El backend manda (`AUTH-007`/`AUTH-008`): muestra su mensaje tal cual. Un chat de solo lectura reutiliza `ChatPanel readOnly`.
- **`RoleChoice`** (`features/organization/components`): `RadioGroup` de tarjetas con el nombre y la descripción de cada rol (ADMIN, OPS, VIEWER; textos en `lib/permissions.ts` `ROLE_DESCRIPTION`).
- **`PasswordRequirements`** (`features/auth/components`): lista de reglas visibles (8–72 caracteres, coinciden) enlazada por `aria-describedby`; úsala con `PasswordInput autoComplete="new-password"` + repetir contraseña. `checkPassword(password, confirm)` devuelve el estado.
- **`StatusPill kind="account"`**: `ACTIVE` / `INVITED` / `INACTIVE` (usuarios y conductores). Estados de Mis ofertas: `LOST` (Perdida) y `BID_CANCELLED` (Cancelada) además de `WITHDRAWN`.
- **403 amable:** `pages/sin-acceso.astro` (`Astro.response.status = 403`) con `EmptyState actionHref/actionLabel` (en `.astro` no pases un `<a>` como `action`).
- **Página con permiso propio:** `if (!can(session, 'MANAGE_USERS')) return Astro.rewrite('/sin-acceso');`.
- **Tabla con filtros en la URL y detalle lateral:** `features/operator/components/AuditViewer.tsx` (MultiSelect/Combobox/DateRangePicker + chips de filtros activos + `Sheet` con `DescriptionList`; patrón de `OperatorListing`).

## Tiempo real (SSE, ADR-0007)
- **Regla:** no hagas `setInterval` para datos del servidor. Usa `useLiveRefresh` de `lib/realtime.ts`; los eventos son avisos (payload mínimo) y la isla vuelve a pedir sus datos por Astro Action.
  ```tsx
  useLiveRefresh({
    types: ['bid.placed', 'auction.updated'],               // tipos de CHANGELOG.md (Fase 6)
    match: (e) => e.data.requestId === requestId,           // filtra por lo que muestras
    refresh: async () => { const { data } = await actions.requests.detail({ id: requestId }); if (data) setState(data); },
    fallbackMs: 60_000,                                     // polling de respaldo, SOLO si el stream no está "live"
  });
  ```
  También refresca al (re)conectar (`onResync`). `useRealtime(types, handler)` es la versión sin refresco (p. ej. `notification.created` → `setUnread(data.unreadCount)`). La conexión es una sola por pestaña (`/eventos`).
- Estado con datos del SSR: la isla recibe los datos iniciales como props, los copia a `useState` y los reemplaza con el resultado de la action de lectura (crea la action en `features/<f>/actions.ts`).
- Varias islas del mismo detalle: una isla sin UI (`RequestLiveSync`) pide los datos y los reparte por evento de ventana (`useRequestState`), ver `features/requests/components/RequestLive.tsx`.
- Páginas solo-SSR (sin isla de datos): envuelve el contenido en `<div data-live-region="x">` y monta fuera `<LiveRegion client:load types={[...]} region="x" />` (`ui/live-region`); vuelve a pedir la página y reemplaza la región sin recargar (no actúa con un diálogo abierto ni escribiendo). Úsalo con listas simples; para pantallas con estado local (mapa, pestañas) usa `useLiveRefresh` + action.
- `layout/LiveIndicator` ("En vivo" / "Reconectando…", `data-testid="live-indicator"`, `data-status`) ya está en el topbar de `AppShell`; no lo repitas. `negotiation/usePolling.ts` → `useNegotiationLive(fn, { types, match })` (suma `NEGOTIATION_EVENT` para refrescar tras una acción propia).
- e2e: espera `[data-testid=live-indicator][data-status=live]`, marca `window.__noReload = true` y comprueba que sigue tras el evento (`tests/e2e/realtime.spec.ts`); timeouts ≤ 10 s, sin depender del respaldo.

## Paneles de datos (dashboards autenticados)
Guías modern-web-guidance aplicadas: `responsive-table`, `size-aware-styling` (container queries), `cross-document-transitions`, `skeleton` (sin movimiento con `prefers-reduced-motion`).
- **Layout** (`components/layout`): `AppShell` agrupa el menú por secciones y trae la **paleta de comandos** (`CommandMenu`, Ctrl/⌘+K: ir a sección + tema). En móvil el selector de tema vive en el pie del menú. Transiciones entre páginas con `@view-transition` (solo el contenido hace fundido; sidebar y topbar tienen `view-transition-name`; desactivadas con reduced-motion). `PageHeader`: `breadcrumbs` (en detalles), slot `meta` (pills bajo el título), slot `actions`.
- **KpiTile** (`domain`): `tone` (`petrol|money|warn|alert`: ícono tintado + filete lateral), `unit`, `delta={{value,direction,good}}`, `loading`. Agrupa con `KpiGrid` (2 col en móvil, 4 en `lg`; `cols={3}`). Máx. 4 por fila; el color expresa significado (money = dinero, warn = por cerrar, alert = incidencia), no decoración.
- **Table**: `<Table label="Solicitudes" stack sticky dense>`. `label` convierte el contenedor en una región enfocable con teclado (obligatoria para tablas que desbordan). `stack` = bajo 720 px **de ancho del contenedor** cada fila es una tarjeta: `<Td primary>` es el título de la tarjeta (sin etiqueta), `<Td label="Recojo">` muestra su etiqueta, `<Td actions>` es el pie con botones a todo el ancho; el `thead` desaparece (por eso cada `Td` necesita `label`). `sticky` fija la cabecera dentro de un scroll de máx. ~70 vh. `Th sort={'asc'|'desc'|null} onSort` = botón con `aria-sort` (el orden lo aplica el listado; para listas de servidor, vía URL).
- **EmptyState**: `icon={SearchX}` para "sin resultados", `compact` para tarjetas pequeñas. **SkeletonList** para carga de listas dentro de una `Card`.
- **DescriptionList** en vez de `<dl>` a mano; **QuickFilters** para filtros de un clic (estado) además de `FilterBar` (filtros finos). `CardDescription`/`CardFooter` para subtítulo y pie de tarjeta.
- `.lx-defer` (global.css): `content-visibility: auto` para ítems de listas largas fuera de pantalla (INP).

## Páginas públicas y autenticación (landing, login, registro, privacidad)
- **Layout:** `layouts/Public.astro` (`footer="compact"` en login/registro, `full` por defecto). Header con anclas a la landing (≥ lg), `ThemeToggle` (en móvil pasa al pie para que quepan "Ingresar" y "Crear cuenta"). Su `<style is:global>` activa `scroll-behavior: smooth`, `scroll-padding-top` y **transiciones entre documentos** (`@view-transition { navigation: auto }` solo con `prefers-reduced-motion: no-preference`; el header y el panel de auth tienen `view-transition-name`). Solo aplica entre páginas públicas: los dashboards no la activan.
- **Login/registro:** `features/auth/components/AuthShell.astro` (panel de marca ≥ lg + `AuthCard embedded` en la columna del formulario). `AuthCard` tiene la prop aditiva `embedded` (sin márgenes ni ancho propios).
- **Formularios de auth (patrón):** `noValidate` + validación propia en español; se valida **al salir del campo** (solo si tiene contenido) y al enviar; el error se limpia al corregir; tras un envío fallido el foco va al primer campo inválido; `aria-invalid` + `aria-describedby` apuntan a `${id}-error` / `${id}-hint` (`Field` ahora emite esos `id`). Autofill: correo `autoComplete="username"` + `spellCheck={false}` + `autoCapitalize="none"`; contraseña `current-password` (login) / `new-password` (registro); `enterKeyHint` por campo. Errores no atribuibles a un campo van en `Alert` (no toast) junto al botón.
- **`PasswordInput`:** prop opt-in `capsLockWarning` (aviso "Bloq Mayús está activado", enlazado con `aria-describedby`); desactiva autocorrección/mayúscula automática. El botón mostrar/ocultar cambia de etiqueta (sin `aria-pressed`).
- **RUC:** `lib/ruc.ts` (`isValidRuc`, `rucKind`) espeja `RucValidator` del backend (prefijo 10/15/17/20 + módulo 11); el input acepta solo dígitos y muestra contador `n/11` o "Dígito verificador correcto". La API sigue siendo la fuente de verdad (su error de campo se muestra bajo el RUC).
- **Landing (v2, nov 2026):** ritmo claro/oscuro con `Public.astro` como dueño de las utilidades de marketing (globales): `.mk-dark` (azul marino `#081F46`+petróleo+cian, solo para bloques oscuros fijos), `.mk-grid`, `.mk-eyebrow`, `.mk-display`/`.mk-h2` (escala fluida con `clamp`, `text-wrap: balance`), `.mk-num` (mono tabular), `.mk-card` (hover), `.mk-pulse`/`.mk-ping`, `.mk-in` (entrada del hero), `.mk-rv`/`-l`/`-r`/`-fade`/`.mk-draw` (entrada por scroll con `animation-timeline: view()`, solo `forwards` y dentro de `@supports` + `prefers-reduced-motion: no-preference`: fuera de rango el contenido queda visible) y contadores `data-count`/`data-dec` (IntersectionObserver; el HTML trae el valor final). Regla de grids: `@layer base { main .grid { grid-template-columns: minmax(0,1fr) } }` evita desbordes horizontales en móvil.
  Secciones en `features/landing/components/`: `Hero` (vídeo `bgv--hero-lead` + `AuctionLive` + KPIs con contador), `Sectors` (marquee CSS con botón Pausar y duplicado `aria-hidden`; son rubros, no clientes), `HowItWorks` (bento de 4 pasos con mocks fieles), `BeforeAfter`, `Corridor` + `CorridorExplorer` (isla `client:idle`: mapa SVG con rutas reales y cotizador; el cotizador cruza el borde inferior de la sección oscura con `lg:-mb-28`, la sección lleva `z-10` y la siguiente `pt-40`), `Audiences`, `Returns`, `Trust` (perfil, criterios, testimonios «Ilustrativo»), `Faq` (`.faq` en `index.astro`), `FinalCta` (vídeo).
  **Datos reales, no inventados:** `features/landing/corridor-data.ts` se genera desde `backend/src/main/resources/routing/routes.json` (11 ciudades, matriz de km y minutos, rutas OSRM simplificadas a 2 px; regenerar si cambia el JSON) y `features/landing/pricing.ts` replica `TariffCalculator` (combustible km×0.65, transporte km×t×0.80×mult., peajes ⌈km/120⌉×12, manipuleo 5 %, piso 75 %). Los mocks (`AuctionLive`, `HowItWorks`, `Audiences`) calculan sus montos con ellas; Lima→Juliaca es 1,248.5 km (no 1,010). Nombres de empresas, horas y testimonios son de muestra y se rotulan «ilustrativo».
  `AuctionLive`: el HTML estático ya trae 2 ofertas (sin JS o con movimiento reducido queda así); con movimiento permitido el script añade 3 ofertas, cierra y reinicia, solo a la vista y con la pestaña visible; `<script is:inline>` fija `data-live` antes de pintar.
- **Tests:** `tests/e2e/a11y-public.spec.ts` (axe, 4 páginas × claro/oscuro, cero violaciones) y `tests/e2e/public.spec.ts`.

## Marca OnCargo y vídeo de fondo (páginas públicas)
Producto = **OnCargo** (antes LOGYX; identificadores técnicos no cambian). Detalle de marca, tabla de vídeos y assets: `docs/design/DESIGN.md` § "Marca, logo y vídeo".
Guías modern-web-guidance aplicadas: `lazy-load-video-and-audio`, `optimize-image-priority`, `efficient-background-processing`, `dark-mode`, `motion`, `scroll-entry-exit-effects` (reveal por scroll), `forms`/`progress`/`size-aware-styling` (registro con avance), `carousel`/`motion` (marquee con pausa), `improve-text-layout-and-legibility` (`text-wrap: balance`).
- **Logo:** `components/layout/Logo.astro` (`dark`, `size`, `symbol`, `class`). Dos `<img>` WebP (texto oscuro/blanco) alternadas por `html[data-theme]`: sin parpadeo. Sobre fondo oscuro fijo (sidebar, hero, pie de vídeo) usa `<Logo dark />`. No inventes otra versión del logo.
- **`BgVideo.astro`** (`components/media`): vídeo decorativo + póster + velo + botón. Úsalo dentro de un contenedor `relative isolate` (el vídeo es `absolute inset-0 -z-10`, el botón es hermano absoluto):
  ```astro
  <section class="relative isolate overflow-hidden bg-chrome text-white">
    <BgVideo desktop="hero-corredor" mobile="hero-corredor-movil" poster="hero-corredor" posterMobile="hero-corredor-movil"
      width={1920} height={1080} priority control class="bgv--hero" label="vídeo de fondo del corredor" controlClass="right-4 top-4" />
    …contenido con text-white…
  </section>
  ```
  Props: `desktop`/`mobile` = nombres base en `/media/` (`.webm` + `.mp4`); `poster`/`posterMobile` (`.avif` + `.webp`); `priority` (solo el póster del hero: `fetchpriority="high"`; el resto `loading="lazy"`); `control` (botón); `breakpoint` (768; 1024 para paneles `lg:`); `position`; `class` = variante de velo (`bgv--hero`, `bgv--flat`, `bgv--panel` o la base).
- **Patrón (por qué):** (1) el LCP es el póster, una `<picture>` AVIF/WebP debajo del `<video>`, nunca el vídeo; (2) el `<video>` **no lleva `autoplay` ni `preload`**: un script único lo arranca tras `window.load`+idle, solo si el bloque está en pantalla (`IntersectionObserver`), y lo pausa fuera de pantalla y con la pestaña oculta; (3) `muted loop playsinline` + `disablepictureinpicture`; (4) `prefers-reduced-motion: reduce`, `Save-Data` y 2g → no se reproduce (queda el póster) pero el usuario puede pulsar Reproducir; (5) `<source media>`: móvil → variante vertical 540×720; sin variante móvil no hay fuentes, es decir 0 bytes de vídeo y el botón se oculta; (6) el vídeo aparece con fundido al evento `playing`; (7) `forced-colors` oculta el vídeo; (8) si no hay JS queda solo el póster.
- **Botón de control:** `aria-label` "Pausar/Reproducir <label>" (cambia con el estado), icono `lucide` oculto a lectores, tamaño 40 px, foco visible. Obligatorio mientras el vídeo se mueva más de 5 s (WCAG 2.2.2).
- **Contraste:** nunca pongas texto sobre el vídeo sin el velo `.bgv__scrim` (usa `color-mix` con `--color-chrome`, oscuro en ambos temas) y texto `text-white`/`text-white/85`. Tarjetas encima: `bg-chrome/50 backdrop-blur-sm` o `bg-surface`. axe no mide contraste sobre vídeo/imagen: calcúlalo con el fotograma más claro.
- **Preparar un vídeo nuevo** (uno a uno, `-threads 4`): `ffmpeg -i in.mp4 -an -vf scale=1920:1080 -c:v libvpx-vp9 -b:v 0 -crf 31 -row-mt 1 -cpu-used 2 -pix_fmt yuv420p -g 120 out.webm` y `-c:v libx264 -preset slow -crf 24 -pix_fmt yuv420p -movflags +faststart -g 120 out.mp4`; recorte vertical para paneles: `-vf crop=540:720:X:0`. Póster: un fotograma (`-frames:v 1`) → `avifenc -q 45 -s 6` y `magick -quality 78 out.webp`. Presupuesto: ≤ 2.5 MB por archivo de escritorio, ≤ 400 KB móvil. Quita siempre el audio y revisa marcas de agua en las esquinas.
