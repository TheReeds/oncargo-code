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
| `date-picker` | `DatePicker`, `DateRangePicker`, `DATE_PRESETS`, `parseISODate`, `toISODate` | valores `'YYYY-MM-DD'`; rango `{from,to}` con `''` vacío; atajos Hoy / Próximos 7 días / Este mes / Próximos 30 días; `min`/`max` |
| `number-input` | `NumberInput`, `MoneyInput` | `value` string crudo ("1850.5"), muestra `1,850.5`; Money: prefijo `S/`, 2 decimales; `suffix="kg"` |
| `form-controls` | `Checkbox`, `RadioGroup`, `RadioGroupItem`, `Switch`, `InlineLabel` | Radix; `onCheckedChange` / `onValueChange` |
| `confirm-dialog` | `confirm()`, `ConfirmDialog`, `ConfirmHost` | ver abajo |
| `toaster` | `toast`, `flash`, `Toaster`, default `Overlays` | `Overlays` ya está montado en `layouts/Base.astro` |
| `alert` | `Alert` | `tone`: info, success, warning, error; `title`, `action` |
| `dialog` / `sheet` | `Dialog` / `Sheet` | modal centrado con footer / panel lateral |
| `tabs` | `Tabs`, `TabsList`, `TabsTrigger`, `TabsContent` | Radix |
| `tooltip` | `Tooltip` | `<Tooltip content="..."><Button/></Tooltip>` |
| `dropdown-menu` | `DropdownMenu`, `…Trigger`, `…Content`, `…Item` (`destructive`), `…Label`, `…Separator` | menú de acciones |
| `pagination` | `Pagination` | `page` (base 1), `size`, `total`, `onPageChange` — directo de `meta` |
| `table` | `Table`, `Th`, `Td`, `Tr` | usar con `font-mono tabular-nums` en cifras |
| `password-input` | `PasswordInput` | `Input` con botón mostrar/ocultar; dentro de `Field`, `id={htmlFor}` (su botón se llama "Mostrar contraseña": en tests usa `getByLabel('Contraseña', { exact: true })`) |
| `filter-bar` | `FilterBar` | barra de filtros de listados: hijos = `Field`s (rejilla), `extra` = 2.ª fila, `sort`, `count`+`noun`, `active`+`onClear` ("Limpiar filtros"); `role="search"` |
| `timeline` | `Timeline`, tipo `TimelineItem` | pasos/paradas: `items=[{id,title,detail?,meta?,status:'done'\|'current'\|'pending'}]`, `orientation` horizontal (apila en móvil) o vertical, `label` obligatorio. Paradas de envío: `StopTimeline`/`stopsToItems` (features/shipments) |
| `auth-card` | `AuthCard` | tarjeta de login/registro: `title`, `subtitle`, `footer` (en `.astro`: `<Fragment slot="footer">`), `className` para el ancho (`max-w-md` default) |
| `star-rating` | `StarRating` | entrada 1–5 accesible (radiogroup, teclado) y `readOnly` con decimales |
| `file-dropzone` | `FileDropzone` | arrastrar/soltar + botón; `accept`, `maxBytes` (10 MB), error por archivo |
| `card`, `badge`, `skeleton`, `avatar`, `kbd` | `Card`/`CardHeader`/`CardTitle`/`CardBody`, `Badge`, `Skeleton`/`SkeletonRows`, `Avatar`, `Kbd` | |
Dominio (`components/domain`): `StatusPill`, `RouteLine`, `KpiTile`, `EmptyState`, `Countdown`, `TrustScore` (`variant="chip"` en listas | `"meter"` en detalle; 75 = sin calificaciones; `trustTone`), `StatusPill` (también incidencias OPEN/IN_REVIEW/RESOLVED/CLOSED y `kind="counter"`), `VerifiedBadge` (`variant="badge"` chip "Empresa verificada" | `"icon"` escudo con tooltip para listas densas; no renderiza si `verified` es falso). Formatos: `lib/format.ts`.

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
Campos que envía la API: `ruc`, `email`, `plate`, `amount`, `vehicleId` y los parámetros de listado. Registro (`RegisterForm`) y nueva solicitud (`RequestForm`) ya mapean `field` → control (nunca parsees el texto del mensaje). `error.message` directo sigue funcionando (el separador es invisible).

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
