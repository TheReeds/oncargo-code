# OnCargo (antes LOGYX) — Sistema de diseño

> **Marca:** el producto se llama **OnCargo** desde nov 2026. Los identificadores técnicos (paquetes `pe.logyx`, códigos `LOGYX-XXX`, nombres de BD, cuentas demo `@logyx.pe`, `localStorage logyx-theme`) conservan el prefijo histórico.

Dirección: **70% Modern Industrial Tech + 30% Friendly Mobility.**
Combinamos la *presentación comercial* de un marketplace (Uber Freight), la *organización densa* de un
SaaS logístico (sennder) y la *rapidez operativa* mobile-first (Flock Freight).

- **Web = negocio.** Estructurada: tablas, filtros, paneles laterales, comparación, tracking con mapa.
- **Móvil = transportista y conductor.** Visual y rápida: tarjetas de carga ricas, precio destacado,
  mapa, acciones grandes. Aquí podemos ser más originales.

> **Skill de diseño (agentes y humanos):** `.claude/skills/logyx-design-system/SKILL.md` resume estos tokens y reglas y trae el catálogo de componentes (web: `/dev/ui`). Voz y microcopy: `docs/design/CONTENT.md`.

Principio rector: **cada módulo ayuda a encontrar, contratar o completar un transporte.** Nada de gráficos
financieros decorativos; cada dato en una tarjeta tiene propósito (cuánto gano, qué llevo, de dónde a dónde,
cuándo, qué vehículo necesito, con quién trato).

## Tokens
| Token | Hex | Uso |
|---|---|---|
| `ink-900` (carbón) | `#0E1726` | Texto principal, header oscuro de dashboard |
| `ink-700` | `#334155` | Texto secundario |
| `ink-400` | `#94A3B8` | Texto terciario, bordes fuertes |
| `steel-100` | `#EEF2F6` | Fondo de app (gris frío) |
| `steel-200` | `#E2E8F0` | Bordes, divisores |
| `surface` | `#FFFFFF` | Tarjetas, tablas |
| `petrol-700` (primario) | `#0B4F6C` | Botón primario, links, selección, ruta en mapa |
| `petrol-50` | `#E6F1F5` | Fondos de selección/chips |
| `emerald-500` (acento) | `#10B981` | **Dinero y oportunidad**: precios, "Ofertar", éxito, entregado |
| `emerald-50` | `#ECFDF5` | Fondo de chip "Compatible", "Verificada" |
| `amber-500` | `#F59E0B` | Subasta por cerrar, pendiente |
| `red-600` | `#DC2626` | Incidencia, error, rechazada |

Modo oscuro (web, implementado): `<html data-theme="dark">` redefine los mismos tokens (los componentes no cambian). Los rellenos sólidos (botones, chips activos) pasan a colores claros con texto oscuro `on-solid`. Contrastes calculados (WCAG, relación de luminancia):

| Token (oscuro) | Hex | vs fondo `#0B1220` | vs superficie `#111A2B` | vs petrol-50 `#12303F` |
|---|---|---|---|---|
| `steel-100` (fondo) / `surface` / `steel-200` (borde) / `petrol-50` | `#0B1220` / `#111A2B` / `#25324A` / `#12303F` | — | — | — |
| `ink-900` (texto) | `#E8EEF6` | 16.0 | 14.9 | 11.8 |
| `ink-700` | `#C0CBD9` | 11.4 | 10.6 | 8.4 |
| `ink-500` (terciario) | `#94A3B8` | 7.3 | 6.8 | 5.4 |
| `ink-400` (decorativo/bordes fuertes, no texto) | `#6B7C93` | 4.4 | 4.1 | 3.2 |
| `petrol-700` (primario aclarado) | `#38A3C9` | 6.5 | 6.0 | 4.8 |
| `petrol-800` (hover) | `#7CC4DE` | 9.6 | 9.0 | 7.1 |
| `emerald-700` (dinero/éxito) / `emerald-500` | `#34D399` / `#10B981` | 9.7 | 9.1 | 7.2 |
| `amber-700` / `amber-500` | `#FBBF24` / `#F59E0B` | 11.2 | 10.4 | 8.3 |
| `red-600` / `red-700` | `#F87171` / `#FCA5A5` | 6.8 / 9.9 | 6.3 / 9.2 | 5.0 / 7.3 |

Fondos tintados oscuros: `emerald-50 #0F2A26`, `amber-50 #2A2210`, `red-50 #2D1518` (texto de su color ≥5.3:1). Texto `on-solid #07121C` sobre `petrol-700` 6.5, `emerald-700` 9.8, `red-600` 6.8. Tokens nuevos de ambos temas: `chrome` (sidebar/tooltips), `on-solid`, `scrim`. Mapas: estilo OpenFreeMap *dark* y ruta `#38A3C9`. El tema se elige con el selector Claro/Oscuro/Sistema (`localStorage logyx-theme`).

**Tipografía:** *Geist Sans* (UI) y *Geist Mono* (cifras técnicas: placas, km, toneladas, IDs, montos en tabla).
Ambas SIL OFL. Números tabulares (`font-variant-numeric: tabular-nums`) en tablas y precios.
**Iconos:** Lucide (web) / Material Symbols Rounded (Android). **Radio:** 10px tarjetas, 8px inputs, pill en chips.
**Mapas:** MapLibre + OpenFreeMap estilo *positron* (claro, discreto) o *dark* en modo oscuro. Rutas en `petrol-700`, origen ○, destino ■.

## Marca, logo y vídeo (web pública)
**Logo.** Fuente: `media/logos/` (sin versionar). Derivados optimizados en `web/public/brand/` (WebP recortado al contenido, 248 px y 496 px para 1x/2x) y `web/public/` (`favicon.ico` 16/32/48, `favicon-16|32.png`, `apple-touch-icon.png` 180, `icon-192|512.png`, `site.webmanifest`, `brand/og-oncargo.png` 1200×630 para redes).
- Componente único: `components/layout/Logo.astro` (props `dark`, `size` sm|md|lg, `symbol`, `class`). Pinta **dos imágenes** (texto oscuro / texto blanco) y CSS alterna por `html[data-theme]` (lo fija el script anti-parpadeo de `Base.astro`): sin parpadeo ni JS. `dark` = fondo oscuro fijo en ambos temas (sidebar, hero) → siempre la variante blanca.
- Uso: altura mínima 24 px; espacio libre alrededor ≥ ½ de la altura; no recolorear, no estirar, no ponerlo sobre fotos sin velo. Cabecera pública 32 px, sidebar de la app según `AppShell`. Símbolo solo en espacios estrechos.
- Colores de marca (del logo): azul noche del texto ≈ `ink-900`/`chrome`, cian del pin `#00BFE9` (solo en el logo; la UI sigue con petrol/emerald).
- `<title>` = `<Página> · OnCargo`; meta description única por página (≤ 160 caracteres); OG/Twitter y `theme-color` en `Base.astro` (prop `ogImage`).

**Vídeo de fondo** (solo páginas públicas; nunca en dashboards). Componente `components/media/BgVideo.astro`; patrón completo en `references/web.md` de la skill.
| Sitio | Vídeo | Por qué |
|---|---|---|
| Hero de la landing | `fondo-mapa-peru` (mapa del corredor con la ruta Lima → Juliaca dibujándose) | Es el producto en una imagen, muy oscuro y de poco movimiento: garantiza contraste del titular y de las tarjetas |
| Login | `gemini_…6ec508b2` (camión de noche con ruta digital) | Transmite seguimiento; el encuadre vertical centrado en el camión cabe en el panel de 380 px |
| Registro | `gemini_…3a7ec79a` (almacén, operario con tableta) | Habla a la PYME que registra su empresa |
| Cierre de la landing | `gemini_…570a7486` (cabina, carretera andina) | Cierra con la perspectiva del conductor y del transportista |
Ninguno trae marca de agua visible (revisadas las 4 esquinas en fotogramas a tamaño completo). Los Gemini traen audio: **siempre se eliminan** (`-an`).
- **Reglas:** sin audio; `muted loop playsinline`; póster = candidato a LCP (`<picture>` AVIF/WebP, `fetchpriority="high"` solo en el hero); el vídeo no se descarga hasta que la página cargó y el bloque está a la vista; pausa con `prefers-reduced-motion`, `Save-Data`, red 2g, fuera de pantalla y pestaña oculta; botón Pausar/Reproducir accesible (WCAG 2.2.2) que gana a la preferencia automática; en móvil, login/registro/cierre muestran solo póster o nada.
- **Contraste:** el texto sobre vídeo siempre lleva el velo `.bgv__scrim` (degradado con `chrome` ≥ 58 % bajo el texto; variantes `bgv--hero`, `bgv--flat`, `bgv--panel`), texto blanco/`white/85` mínimo y tarjetas con `bg-chrome/50`. Verifica en ambos temas: el vídeo es igual en claro y oscuro.
- **Assets** (`web/public/media/`, sin audio, WebM VP9 + MP4 H.264 `+faststart`, GOP 120): `hero-corredor` (1920×1080) y `hero-corredor-movil` (540×720), `login-camion` y `registro-almacen` (540×720), `cierre-cabina` (1280×720), más póster `.avif`/`.webp` de cada uno. Re-generar con ffmpeg `-threads 4`, uno a la vez (máquina de 15 GB): `-c:v libvpx-vp9 -b:v 0 -crf 30..32 -row-mt 1` y `-c:v libx264 -preset slow -crf 24 -movflags +faststart`.

## Web pública v2 (landing, login, registro, privacidad)
Objetivo: densidad y ritmo, sin huecos ni secciones planas. Detalle de componentes y clases `.mk-*` en `references/web.md` de la skill.
- **Ritmo de fondos:** hero oscuro con vídeo → franja de sectores (superficie) → bento «cómo funciona» (`steel-100`) → antes/después (superficie) → corredor oscuro (`.mk-dark`) con el cotizador cruzando el borde → roles (`steel-100`) → retornos (superficie) → confianza (`steel-100`) → FAQ (superficie) → cierre con vídeo. Padding vertical de sección 48/64 px (`py-12 lg:py-16`), nunca 160 px; ancho de contenedor 1280 px; lectura 60–70 caracteres (`mk-lead` 62ch).
- **Tipografía:** titulares `mk-display` (hasta 80 px en el hero) y `mk-h2` (hasta 48 px), peso 650, tracking −0.025em, `text-wrap: balance`; eyebrow en mayúsculas 12 px con barra emerald; cifras en `mk-num` (Geist Mono, tabulares).
- **Prueba de producto:** subasta en vivo (20 min o 5 ofertas, piso 75 %), mapa con rutas reales de `routes.json` y cotizador con las reglas del `TariffCalculator`; todo lo que es de muestra va rotulado «ilustrativo».
- **Login y registro:** pantalla partida; panel de marca a sangre con vídeo, beneficios por rol, cifras reales, testimonio ilustrativo y sellos de confianza; en < lg, banda de marca con póster y formulario que la solapa. Registro con indicador de avance (3 pasos derivados de la validez de los campos) sin ocultar campos.
- **Movimiento:** entrada por scroll con `animation-timeline: view()`, contadores y hover de tarjetas; todo bajo `prefers-reduced-motion: no-preference`.
- **Páginas legales y comerciales (ola 1, nov 2026):** `/terminos`, `/precios` y `/contacto` siguen el molde de `/privacidad` (cabecera `.mk-dark`, tarjetas de resumen que la solapan, índice lateral con anclas, ancho de lectura 68 ch). `/terminos` es un **borrador** y se rotula siempre «Borrador — pendiente de revisión legal» (insignia ámbar en la cabecera + `Alert` warning sobre el texto) con la versión vigente en monoespaciada. `/precios` explica quién paga (PYME sin costo, comisión al transportista, IGV incluido) y muestra un ejemplo con cifras reales de la API (dinero en `.money`). El pie completo enlaza Términos, Privacidad, Precios y Contacto; el pie compacto (login/registro) enlaza Términos y Privacidad. El registro suma una segunda casilla («Acepto los términos y condiciones…») junto a la de privacidad, ambas obligatorias.
- **Cotizador de la landing:** estimación inmediata + confirmación de la API; etiqueta «Estimación» (ámbar) → «Cotización de referencia» (verde); línea «Incluye IGV (18 %)»; CTA «Publicar esta carga» que conserva ruta, peso y tipo.

## Patrones (componentes con nombre — úsalos igual en web y móvil)
- **RouteLine** — `Lima ──── 1,010 km ────▶ Juliaca` con fechas debajo de cada ciudad.
- **LoadCard** (móvil, marketplace) — precio grande en emerald + "precio referencial"; RouteLine; chips
  (`Carga general`, `3.2 t`, `Camión 5 t`, `Compatible`); empresa + sello "Verificado por OnCargo";
  contador de subasta ("cierra en 12 min", amber < 5 min); CTA "Ver carga y ofertar".
- **LoadRow** (web, marketplace tabla) — columnas: Recojo (ciudad, dirección, fecha) · distancia · Entrega ·
  Carga/peso · Vehículo mínimo · Ofertas (n/5) · Precio sugerido · botón "Ofertar".
- **BidComparison** (web PYME) — tabla/tarjetas de ofertas: transportista + verificación, monto,
  diferencia vs sugerido (%), vehículo, nota, botón "Aceptar". Mejor precio resaltado.
- **StatusPill** — PUBLISHED=Publicada (petrol), MATCHED=Adjudicada (emerald), CANCELLED=Cancelada (gris),
  PENDING=Por recoger (amber), IN_TRANSIT=En tránsito (petrol), DELIVERED=Entregado (emerald).
- **StopTimeline** — paradas verticales (móvil) u horizontales (web) con estado y hora.
- **TrackingSplit** (web) — lista de envíos a la izquierda, mapa a la derecha, tarjeta inferior con
  timeline (referencia: tracking split view).
- **KpiTile** — número grande + etiqueta + contexto ("3 por recoger hoy"). Máx. 4 por fila.
- **EmptyState** — ilustración simple de camión + acción siguiente.
- **VerificationBanner / VerificationBlock** (RN01) — franja bajo el topbar con el estado de verificación de la cuenta (`PENDING` ámbar, `REJECTED`/`SUSPENDED` rojo), qué puede y qué no puede hacer, la nota del operador y a quién escribir; el bloqueo `ORG-003/004` al actuar se explica dentro del formulario, no en un toast. Sello **"Verificado por OnCargo"** (`VerifiedBadge`) = revisión del equipo, no el dígito verificador del RUC.
- **ReadOnlyNotice / `requiresOperate`** (móvil, rol Lector) — franja de información bajo la barra superior ("Tu rol Lector solo permite consultar…") y botones de escritura deshabilitados; `VerificationBanner` también existe en Android (bajo la barra del inicio de PYME y transportista).
- **ReasonDialog** — diálogo para acciones que piden motivo (cancelar envío/solicitud, rechazar, suspender, relevar conductor): textarea con contador, obligatorio u opcional, error en línea y botón rojo cuando es destructivo.
- **Roles y solo lectura** — lo que el rol no puede hacer **no se muestra** (no se deshabilita): `useCan`/`can()` leen `UserInfo.permissions` (calculado del JWT), y donde faltan acciones aparece una línea discreta "Tu rol Lector solo permite consultar…" (`ReadOnlyNotice`). Las secciones que el rol no tiene (Usuarios, Operador, otro tipo de cuenta) responden una página 403 "No tienes acceso a esta sección" con enlace al panel, nunca una redirección muda.
- **Contraseñas** — formularios con `autocomplete` correcto (`new-password` + repetir), requisitos visibles que pasan a verde al cumplirse, confirmación idéntica en "Olvidé mi contraseña" (no revela si el correo existe), estado claro para enlace vencido con "Pedir otro enlace". Un enlace con token nunca queda en la barra de direcciones ni envía `Referer`.
- **Vehículos no aptos** — en la hoja de oferta y en el cambio de vehículo, los vehículos que no pueden llevar la carga (tipo, kg, m³) quedan deshabilitados y la lista "No disponibles para esta carga" dice por qué.

## Pantallas por rol
### Web — PYME (Negocio)
1. **Panel**: KPIs (solicitudes activas, ofertas por revisar, envíos en tránsito, entregados del mes),
   "Requiere tu atención" (subastas cerradas con ofertas sin elegir), envíos en curso con mini-mapa.
2. **Solicitudes**: tabla filtrable (estado, ruta, fecha) + botón "Nueva solicitud".
3. **Nueva solicitud**: formulario en 2 columnas con **cotización en vivo** (`/pricing/calculate`) en panel lateral
   (desglose: combustible, transporte, peajes, manipuleo, sugerido, piso).
4. **Detalle de solicitud**: datos + subasta (cuenta regresiva, n/5) + **BidComparison** + aceptar.
5. **Envíos / Tracking**: **TrackingSplit**.
### Web — Transportista
1. **Marketplace**: tabla densa (LoadRow) con filtros (origen, destino, fecha, peso máx = mi vehículo) +
   vista tarjetas; panel lateral para ofertar (elige vehículo compatible, monto ≥ piso).
2. **Mis ofertas**, 3. **Flota** (vehículos + conductores), 4. **Envíos** (asignar conductor).
### Web — Operador (parámetros, búsqueda y conversaciones)
- **Parámetros del sistema** (`/operador/parametros`): una tarjeta por parámetro (comisión en %, SLA de asignación, umbrales de incidencias) con el valor grande en monoespaciada, insignia «Versión n» o «Valor por omisión», el motivo del último cambio y «Cambiar» (diálogo con el campo de valor y el motivo obligatorio). Debajo, historial de solo lectura (parámetro, antes → después, motivo, quién y cuándo).
- **Búsqueda** (`SearchInput` dentro de `FilterBar`): «Buscar» ocupa dos columnas de la barra de filtros de solicitudes, envíos y verificación; Enter o la lupa aplican, la X limpia; el texto vive en la URL (`q`).
- **Conversaciones** (`OperatorChat`, detalle de solicitud y de envío): tarjeta de solo consulta que no carga nada hasta «Ver conversaciones»; pide un motivo opcional y avisa que queda auditado. Cada hilo abre un `Sheet` con burbujas y sin cuadro de escritura.

### Móvil — Transportista
Home "Tu actividad" (cargas sugeridas · ofertas enviadas · viaje activo) → LoadCard feed con filtros rápidos
(Cerca de mí, Mi vehículo, Mejor pago) → detalle con mapa + ofertar (bottom sheet) → mis ofertas → envíos.
### Móvil — Conductor (action-first)
Un viaje activo a pantalla completa: mapa, siguiente parada, **un botón grande** ("Llegué al recojo" →
"Carga recogida" → "Llegué a destino" → "Entregado"), StopTimeline. Textos grandes, alto contraste.
### Móvil — PYME
Mis solicitudes, ofertas recibidas (comparar y aceptar), seguimiento.

## Imagen y contenido
Pequeñas ilustraciones/siluetas de tipos de camión (VAN, TRUCK_SMALL/MEDIUM/LARGE, PLATFORM,
REFRIGERATED, TANKER) como SVG propios. Fotografía solo si aporta (onboarding móvil). Todo en español peruano,
montos `S/ 1,850.00`, fechas `28 nov · 08:00`.
