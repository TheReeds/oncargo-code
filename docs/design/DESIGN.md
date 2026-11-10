# LOGYX — Sistema de diseño

Dirección: **70% Modern Industrial Tech + 30% Friendly Mobility.**
Combinamos la *presentación comercial* de un marketplace (Uber Freight), la *organización densa* de un
SaaS logístico (sennder) y la *rapidez operativa* mobile-first (Flock Freight).

- **Web = negocio.** Estructurada: tablas, filtros, paneles laterales, comparación, tracking con mapa.
- **Móvil = transportista y conductor.** Visual y rápida: tarjetas de carga ricas, precio destacado,
  mapa, acciones grandes. Aquí podemos ser más originales.

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

Modo oscuro (web, opcional en MVP): fondo `#0B1220`, superficie `#111A2B`, mismo primario aclarado `#38A3C9`.

**Tipografía:** *Geist Sans* (UI) y *Geist Mono* (cifras técnicas: placas, km, toneladas, IDs, montos en tabla).
Ambas SIL OFL. Números tabulares (`font-variant-numeric: tabular-nums`) en tablas y precios.
**Iconos:** Lucide (web) / Material Symbols Rounded (Android). **Radio:** 10px tarjetas, 8px inputs, pill en chips.
**Mapas:** MapLibre + OpenFreeMap estilo *positron* (claro, discreto). Rutas en `petrol-700`, origen ○, destino ■.

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
