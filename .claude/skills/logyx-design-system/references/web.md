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
| `copy-button` | `CopyButton` | copiar al portapapeles: `text`, `label` («Copiar enlace»); Async Clipboard con respaldo, confirmación visible que es también la región viva (`role="status"`, fuera del botón), cambia el ícono (no solo color) y no se deshabilita mientras avisa |
| `search-input` | `SearchInput` | buscador de texto dentro de `Field`: `value` (texto aplicado), `onSearch(texto)` con Enter (no mientras se compone con un IME) o la lupa; la X limpia y busca vacío. Úsalo con el filtro `q` en la URL (listados del operador) |
| `timeline` | `Timeline`, tipo `TimelineItem` | pasos/paradas: `items=[{id,title,detail?,meta?,status:'done'\|'current'\|'pending'}]`, `orientation` horizontal (apila en móvil) o vertical, `label` obligatorio. Paradas de envío: `StopTimeline`/`stopsToItems` (features/shipments) |
| `auth-card` | `AuthCard` | tarjeta de login/registro: `title`, `subtitle`, `footer` (en `.astro`: `<Fragment slot="footer">`), `className` para el ancho (`max-w-md` default) |
| `star-rating` | `StarRating` | entrada 1–5 accesible (radiogroup, teclado) y `readOnly` con decimales |
| `file-dropzone` | `FileDropzone` | arrastrar/soltar + botón; `accept`, `maxBytes` (10 MB), error por archivo |
| `card`, `badge`, `skeleton`, `avatar`, `kbd` | `Card`/`CardHeader`/`CardTitle`/`CardDescription`/`CardBody`/`CardFooter`, `Badge`, `Skeleton`/`SkeletonRows`/`SkeletonList`, `Avatar`, `Kbd` | |
Dominio (`components/domain`): `StatusPill`, `RouteLine`, `KpiTile`, `EmptyState`, `Countdown`, `TrustScore` (`variant="chip"` en listas | `"meter"` en detalle; 75 = sin calificaciones; `trustTone`), `StatusPill` (también incidencias OPEN/IN_REVIEW/RESOLVED/CLOSED y `kind="counter"`), `BidSeals` (sellos de una oferta para la PYME, ver «Ola 2 · zona T»), `VerifiedBadge` (= "Verificado por OnCargo" (`rucVerified`); `variant="badge"` chip | `"icon"` escudo con tooltip para listas densas; no renderiza si `verified` es falso; `rucValid` no da sello). `VerificationBanner` (estado `PENDING/REJECTED/SUSPENDED` con nota y a quién escribir, plazo «1 día hábil» y «Qué revisamos»; lo monta `AppShell`), `TermsGate` (V24: diálogo **no descartable** para aceptar la versión nueva de los Términos; Radix AlertDialog con `onEscapeKeyDown` prevenido, sin botón de cerrar; lo monta `AppShell` con `termsAccepted = false`. Es el único diálogo que no se cierra: no lo copies para otra cosa) y `VerificationBlock` / `reportFailure()` (bloqueo `LOGYX-ORG-003/004` dentro de un formulario o tras un botón). `StatusPill kind="verification"` (En revisión/Verificada/Rechazada/Suspendida) y estado `WITHDRAWN` (Retirada). `features/fleet/components/UnfitList` (vehículos no aptos con su motivo). Formatos: `lib/format.ts`.

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
- **Ocultar por rol — patrón `<Can>` (fuente única, Ola 1 zona D):** todo vive en `@/lib/permissions` (puro: `can(session, p)`, `roleLabel`, `deniedReason`, `PERMISSION_LABEL`, `WRITE_ROUTES`, `openRequestAccess(p)`) y `@/lib/permissions/react` (islas y SSR de la isla: `<Can>`, `useCan(p)`, `usePermissions()`, `useRole()`). `@/lib/use-can` queda solo como re-export.
  ```tsx
  import { Can } from '@/lib/permissions/react';
  <Can><Button onClick={accept}>Aceptar oferta</Button></Can>                       // oculta sin OPERATE (por omisión)
  <Can readOnly="No puedes publicar solicitudes."><RequestForm /></Can>            // y en su lugar el aviso con «Pedir acceso»
  <Can permission="MANAGE_USERS" fallback={<p>…</p>}>…</Can>                       // fallback propio
  ```
  En `.astro`: `can(Astro.locals.session, 'OPERATE')` (o `<Can>` sin `client:*`: render estático). **Do:** ocultar botones, formularios, menús y acciones en filas; un solo aviso de solo lectura por pantalla/panel (`readOnly` o `<ReadOnlyNotice>No puedes …</ReadOnlyNotice>`, que ahora trae el botón **«Pedir acceso»** → `RequestAccessDialog`). **Don't:** botón "deshabilitado" para el Lector, ocultar datos (precios, estados, chats) con `<Can>`, ni copiar la tabla de permisos en una feature. Páginas que solo sirven para escribir: agrégalas a `WRITE_ROUTES` (el middleware devuelve al listado con `?sinPermiso=<clave>` y `AppShell` muestra el aviso). El backend manda (`AUTH-007`/`AUTH-008`): `toast.error(error.message)` de un `AUTH-007` ya ofrece «Pedir acceso». Un chat de solo lectura reutiliza `ChatPanel readOnly`. Rol visible: `AppShell` (pie de la barra lateral «Cuenta PYME · Lector») y `UserMenu` (subtítulo del topbar, «Rol: …», «Mis avisos», «Pedir acceso» para el Lector).
- **Avisos por persona (V16):** `NotificationItem` pinta la marca **«Tuyo»** (`mine`); `/notificaciones` tiene Todas / No leídas / **Solo míos**; preferencias en `/cuenta/avisos` (`NotificationPreferences`: lista categoría × «En la app» / «Por correo» con `Switch` etiquetado, candado en «Cuenta y accesos», guardar solo lo cambiado).
- **Autoría:** `features/activity/components/ActivityTrail` (tarjeta «Actividad»: quién creó y quién hizo cada acción, `subject` REQUEST|BID|SHIPMENT|TRIP|INCIDENT, `initial` del SSR, `liveTypes`) y `AuthorLine` («Publicada por Ana R. (Operaciones)», para el `meta` del `PageHeader`). Solo muestra personas de tu empresa (la API ya lo filtra). Móntala con `client:idle` o `client:load`, **no** `client:visible`: una isla fuera de pantalla nunca hidrata y `hydrated()` de los e2e espera a todas.
- **`RoleChoice`** (`features/organization/components`): `RadioGroup` de tarjetas con el nombre y la descripción de cada rol (ADMIN, OPS, VIEWER; textos en `lib/permissions` `ROLE_DESCRIPTION`).
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
  Secciones en `features/landing/components/`: `Hero` (vídeo `bgv--hero-lead` + `AuctionLive` + KPIs con contador), `Sectors` (marquee CSS con botón Pausar y duplicado `aria-hidden`; son rubros, no clientes), `HowItWorks` (bento de 4 pasos con mocks fieles), `Corridor` + `CorridorExplorer` (isla `client:idle`: mapa SVG con rutas reales y cotizador; el cotizador cruza el borde inferior de la sección oscura con `lg:-mb-28`, la sección lleva `z-10` y la siguiente `pt-40`), `Audiences`, `Returns`, `Trust` (perfil de transportista ilustrativo y 4 criterios; sin testimonios desde 2026-11-19), `Faq` (`.faq` en `index.astro`), `FinalCta` (vídeo).
  **Datos reales, no inventados:** `features/landing/corridor-data.ts` se genera desde `backend/src/main/resources/routing/routes.json` (11 ciudades, matriz de km y minutos, rutas OSRM simplificadas a 2 px; regenerar si cambia el JSON) y `features/landing/pricing.ts` replica `TariffCalculator` (combustible km×0.65, transporte km×t×0.80×mult., peajes ⌈km/120⌉×12, manipuleo 5 %, piso 75 %). Los mocks (`AuctionLive`, `HowItWorks`, `Audiences`) calculan sus montos con ellas; Lima→Juliaca es 1,248.5 km (no 1,010). Nombres de empresas, horas y testimonios son de muestra y se rotulan «ilustrativo».
  `AuctionLive`: el HTML estático ya trae 2 ofertas (sin JS o con movimiento reducido queda así); con movimiento permitido el script añade 3 ofertas, cierra y reinicia, solo a la vista y con la pestaña visible; `<script is:inline>` fija `data-live` antes de pintar.
- **Páginas públicas nuevas (ola 1, agente E):** `/terminos` (borrador marcado «Borrador — pendiente de revisión legal», contenido en `features/legal/terms-content.ts`, versión desde `GET /public/legal`), `/precios` (comisión y IGV con un ejemplo real de `POST /public/pricing/quote`, cacheado 10 min) y `/contacto` (WhatsApp solo si hay `PUBLIC_WHATSAPP_NUMBER`, soporte, privacidad, horario, 11 ciudades y «Avísame cuando lleguen a mi ciudad» por `mailto`). Mismo patrón que privacidad: cabecera `.mk-dark`, tarjetas que solapan, índice lateral. Correos y horario en `lib/contact.ts` (`SUPPORT_EMAIL`=soporte@oncargo.tech, `PRIVACY_EMAIL`=privacidad@oncargo.tech); el pie de `Public.astro` enlaza Términos, Privacidad, Precios y Contacto, y el registro exige aceptar los términos (casilla `acceptTerms` + `termsVersion`).
- **Cotizador de la landing conectado a la API:** `CorridorExplorer` muestra al instante la estimación local y, con 400 ms de espera, la confirma con la acción pública `landing.quote` (`POST /public/pricing/quote`, con la IP del visitante en `X-Forwarded-For` para el límite por IP). Con respuesta, la etiqueta pasa de «Estimación» a «Cotización de referencia»; si la API responde 429 mantiene la estimación local y lo avisa. Muestra el IGV incluido (18 %) y «Publicar esta carga» lleva a `/registro?next=/pyme/solicitudes/nueva?origen=…&destino=…&peso=…&tipo=…` (el registro solo acepta esa ruta como `next`).
- **Tests:** `tests/e2e/a11y-public.spec.ts` (axe, 7 páginas × claro/oscuro, cero violaciones) y `tests/e2e/public.spec.ts`.

## SEO de la web pública (2026-11-19)
Fuente única: `features/landing/seo.ts` (`SITE_URL`, `INDEXABLE_PATHS`, `homeJsonLd()`, `breadcrumbJsonLd(nombre, ruta)`). Copy y largos: `docs/design/CONTENT.md` → «Web pública».
- **Indexables** = `INDEXABLE_PATHS` = `public/sitemap.xml` (`/`, `/precios`, `/contacto`, `/registro`, `/terminos`, `/privacidad`). Si agregas una página pública, súmala en **los dos** sitios (lo comprueba `public.spec.ts`). `Base.astro` les pone `<link rel="canonical">` absoluto a `https://oncargo.tech` (sin query: `/registro?tipo=…` canoniza a `/registro`), `hreflang="es-PE"`, `og:url` y el JSON-LD. **Todo lo demás** (paneles, `/login`, `/olvide`, `/restablecer`, `/dev/ui`) sale con `<meta name="robots" content="noindex">` sin tocar cada página; `/s/<token>` (`privateUrl`) sigue con `noindex, nofollow, noarchive` + `X-Robots-Tag` del middleware.
- **Metadatos por página:** `<Public title description jsonLd>`. `title` ≤ 60 caracteres contando ` · OnCargo`; `description` 110–160 caracteres; ambos únicos. OG/Twitter (`summary_large_image`) con `brand/og-oncargo.png` 1200×630 en URL absoluta del sitio (no del host que sirvió la petición).
- **JSON-LD:** home = `Organization` (logo `icon-512.png`, correo de soporte) + `WebSite` + `Service` (11 ciudades de `corridor-data.ts` como `areaServed`); internas = `BreadcrumbList`. Va en un `<script type="application/ld+json">` con `<` escapado; la CSP no lo afecta (no es script ejecutable). **No uses** `LocalBusiness` (exige dirección física visitable) ni `FAQPage` (Google ya no muestra ese resultado) ni inventes `aggregateRating`.
- **`public/robots.txt`:** permite todo salvo `/s/`, `/app/`, `/dev/`, `/exportar/`, `/documentos/`, `/eventos`, `/_actions/`, los paneles por rol, `/cuenta`, `/notificaciones`, `/incidencias`, `/sin-acceso`, `/logout`, `/restablecer`; apunta al sitemap. El middleware deja pasar `robots.txt` y `sitemap.xml` como estáticos (tienen punto).
- **Rendimiento (LCP = póster del hero):** póster `<picture>` con `fetchpriority="high"`; el `<video>` del hero lleva `scale: 1.01` (`.bgv--hero-lead .bgv__video` en `Hero.astro`): sin eso, en móviles de alta densidad su primer fotograma (pintado después de `load`) reemplazaba al póster como LCP y Lighthouse móvil marcaba el LCP en el vídeo. Con escala Chrome no lo toma como candidato (comprobado empíricamente, Chromium 1254; achicar el ancho del `<video>` no sirve porque con `object-fit: cover` el área cuenta recortada por el padre). Compruébalo con un `PerformanceObserver('largest-contentful-paint')` a DPR 1.75 y 3 si tocas `BgVideo`. En páginas públicas, `Overlays` (toasts) y `ThemeToggle` hidratan con `client:idle` (`Base` prop `overlays="idle"`, la pone `Public.astro`): sin JS de React en el camino del primer render. Los paneles siguen con `client:load`.
- **Medir:** Lighthouse sobre el build (`npm run build && PORT=4330 node dist/server/entry.mjs`, con `flock`), móvil por defecto. En local no hay compresión (producción usa `encode zstd gzip` en Caddy): compara siempre local con local.

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

## Solicitudes, direcciones y búsqueda (V17, Ola 1 zona A)
- **`ExportButton`** (`ui/export-button`): `<ExportButton href="/exportar/<recurso>?<filtros>" />`. Descarga con `fetch` y muestra el error de la API en un toast (no guarda un archivo con el mensaje). El proxy `pages/exportar/[recurso].ts` traduce los filtros de la URL de la página a la API.
- **Listado con filtros en la URL** (patrón de `RequestsTable` y `OperatorListing`): estado local + `go(next, page)` que navega con `window.location`; `QuickFilters` con `count` desde un endpoint de totales, `FilterBar` con `extra` (exportar) y `sort`, `Pagination` solo si `total > size`; la tabla atenuada y `aria-busy` mientras navega.
- **Sección opcional plegable**: `<details>` nativo dentro de un `Card`, `summary` sin encabezados (JAWS/TalkBack los pierden), chevron con `group-open:rotate-90`; ábrela por código si un campo interno falla.
- **Combobox de datos del usuario** (`Mis direcciones`): `Combobox` con `allowClear`, `hint` con la ciudad y opciones filtradas por contexto; al elegir, copia los campos al formulario; editar a mano esos campos suelta la selección.
- **Aviso bloqueante no modal** («Ya publicaste una igual hoy»): `div role="alert" tabIndex={-1}` con foco programático y tres acciones (continuar, ver la anterior, revisar); no es un diálogo.
- **Paleta Ctrl+K con búsqueda**: `Command.Dialog shouldFilter={false}`, entrada controlada, debounce de 200 ms con contador de secuencia para descartar respuestas viejas, grupos por tipo con el código en `font-mono`, estados «Buscando…», error y «No encontramos resultados».

## Oferta rápida, precio con IGV y flota (Ola 1 zona C, 2026-11-18)
- **`PriceBreakdown`** (`components/domain/PriceBreakdown`): `<PriceBreakdown breakdown={shipment.priceBreakdown} />`. Detalle plegable con `<details>` nativo (sin JS, `summary` sin encabezados): la PYME ve valor de venta + IGV; el transportista y el operador ven además la comisión con la tasa guardada, el IGV de la comisión y «Cobras neto» (los campos de comisión llegan nulos para la PYME y el conductor). `data-testid="price-breakdown"`.
- **«Cobras S/ X neto»**: `lib/pricing.ts` (`priceBreakdown(monto, tasa)`, `vatSplit`, `formatRate`) espeja `CommissionPolicy` con aritmética en céntimos (HALF_UP); ejemplo del dueño S/ 1,180 al 8 % → neto 1,085.60. La tasa sale de `features/marketplace/commission.ts` (`loadCommissionRate()`, una consulta por pestaña). El monto va en una caja `emerald-50` con `aria-live="polite"` y el desglose en un `<details>` («Ver cómo se calcula»).
- **Atajos de monto** (`features/marketplace/quick-bid.ts`): grupo `role="group"` de `Button` con `aria-pressed` (`Sugerido`, `−5 %`, `−10 %`, `Piso`); `shortcutAmount` nunca baja del piso (RN03). No existe «mejor oferta − paso»: los montos ajenos son secretos (RN04). El navegador recuerda último vehículo, nota y atajo (`recalled()/remember()`, `localStorage`, siempre en `try/catch`).
- **Oferta sin recargar**: `BidSheet` avisa con `onPlaced(bid, advance)`; la tabla marca la fila «Ofertada» en sitio y mueve el foco al siguiente `[data-bid-row]` (Radix devuelve el foco a un botón que ya no existe: hay que moverlo a mano con un `setTimeout` corto). «Enviar y ofertar la siguiente carga» deja la hoja abierta con la siguiente.
- **Agenda por fecha** (RN02): el selector de vehículo usa `actions.marketplace.eligibility({requestId})`; los ocupados quedan deshabilitados con «Ocupado · libre el 24 nov» (`data-testid="agenda-blocked"`), y `CAP-006` se muestra como `Alert tone="warning"` antes de enviar. `features/fleet/compatibility.ts` ya no bloquea por «en ruta» (solo mantenimiento).
- **Flota**: `FleetManager` ofrece Editar (`VehicleDialog` con `vehicle`, placa deshabilitada) y Archivar (`confirm` destructivo); sin «Marcar en ruta» (lo controla el envío). Los errores `FLT-004/006` se muestran en un `toast.error(splitActionError(error).message)`.
- **Duplicar viaje**: enlace `/transportista/viajes/nuevo?from=<id>` (botón «Duplicar» en la tabla y en el detalle); `PublishTripForm initial={…}` con un `Alert tone="info"` «Copia de un viaje anterior».
- **Autoría en Mis ofertas / detalle del viaje**: botón «Historial» → `Sheet` con `ActivityTrail subject="BID"`; el detalle del viaje monta `ActivityTrail subject="TRIP"`.
- **Seguimiento público y envíos (zona B):** el detalle del envío monta `StopContacts` (contacto con `tel:`, solo si la API lo manda),
  `ShareTrackingButton` (PYME) y `ActivityTrail`; la lista sigue el patrón «listado con filtros en la URL» (`features/shipments/filters.ts`).
  Una página pública con un secreto en la URL usa `<Base privateUrl>` (sin indexar, sin `Referer`, sin canónica) y nunca monta `AppShell`.


## Ola 2 zona P: demanda de la PYME (plantillas, preferidos, segunda oferta, bandeja, acciones masivas)
Sin componentes nuevos en `components/ui`: todo se arma con los existentes. Patrones nuevos que conviene repetir:
- **Selección múltiple en una tabla** (`RequestsTable`): `Checkbox` en la primera columna (`aria-label` «Seleccionar <ruta>» y «Seleccionar todas las solicitudes de la página», indeterminado si hay algunas), fila marcada con `bg-petrol-50/60` y una **barra de acciones** (`role="region"` con `aria-label`, `bg-petrol-50`) encima de la tabla con el contador `aria-live` y los botones; solo con `OPERATE` (`useCan`). La selección vive en la página visible: se pierde al cambiar de filtro o de página.
- **Acción masiva por ítem** (`BatchActions`): confirma o pide el dato (`ReasonDialog` con motivo común, `Dialog` con `DatePicker`, `confirm()`), llama a la action y muestra **`BatchResultDialog`**: título «Listo» o «Terminamos, con avisos», resumen «Canceladas: 3 solicitudes de 4» y un `Alert` `warning` con una línea por fallo (`código · ruta`: mensaje de la API). Al cerrar, recarga. Nunca prometas atomicidad en el texto.
- **Programación** (`ScheduleDialog`): `Select` de frecuencia, día y anticipación, `DatePicker` opcional de fin (`clearable`), `RadioGroup` «Al llegar la fecha» (publicar / dejar borrador), `Checkbox` de feriados y una línea `aria-live` con el primer recojo. Estados de la programación = `Badge` (`Publica sola` emerald, `Deja borrador` emerald, `Pausada` amber, `Pausada por fallos` red, `Sin programar`/`Terminada` neutral): `features/templates/labels.ts#scheduleState`.
- **Marcar a un transportista** (`CarrierMarkMenu` + `MarkBadge`): botón «Marcar» (ghost) con `DropdownMenu` (Marcar como preferido / Bloquear transportista [destructivo, con `confirm`] / Quitar marca). `Badge emerald` con estrella = Preferido, `Badge red` con `Ban` = Bloqueado. La oferta de un bloqueado no ofrece «Aceptar oferta» (texto rojo corto) y no cuenta como «Mejor precio».
- **Bandeja** (`OffersInbox`): tabla `stack` con `Countdown`, `TrustScore` chip y `money`; la acción primaria por fila es `Button variant="money"` «Aceptar la mejor» con `confirm()` que repite transportista, vehículo y monto.
- **Segunda oferta** (`BackupBidsPanel`): `Card` con lista; cada candidata muestra su posición («Siguiente mejor») y un solo botón según el estado (Pedir confirmación → «Esperando su respuesta» → Adjudicar carga [money]).
- Copy: «plantilla», «programar», «borrador», «segunda oferta», «preferido/bloqueado». No uses «cron», «job», «recurrente» ni «blacklist».

## Ola 2 · zona T: alertas, mejorar oferta, documentos y agenda de flota (2026-11-19)
- **`BidSeals`** (`components/domain/BidSeals`, sirve en SSR e islas): `<BidSeals improvedCount soatValidUntil inspectionValidUntil />` → chips «Mejorada» (`improvedCount > 0`), «SOAT vigente» y «Revisión técnica vigente» (solo si la fecha es ≥ hoy en Lima; la fecha va en el `title` y en texto `sr-only`). No renderiza nada sin sellos; nunca muestra montos. Hoy lo usa `BidComparison` (PYME). `today` es opcional (pruebas).
- **`lib/lima-date`**: `limaToday()`, `addDays`, `daysBetween`, `dayRange`, `mondayOf`, `weekdayShort` sobre fechas `YYYY-MM-DD` sin `Date` locales (mismo resultado en servidor y navegador). Úsalo en vez de `new Date()` para «hoy».
- **Búsquedas guardadas** (`features/marketplace/components/SavedSearches`): «Guardar búsqueda» (`Dialog` con nombre + resumen de filtros + vehículo opcional + enlace «Elige cómo te avisamos» → `/cuenta/avisos`) y «Mis búsquedas» (`Popover` con `aria-label`; aplicar = enlace con los filtros en la URL; pausar/reanudar con `Switch`; borrar con `confirm`). Escribir va bajo `<Can>`. `saved-searches.ts` traduce filtros ↔ API ↔ URL. Los avisos `NEW_LOAD_MATCH` abren `/transportista/marketplace?solicitud=<id>` (la hoja de oferta se abre sola).
- **Mejorar oferta** (`ImproveBidDialog`): `MoneyInput` (debe bajar) + `Combobox` de vehículos aptos (de `vehicle-eligibility`; bloqueados con «Bloqueado: SOAT vencido…») + historial de mejoras. `standingText()` suma `position` («Entre las 2 más bajas» / «Por debajo de la mediana» / «Por encima de la mediana»). En `BidSheet`, «Precio de esta ruta» (`marketplace.laneStats`; `null` = no se pinta nada) y las advertencias `LOGYX-FLT-007/008` como `Alert warning`.
- **Documentos de flota** (`features/fleet`): `FleetDocumentsSheet` (por vehículo o conductor: vigente por tipo con `VigencyBadge`, subir con `FileDropzone` + `DatePicker`, historial en `<details>`, descarga por el proxy `/flota/documentos/<id>`; subida multipart por `/flota/documentos/subir`), `ExpirySummary` (Alert con vencidos y por vencer ≤ 30 días arriba de Flota), `VehicleConfigDialog` (equipamiento + «Tipos de carga que aceptas»; solo la matriz RN02, `cargoTypesFor`), `documents.ts` (tipos, etiquetas, `expiryText`). `vehicleFit` ya respeta `acceptedCargoTypes`.
- **Agenda** (`FleetAgenda`, `/transportista/agenda`): tabla semántica (`<caption>`, `th scope`, columna de fila fija) dentro de una región enfocable con scroll horizontal propio; compromisos como barras con ícono **y** palabra (Envío / Oferta pendiente / Viaje), vencimientos como marcador, filas bloqueadas con su motivo, leyenda visible, hoy con `aria-current="date"`. Estado en la URL (`?desde=`, `?dias=7|14`). `AssignSheet` (sugerencias ordenadas, no asignables deshabilitados con motivo; motivo con `ReasonDialog` si el envío ya se recogió) y «Asignar en lote» (una llamada por envío, resumen por fila).
- **Operador**: `ParametersEditor` acepta `kind: 'FLAG'` (`0`/`1`) con `Select` Activado/Desactivado.

## Ola 2 zona X (2026-11-19)
- `StatusPill kind="adjustment"` (Por responder, Aceptado, Rechazado, Retirado, «Lo decide OnCargo» = `ESCALATED`) y `kind="severity"` (Baja,
  Media, Alta, Crítica).
- `PriceBreakdown totalLabel` («Precio final» cuando incluye ajustes aceptados). No lo metas dentro de un `<dl>`: es un `<details>` (axe
  `definition-list`).
- Montos con signo: `signedMoney` de `features/shipments/components/AdjustmentsPanel` («− S/ 50.00» para descuentos).
- Patrones: elegir 1 de 5 visibles → `RadioGroup` con hint por opción (tipo de ajuste); confirmar dinero → `confirm()` con el monto; rechazar →
  `ReasonDialog`; una acción por fila en bandejas (`Td actions`). Señales de fraude: `Badge` rojo (Alta) / ámbar (Media), nunca texto de sanción.

## Ola 2 zona F: reportes y cobros (2026-11-19)
- `StatusPill kind="settlement"` (Falta comprobante, Facturado, Pagado, Cobrado, En disputa) y `kind="commission"` (Por cobrar, Cobrada, No aplica).
- **Gráfico de barras de una serie** (`features/reports/components/MonthlyBars`): marcas finas (máx. 40 px) con el extremo redondeado, una tinta (`bg-petrol-700`), etiqueta directa solo con ≤ 8 barras, tooltip al pasar el cursor, `role="img"` con todos los valores y una tabla con los mismos datos al lado. Si necesitas más de una serie, carga la skill `dataviz` y valida la paleta.
- **Página imprimible**: dentro de `AppShell`, con `@media print` (global en `styles/global.css`: oculta `#sidebar`, `#app-topbar`, `.no-print`; tokens claros siempre) y `@page` por página; botón con `window.print()`. En papel todo en negro sobre blanco y con bordes (no dependas del color). Ejemplo: `pages/reportes/liquidacion/[periodo].astro`.
- **Tabla con totales**: `<tfoot>` con `Tr`/`Td` (`ReportView#GroupTable`); en móvil (`stack`) la fila de total conserva sus etiquetas.
- Dinero siempre `.money` o `font-mono tabular-nums`; precios en tablas con `whitespace-nowrap`. Fechas con hora (`deliveredAt`) → `limaDateOf` antes de `formatDate`.
- Copiar CCI o número de cuenta: `CopyButton` con etiqueta específica («Copiar CCI»).
