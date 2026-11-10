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
| Contrato aditivo 2026-11-10 (BidView.vehicleType/vehiclePlate, RequestView.requesterRucVerified/minVehicleType, UserInfo.organizationCity, `field` en errores de negocio, montos `S/ 1,850.00`, filtros+paginación, `GET /driver/shipments/active`, `GET /dashboard/summary`) | ✅ | 2026-11-10 agente contrato API · migración V3 · módulo nuevo `dashboard` · 54 tests · OpenAPI solo adiciones · detalle y ejemplos en `docs/api/CHANGELOG.md` · **web/android deben regenerar clientes** |
| negotiation, reputation, documents, notification, incident, operator, smartload, returnload | ⬜ | |

## Web (Astro)
| Pantalla | Estado |
|---|---|
| Base: shell, sesión (cookie httpOnly), cliente API tipado, design tokens | ✅ 2026-11-10 (agente web) |
| Login / registro / privacidad (Ley 29733) / página conductor | ✅ |
| PYME: panel, solicitudes, nueva solicitud con cotización en vivo, detalle + comparación de ofertas + aceptar/cancelar, tracking (mapa) | ✅ |
| Transportista: marketplace (filtros + drawer de oferta), mis ofertas, flota (vehículos/conductores), envíos (asignar conductor) | ✅ |
| Sistema de diseño web (2026-11-10): biblioteca `components/ui` (Radix + cmdk + react-day-picker + sonner: Select, Combobox, MultiSelect, DatePicker/DateRangePicker, Money/NumberInput, ConfirmDialog `confirm()`, toasts, Dialog, Tabs, Tooltip, DropdownMenu, Pagination, Avatar, Skeletons), catálogo `/dev/ui` (solo dev), skill `.claude/skills/logyx-design-system/` (+ `references/web.md`), guía `docs/design/CONTENT.md`; todas las pantallas migradas (sin selects/fechas nativos, confirmaciones y toasts consistentes) | ✅ |
| Pendientes web: modo oscuro, polling/tiempo real, ruta por carretera en mapa, tests e2e; ver `web/AGENTS.md` | ⬜ |

Notas de contrato (web): `BidView` no trae tipo/placa del vehículo (la PYME no ve el vehículo ofertado); `RequestView` no trae "vehículo mínimo" (se muestra el peso); el mensaje de RN03 formatea montos sin separador de miles (`S/ 2925.89`); los errores de validación llegan con `field: null`.

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
| Sistema de diseño Compose (2026-11-10): `Logyx*` botones/campos/MoneyField/dropdown/combobox/date pickers es-PE/diálogos/hojas/mensajes (`UiMessage`)/banners/skeletons/segmentado/badges/top+bottom bar, a11y y variantes AA | ✅ | `:core:designsystem`; catálogo debug (login → "Catálogo UI"); doc en `.claude/skills/logyx-design-system/references/android.md`; pantallas migradas a estos componentes con confirmaciones y mensajes de éxito; textos en `strings.xml` por módulo |
| Pendiente | ⬜ | Publicar solicitud desde móvil, push/notificaciones, GPS real del conductor, evidencia foto/firma, tests de UI/capturas, KMP para iOS |
| Contrato aditivo 2026-11-10 adoptado en Android | ✅ | DTOs (`BidView.vehicle*`, `RequestView.requesterRucVerified/minVehicleType`, `UserInfo.organizationCity`, `DashboardSummary`), marketplace con filtros + scroll infinito en servidor, `GET /dashboard/summary` en el Home, `GET /driver/shipments/active` en conductor, errores con `field` en los campos de la oferta; tests de parseo actualizados |

Notas de contrato (Android): ver informe del agente — falta filtros/paginación en `GET /shipment-requests`, `ciudad` de la organización
solo en `/organizations/{id}`, `BidView` sin datos del vehículo, requester sin RUC verificado, sin endpoint de "viaje activo" del conductor.

## iOS (SwiftUI) — ⬜ fase posterior
