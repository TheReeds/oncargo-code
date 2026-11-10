# Estado del proyecto

Leyenda: ✅ hecho · 🔄 en progreso · ⬜ pendiente. Actualiza la fila al terminar una tarea (fecha + agente).

## Backend
| Módulo | Estado | Notas |
|---|---|---|
| shared (respuestas, errores, tenancy/RLS, auditoría) | ✅ | 2026-11-10 · `BusinessException` movida a `shared.domain` |
| identity (registro, login, me, organizaciones) | ✅ | 2026-11-10 · fusiona los antiguos `auth` + `organization` |
| fleet (vehículos, conductores) | ✅ | |
| catalog (ciudades, tipos de carga) | ✅ | `City`/`CargoType` = vocabulario compartido en `catalog.domain` |
| pricing (motor de costos por reglas) | ✅ | Haversine × 1.35; ORS pendiente · `TariffCalculator` + `PriceFloorPolicy` |
| marketplace (solicitudes, subasta inversa, ofertas) | ✅ | |
| shipment (envíos, paradas, app conductor) | ✅ | Falta evidencia foto/firma |
| Refactor a hexagonal por módulo (ADR-0002) | ✅ | 2026-11-10 agente backend · contrato HTTP/OpenAPI sin cambios (diff idéntico) · `ModuleBoundariesTest` vigila capas · 38 tests |
| Seed de datos demo (perfil dev) | ✅ | 2026-11-10 · `devdata.DemoDataSeeder`; cuentas en `backend/AGENTS.md` |
| negotiation, reputation, documents, notification, incident, operator, smartload, returnload | ⬜ | |

## Web (Astro)
| Pantalla | Estado |
|---|---|
| Base: shell, sesión, cliente API tipado, design tokens | 🔄 |
| Login / registro | 🔄 |
| PYME: panel, solicitudes, nueva solicitud con cotización, detalle + comparación de ofertas, tracking | 🔄 |
| Transportista: marketplace, mis ofertas, flota, envíos | 🔄 |

## Android (Kotlin) — 2026-11-10 (agente android)
Compila (`assembleDebug`) y pasa `testDebugUnitTest` (28 tests: DTOs del contrato, ViewModels). **Sin probar en emulador/dispositivo** (no hay system image en el SDK ni backend corriendo): validar contra la API real.
| Pantalla / módulo | Estado | Notas |
|---|---|---|
| Base: módulos (`app`, `core:{model,network,data,designsystem}`, `feature:{auth,loads,requests,shipments,driver}`), tema Geist, red Ktor, sesión DataStore, Koin | ✅ | `core:model`/`core:network` son Kotlin/JVM puro |
| Login + cuenta/cerrar sesión (cuentas demo en debug) | ✅ | "Registrarse" abre `${WEB_BASE_URL}/registro` |
| Transportista: Home "Tu actividad" | ✅ | cargas compatibles, ofertas pendientes, viaje activo |
| Transportista: feed LoadCard + chips (Mi vehículo / Mejor pago / Cerca de mí) | ✅ | filtros en el cliente (el API no filtra) |
| Transportista: detalle con mapa + bottom sheet de oferta | ✅ | valida piso, muestra el error de la API |
| Transportista: mis ofertas, envíos + asignar conductor | ✅ | |
| Conductor: viaje activo, mapa, siguiente parada, botón único, StopTimeline | ✅ | arrive/complete |
| PYME: mis solicitudes (cuenta regresiva), comparar ofertas y aceptar, cancelar | ✅ | |
| PYME: seguimiento del envío (mapa + paradas) | ✅ | |
| Pendiente | ⬜ | Publicar solicitud desde móvil, push/notificaciones, GPS real del conductor, evidencia foto/firma, tests de UI/capturas, KMP para iOS |

Notas de contrato (Android): ver informe del agente — falta filtros/paginación en `GET /shipment-requests`, `ciudad` de la organización
solo en `/organizations/{id}`, `BidView` sin datos del vehículo, requester sin RUC verificado, sin endpoint de "viaje activo" del conductor.

## iOS (SwiftUI) — ⬜ fase posterior
