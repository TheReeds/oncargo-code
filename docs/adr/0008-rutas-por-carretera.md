# ADR-0008 — Rutas y distancias por carretera: precálculo OSM versionado, con motor propio como siguiente paso

**Estado:** aceptado · 2026-11-16 · implementado: fase 1 (dataset y módulo `routing`), fase 2 backend (pricing, trip, `GET /routes`) y mapas web y Android

## Contexto
- **Distancia del precio.** `pricing/infrastructure/distance/HaversineDistanceProvider` calcula la línea recta entre los
  centros de las ciudades × 1.35. `TariffCalculator` usa esos km para `distance_km`, `suggested_price` y `price_floor` (RN03).
  `trip/domain/CityProximityPolicy` (retornos, Smart Load) usa el mismo × 1.35, salvo una tabla con un solo par
  (Juliaca–Puno = 44 km). El error frente a la carretera real va de −52 % a +21 % (tabla abajo): en la sierra la vía
  rodea cerros y valles (Cusco–Ayacucho: 334 km estimados contra 699 km reales), y en la costa el factor sobra (Lima–Ica).
- **Mapas.** La web (`TrackingMap.tsx`) y Android (`RouteMap.kt`) dibujan rectas entre ciudades, que cruzan cerros y el lago.
- **Paradas múltiples.** Smart Load ordena recojos por fecha y entregas por cercanía al último recojo (un orden "voraz"), con
  distancias aproximadas.
- **Alcance:** catálogo cerrado de 11 ciudades (`catalog.domain.City`), VPS de 4 a 8 GB, costo casi cero. Más adelante
  habrá direcciones arbitrarias (recojo y entrega exactos).

## Opciones evaluadas (precios y límites de noviembre de 2026)
| Opción | Exactitud | Costo | RAM | Licencia y términos | Mantenimiento | Direcciones arbitrarias |
|---|---|---|---|---|---|---|
| **OSRM self-hosted** (extracto Geofabrik Perú, 246 MB `.pbf`) | Alta (red OSM completa) | VPS | Preproceso `osrm-extract`/`partition` ≈ 3–5 GB; servicio MLD ≈ 1–1.5 GB (estimado, sin medir) | BSD-2 + ODbL de los datos | Reprocesar el extracto para actualizar; perfiles en Lua | Sí |
| **Valhalla self-hosted** | Alta; tiene perfil `truck` | VPS | Tiles ≈ 1–2 GB al construir; servicio liviano (tiles mapeados en memoria) | MIT + ODbL | Similar a OSRM | Sí |
| **GraphHopper self-hosted** | Alta; *custom models* para camión | VPS | JVM: importación ≈ 2–3 GB de heap, servicio ≈ 1–2 GB | Apache-2 + ODbL | Similar | Sí |
| **OpenRouteService API** | Alta (OSM) | Gratis: 2 000 rutas/día, 500 matrices/día, 40/min | 0 | Datos ODbL: se pueden guardar con atribución | Clave de API; depende de un servicio académico sin SLA | Sí |
| **Mapbox Directions/Matrix** | Alta | 100 000 rutas/mes gratis, luego US$ 2 por 1 000 | 0 | Sus términos limitan guardar resultados y piden mostrarlos sobre mapas de Mapbox (usamos OpenFreeMap) | Clave y facturación | Sí |
| **Google Routes (Essentials)** | Muy alta (con tráfico) | 10 000 eventos/mes gratis, luego US$ 5 por 1 000 | 0 | Prohíbe cachear más de 30 días y mostrar sobre mapas que no sean de Google | Clave y facturación | Sí |
| **Precálculo offline** (matriz ciudad×ciudad + geometrías como dato del repo) | Alta para los pares del catálogo (la del motor usado) | 0 (≈ 60 peticiones a un servidor demo, una vez) | Despreciable (dataset de ≈ 220 KB) | ODbL: base de datos derivada, con atribución | Regenerar con un script (≈ 2 min) al cambiar el catálogo | No |
| **Dijkstra/A\* propio sobre un grafo vial simplificado** | Media: depende de un grafo a mano | 0 | Mínima | Propio (si sale de OSM, ODbL) | Alto: mantener nodos y aristas a mano | Parcial |

Hallazgo al probar los motores: el perfil **`truck` de Valhalla** sobre los datos OSM de Perú da desvíos absurdos
(Lima → Ica 506 km y 13.7 h, frente a 302 km y 4.5 h con `auto`; Lima → Tacna 1 431 km, frente a 1 218 km con OSRM),
probablemente por etiquetas `hgv`/`maxweight` incompletas en la Panamericana Sur. No se usa hasta validarlo con un
extracto propio. El servidor público de Valhalla además limita las matrices a 150 km por celda.

## Decisión
**Enfoque híbrido en dos etapas.**

1. **Ahora: precálculo offline versionado** (`scripts/routing/build_routes.py` → `backend/src/main/resources/routing/routes.json`).
   - Motor: **OSRM, perfil `driving`**, del servidor demo público (`router.project-osrm.org`). Solo se envían las
     coordenadas de las 11 ciudades: una petición `table` (matriz 11×11 **dirigida** de km y minutos) y 55 `route` (una
     por par) más las del sentido inverso cuando la ruta de vuelta es otra (> 3 % de diferencia; 5 a 6 pares en la
     sierra). Pausa de 1.5 s entre peticiones (la política pide como máximo 1 por segundo), reintentos con espera y caché
     local (`scripts/routing/.cache`, ignorada por git) para que regenerar no repita peticiones.
   - Métrica: **la ruta más rápida** del perfil; `distanceKm` es la longitud de esa ruta. En la sierra la ruta más
     corta en km suele ir por trochas sin asfaltar que un camión no toma; la más rápida sigue las vías nacionales.
     Como en dos sentidos la ruta puede ser distinta, la matriz no es simétrica ni cumple siempre la desigualdad
     triangular: el optimizador no lo supone.
   - Geometría: Douglas-Peucker con 60 m de tolerancia (de 2 700–28 000 puntos por ruta a 34–2 000) y polyline con
     precisión 5. Clave `ORIGEN-DESTINO`; si no existe, se usa la del otro sentido invertida.
   - Duración: la del perfil de auto. El precio no la usa; si se muestra al usuario, se aplica un factor para camión
     (fase 2).
2. **Módulo `pe.logyx.routing`** (hexagonal, ADR-0002):
   - `domain/`: `RoadNetwork` (km y min dirigidos), `RouteCalculator` (tramo, recorrido, orden óptimo de paradas, orden
     de recojos y entregas, desvío), `StopOrderOptimizer` (**Held-Karp exacto hasta 13 nodos**, con partida y llegada fijas
     opcionales y precedencias recojo → entrega; con más nodos, vecino más cercano factible más mejora local por
     reubicación y 2-opt), `PolylineCodec` y `StraightLineEstimate` (respaldo × 1.35 marcado `estimated` si falta un par).
     Con 4 cargas (el máximo de Smart Load) hay 8 paradas más la partida y la llegada, y siempre se resuelve de forma
     exacta (< 10 ms).
   - Desvío (Smart Load o una carga sobre un viaje publicado, RN12): mejor inserción del recojo y la entrega en el
     recorrido, con la partida y la llegada fijas: `Detour.extraKm()`.
   - `application/RoutePlanner`: lo que usan los demás módulos (por su `application`, como manda `ModuleBoundariesTest`).
     `application/port/out/RouteDataset`: fuente de datos viales.
   - `infrastructure/dataset/ClasspathRouteDataset`: lee el JSON una vez (Jackson) y decodifica las geometrías bajo
     demanda (`logyx.routing.dataset`, por omisión `routing/routes.json`).
   - **Sin Dijkstra propio:** la matriz ya trae los caminos mínimos calculados por OSRM sobre la red OSM completa. Un
     grafo hecho a mano sería menos exacto y costaría mantenerlo. Paradas y desvíos se resuelven sobre la matriz.
3. **Después: direcciones arbitrarias** con un **OSRM propio** (o Valhalla, si se valida su perfil de camión) como otra
   implementación de `RouteDataset`/`RoadNetwork` por HTTP. El extracto de Perú se preprocesa fuera del VPS (en CI o en
   un equipo con al menos 8 GB libres) y el VPS solo sirve el grafo (≈ 1–1.5 GB). El dataset sigue como caché y respaldo
   para los pares del catálogo. Hace falta geocodificar (Nominatim o Photon propios, o un servicio con términos que
   permitan guardar resultados). Mapbox y Google quedan descartados para guardar rutas por sus términos.

## Licencia y atribución (ODbL 1.0)
- El JSON es una **base de datos derivada** de OpenStreetMap: lleva `attribution` y `license`, y el script que la genera
  está en el repo, lo que cumple la obligación de ofrecer el método para reproducirla si se usa en público.
- Lo que se muestra (mapas y precios calculados con esos km) debe atribuir a OpenStreetMap. Las teselas de
  OpenFreeMap ya muestran "© OpenStreetMap contributors". La utilidad web `web/src/lib/routing/route-path.ts` además
  declara la atribución en su fuente GeoJSON, y en la fase 2 la API devolverá `attribution` junto a la geometría.

## Cómo cambian las distancias (línea recta entre los centros, cálculo anterior y carretera)
| Par | Línea recta km | Antes km (× 1.35) | Carretera km | Diferencia | Duración en auto | Precio sugerido con 10 t GENERAL (antes → después) |
|---|---:|---:|---:|---:|---:|---:|
| Lima → Arequipa | 766 | 1 034 | 1 002 | −3 % | 14.3 h | S/ 9,470 → S/ 9,172 |
| Lima → Juliaca | 839 | 1 133 | 1 248 | +10 % | 18.9 h | S/ 10,372 → S/ 11,431 |
| Lima → Cusco | 575 | 776 | 1 104 | +42 % | 17.7 h | S/ 7,104 → S/ 10,112 |
| Lima → Ica | 266 | 359 | 297 | −17 % | 4.2 h | S/ 3,286 → S/ 2,727 |
| Arequipa → Puno | 174 | 235 | 274 | +17 % | 5.5 h | S/ 2,148 → S/ 2,514 |
| Arequipa → Tacna | 225 | 303 | 402 | +33 % | 6.0 h | S/ 2,781 → S/ 3,688 |
| Juliaca → Puno | 40 | 54 (pricing) / 44 (trip) | 43 | −20 % | 0.9 h | S/ 497 → S/ 400 |
| Puno → Cusco | 331 | 447 | 385 | −14 % | 6.2 h | S/ 4,094 → S/ 3,532 |
| Cusco → Abancay | 99 | 134 | 208 | +55 % | 4.3 h | S/ 1,239 → S/ 1,907 |
| Cusco → Ayacucho | 248 | 334 | 699 | +109 % | 11.3 h | S/ 3,061 → S/ 6,398 |

La lista de los 55 pares sale con `python3 -I scripts/routing/build_routes.py --offline --compare`. Los radios de proximidad
(retorno 50 km, Smart Load 50 y 80 km) siguen siendo válidos: el único par cercano del corredor, Juliaca–Puno, queda en 43 km.

## Consecuencias
- (+) Precios y desvíos con km reales, sin red en tiempo de ejecución, sin claves, sin costo y con RAM despreciable.
  Es determinista: los tests y la CI no dependen de servicios externos.
- (+) Interfaz estable (`RoutePlanner`/`RouteDataset`): el motor propio entra después sin tocar a quienes lo usan.
- (−) Solo sirve para los centros de las ciudades del catálogo. Agregar una ciudad obliga a regenerar el dataset; si no
  se hace, `ClasspathRouteDatasetTest` falla, y en producción el par cae al respaldo × 1.35 marcado `estimated`.
- (−) Perfil de auto: no considera restricciones para camiones y su duración es optimista para carga pesada.
- (−) Los datos tienen la fecha del precálculo: regenerar cada 6 meses o cuando cambie una vía importante (≈ 60 peticiones).
- Cambian los precios sugeridos y los pisos de RN03 de las solicitudes **nuevas** cuando se conecte pricing (fase 2).
  Las ya publicadas conservan su `distance_km` guardado.

## Fase 2
**Backend (hecho, 2026-11-16):**
- `pricing/infrastructure/distance/RoadDistanceProvider` implementa `DistanceProvider` delegando en `RoutePlanner.distanceKm`;
  `HaversineDistanceProvider` se retiró.
- `trip/domain/RoadDistances` (interfaz funcional del dominio de trip) + `CityProximityPolicy` por instancia: `ReturnLoadService` y
  `SmartLoadService` le pasan `RoutePlanner::distanceKm`. `distanceKm` es dirigida (del destino del tramo al origen del retorno);
  `within` usa el sentido más corto, para que la compatibilidad de Smart Load sea simétrica.
- `SmartLoadPlanner.build`: la sugerencia se persiste como "todos los recojos, luego todas las entregas" (`pickupOrder` /
  `deliveryOrder` en `smart_load_items`), así que, **dentro de esa forma**, se prueban todas las permutaciones (≤ 4! × 4! = 576)
  con los recojos en orden de fecha y se elige la de menos km (`Plan.routeKm`). Intercalar recojos y entregas (lo que resuelve
  `RoutePlanner.planLoads`) exigiría cambiar el modelo persistido (una secuencia de paradas, migración V14); no compensa en el
  corredor actual, donde los orígenes compatibles están a ≤ 50 km y los destinos a ≤ 80 km.
- `GET /api/v1/routes?stops=…[&optimize=true]` (`routing/infrastructure/web/RoutingResource` + `RoutingDtos`): paradas, km,
  minutos, tramos, `polyline` (precisión 5), `estimated` y `attribution`. Errores `LOGYX-RTE-001/002`. Contrato compatible.

**Web y Android (hecho, 2026-11-16):**
- Web: `features/routing` (`getRoute` con caché en el servidor, action `routing.route`, hook `useRouteGeometries` con caché
  por pestaña; `toRouteGeometry` mapea `polylinePrecision` → `precision`). `TrackingMap.tsx` (envíos de PYME y transportista y
  panel de la PYME) dibuja con `web/src/lib/routing`: línea continua por la vía cuando llega la geometría y recta **punteada**
  mientras carga o si falla. La fuente declara "Rutas © OpenStreetMap", que aparece en el control de atribución.
- Android: `RouteGeometry`/`RouteView`/`Polyline` (`:core:model`), `RoutesApi` (`:core:network`, implementado por `LogyxApi`),
  `RoutesRepository` con caché (`:core:data`). `RouteMap(path = …)` dibuja la vía con la etiqueta "Rutas © OpenStreetMap"
  y usa la recta punteada como respaldo. Cargan la ruta los ViewModels de detalle de solicitud, detalle de carga, seguimiento
  de envío y conductor.
- Android traza origen → destino, como antes; la web sigue las paradas del envío en orden.
