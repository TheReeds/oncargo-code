---
name: logyx-design-system
description: Sistema de diseño de LOGYX (tokens petrol/emerald/ink/steel, Geist, componentes compartidos, reglas de copy en español peruano). Úsala SIEMPRE antes de crear o modificar cualquier UI web (Astro/React en web/) o móvil (Android Compose en android/) de LOGYX, incluidos formularios, selects, fechas, diálogos de confirmación, toasts, tablas, estados vacíos y textos de interfaz.
---

# LOGYX Design System

Fuente de verdad visual y de contenido del monorepo. Si vas a escribir UI, léela completa y luego abre la referencia de tu plataforma. Funciona para cualquier agente (no depende de herramientas de Claude).

- Web (Astro + React + Tailwind 4 + componentes estilo shadcn): `references/web.md`
- Android (Compose + Material 3): `references/android.md`
- Detalle de producto y pantallas: `docs/design/DESIGN.md` · Voz y microcopy completa: `docs/design/CONTENT.md`
- Catálogo visual vivo (web, solo dev): `http://localhost:4321/dev/ui` (`cd web && npm run dev`)

## Reglas no negociables
1. **Reutiliza, no reinventes.** Si existe un componente compartido (ver tabla), úsalo. Si falta uno, agrégalo a `web/src/components/ui` (o al módulo de UI de Android), documéntalo en la referencia y en `/dev/ui`; nunca crees una versión local en una feature.
2. **Nada de controles nativos sin diseño** en web: ni `<select>`, ni `<input type="date">`, ni `alert()`/`confirm()` del navegador. Usa `Select`/`Combobox`, `DatePicker`/`DateRangePicker`, `toast` y `confirm()`.
3. **Solo tokens.** Nada de hex sueltos ni colores arbitrarios de Tailwind (`blue-500`…). Radio: 10px tarjetas, 8px controles, pill en chips.
4. **Todo texto en español peruano**, tuteo, verbo + objeto en botones, errores = qué pasó + qué hacer (ver abajo).
5. **Accesible por defecto:** label en cada control (`Field`), errores con `role="alert"`, foco visible, teclado completo, contraste AA (usa `ink-500`, `emerald-700`, `amber-700` para texto pequeño).
6. **Estados siempre:** normal, hover, foco, deshabilitado, inválido, cargando, vacío, error.

## Principios (de DESIGN.md)
70% Modern Industrial Tech + 30% Friendly Mobility. **Web = negocio** (estructurada: tablas, filtros, paneles, comparación, mapa). **Móvil = transportista/conductor** (visual y rápido, acciones grandes). Cada pantalla ayuda a *encontrar, contratar o completar un transporte*; nada decorativo. **Dinero y oportunidad = emerald**; subasta por cerrar/pendiente = amber; error/incidencia = red.

## Tokens
| Token | Hex | Uso |
|---|---|---|
| ink-900 / 700 / 500 / 400 | #0E1726 / #334155 / #5B6B7F (web y Android) / #94A3B8 | Texto principal / secundario / terciario (AA ≥4.5 sobre blanco, steel-100 y petrol-50) / bordes fuertes |
| steel-100 / 200 | #EEF2F6 / #E2E8F0 | Fondo de app / bordes y divisores |
| surface | #FFFFFF | Tarjetas, tablas, controles |
| petrol-700 / 800 / 50 | #0B4F6C / #093F56 / #E6F1F5 | Primario (botón, links, selección, ruta) / hover / fondo selección |
| emerald-500 / 700 / 50 | #10B981 / #047857 / #ECFDF5 | Acento dinero y éxito / texto (AA) / chip positivo |
| amber-500 / 700 / 50 | #F59E0B / #B45309 / #FFFBEB | Por cerrar, pendiente |
| red-600 / 50 | #DC2626 / #FEF2F2 | Error, rechazada, incidencia |
Android usa los mismos valores (ver `references/android.md`). Modo oscuro: pendiente.

## Tipografía
Geist Sans (UI) y Geist Mono (cifras técnicas: placas, km, toneladas, IDs, montos en tablas), números tabulares en precios/tablas. Escala web: página 30/semibold · sección 20/semibold · tarjeta 15/semibold · cuerpo 14 · etiqueta 13/medium · ayuda 12 `ink-500` · encabezado de tabla 12/semibold mayúsculas. Dinero = clase `.money` (mono, emerald-700).

## Reglas de copy (resumen de CONTENT.md)
- Tú / frases neutras; nunca "usted". Sin emojis, sin "!" salvo celebraciones.
- Botones: **verbo + objeto** ("Publicar solicitud", "Enviar oferta"); cancelar un diálogo = "Volver"; en curso: "Publicando…".
- Errores: hecho + siguiente paso ("No pudimos publicar la solicitud. Revisa tu conexión e inténtalo de nuevo."). Validación de campo: "Ingresa la dirección de recojo." Mensajes de la API ya vienen en español: muéstralos tal cual.
- Éxito (toast): "Oferta enviada", "Solicitud publicada". Vacíos: título + frase + acción.
- Destructivo: pregunta con objeto + consecuencia + "No se puede deshacer"; botón rojo con el verbo.
- Formatos: `S/ 1,850.00` · `28 nov · 08:00` · `2.5 t` · `1,010 km` · `Lima → Juliaca` (siempre `lib/format.ts`).
- Glosario: carga, solicitud, oferta, subasta, envío, parada, transportista, conductor, PYME. NO: pedido, chofer, camionero, cliente, puja, tracking, dashboard, bid, marketplace en UI.

## Cómo elegir componente (web)
| Necesidad | Componente |
|---|---|
| Acción principal / secundaria / peligrosa | `Button` (`default`, `money`, `outline`, `ghost`, `destructive`, `destructive-solid`) con `loading` |
| Texto corto / largo | `Input` / `Textarea` dentro de `Field` |
| Elegir 1 de ≤ 8 opciones | `Select` |
| Elegir 1 de muchas, con búsqueda (ciudades, vehículos) | `Combobox` (`allowClear` en filtros) |
| Elegir varias | `MultiSelect` (chips) · pocas opciones visibles: `Checkbox` |
| Elegir 1 entre 2–4 visibles | `RadioGroup` |
| Activar/desactivar ajuste | `Switch` |
| Monto en soles | `MoneyInput` · número con unidad (kg, m³): `NumberInput` |
| Una fecha / un rango con atajos | `DatePicker` / `DateRangePicker` |
| Confirmar acción (aceptar, cancelar, eliminar) | `await confirm({...})` |
| Feedback tras una acción | `toast.success/error/info` (o `flash()` si vas a redirigir) |
| Mensaje persistente en la página | `Alert` (`info/success/warning/error`) |
| Formulario corto modal | `Dialog` · panel lateral (ofertar, detalle) `Sheet` |
| Acciones secundarias de una fila | `DropdownMenu` · ayuda breve: `Tooltip` |
| Vistas alternas en una página | `Tabs` |
| Listados largos | `Table` + `Pagination` · cargando: `Skeleton`/`SkeletonRows` · sin datos: `EmptyState` |
| Estado de entidad / ruta / KPI / cuenta regresiva | `StatusPill` / `RouteLine` / `KpiTile` / `Countdown` |
| Identidad de empresa o persona | `Avatar` |
| Contraseña | `PasswordInput` (mostrar/ocultar) |
| Barra de filtros de un listado | `FilterBar` |
| Paradas / hitos de un proceso | `Timeline` (`StopTimeline` para envíos) |
| RUC verificado | `VerifiedBadge` (`variant="icon"` en listas densas) |
| Pantalla de login/registro | `AuthCard` |

## Flujo de trabajo para agentes
1. Lee esta skill + la referencia de la plataforma. 2. Revisa `/dev/ui` (web) para ver qué existe. 3. Construye con componentes compartidos y copy de CONTENT.md. 4. Verifica: web `cd web && npm run check && npm run build`; Android `./gradlew assembleDebug`. 5. Actualiza `docs/STATUS.md` si cambió el estado de una pantalla.
