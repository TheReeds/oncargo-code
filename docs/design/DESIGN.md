# LOGYX — Sistema de diseño

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

## Patrones (componentes con nombre — úsalos igual en web y móvil)
- **RouteLine** — `Lima ──── 1,010 km ────▶ Juliaca` con fechas debajo de cada ciudad.
- **LoadCard** (móvil, marketplace) — precio grande en emerald + "precio referencial"; RouteLine; chips
  (`Carga general`, `3.2 t`, `Camión 5 t`, `Compatible`); empresa + badge "Empresa verificada" (RUC);
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
