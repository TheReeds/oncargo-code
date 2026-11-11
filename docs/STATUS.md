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
| shipment (envíos, paradas, app conductor) | ✅ | 2026-11-10 fase 2: completar la entrega exige foto (`LOGYX-TRK-006`), incidencia activa congela paradas (`LOGYX-TRK-005`), `ShipmentView.hasActiveIncident` |
| Refactor a hexagonal por módulo (ADR-0002) | ✅ | 2026-11-10 agente backend · contrato HTTP/OpenAPI sin cambios (diff idéntico) · `ModuleBoundariesTest` vigila capas · 38 tests |
| Seed de datos demo (perfil dev) | ✅ | 2026-11-10 · `devdata.DemoDataSeeder`; cuentas en `backend/AGENTS.md` |
| Contrato aditivo 2026-11-10 (BidView.vehicleType/vehiclePlate, RequestView.requesterRucVerified/minVehicleType, UserInfo.organizationCity, `field` en errores de negocio, montos `S/ 1,850.00`, filtros+paginación, `GET /driver/shipments/active`, `GET /dashboard/summary`) | ✅ | 2026-11-10 agente contrato API · migración V3 · módulo nuevo `dashboard` · 54 tests · OpenAPI solo adiciones · detalle y ejemplos en `docs/api/CHANGELOG.md` · **web/android deben regenerar clientes** |
| negotiation (chat RN07 + contra-ofertas) | ✅ | 2026-11-10 líder backend fase 2 · V4 · chat por cursor (`meta.nextCursor`) |
| documents (evidencia foto/firma, guías; almacenamiento local) | ✅ | 2026-11-10 · V5 · ADR-0005 · multipart jpg/png/pdf ≤ 10 MB |
| reputation (calificaciones RN06, Trust Score RN09) | ✅ | 2026-11-10 · V6 · `GET /organizations/{id}/reputation`, `carrierTrustScore`/`requesterTrustScore` · métricas compuestas RF42/RF43 pendientes |
| incident (RN11, comentarios, estados) | ✅ | 2026-11-10 · V7 · resolución no fija el estado final del envío (RF55 parcial) |
| notification (in-app por eventos de dominio) | ✅ | 2026-11-10 · V8 · ADR-0004 · sin email (RF50) ni push/tiempo real; los conductores no tienen centro propio |
| operator (alertas SLA, métricas, asignación manual RN10) | ✅ | 2026-11-10 · V9 · cuenta `operator@logyx.pe` en el seed |
| Fase 2 backend — integración | ✅ | 2026-11-10 · 107 tests verdes (ModuleBoundaries + RLS con 6 casos) · OpenAPI solo adiciones (+24 paths, +53 schemas; `BidView`, `RequestView`, `ShipmentView`, `Meta` con campos nuevos) · **cambio de comportamiento: completar entrega exige foto** · detalle en `docs/api/CHANGELOG.md` · **web/android deben regenerar clientes y agregar upload de evidencia antes de completar la entrega** |
| smartload, returnload, trip (RN12), email/push, SUNAT, ORS | ⬜ | |

## Web (Astro)
| Pantalla | Estado |
|---|---|
| Base: shell, sesión (cookie httpOnly), cliente API tipado, design tokens | ✅ 2026-11-10 (agente web) |
| Login / registro / privacidad (Ley 29733) / página conductor | ✅ |
| PYME: panel, solicitudes, nueva solicitud con cotización en vivo, detalle + comparación de ofertas + aceptar/cancelar, tracking (mapa) | ✅ |
| Transportista: marketplace (filtros + drawer de oferta), mis ofertas, flota (vehículos/conductores), envíos (asignar conductor) | ✅ |
| Sistema de diseño web (2026-11-10): biblioteca `components/ui` (Radix + cmdk + react-day-picker + sonner: Select, Combobox, MultiSelect, DatePicker/DateRangePicker, Money/NumberInput, ConfirmDialog `confirm()`, toasts, Dialog, Tabs, Tooltip, DropdownMenu, Pagination, Avatar, Skeletons), catálogo `/dev/ui` (solo dev), skill `.claude/skills/logyx-design-system/` (+ `references/web.md`), guía `docs/design/CONTENT.md`; todas las pantallas migradas (sin selects/fechas nativos, confirmaciones y toasts consistentes) | ✅ |
| QA web (2026-11-10): suite e2e Playwright versionada en `web/tests/e2e/` (27 tests: registro/validaciones, login/logout/roles incl. conductor, publicar solicitud con cotización en vivo, marketplace con filtros+paginación, oferta con error inline y toast, comparar/aceptar, asignar conductor, seguimiento, cancelar, `/conductor`, accesibilidad axe en 7 pantallas); `npm run test:e2e` | ✅ |
| QA web: tabla de cargas rediseñada (5 columnas, sin scroll horizontal ≥1280 px, precio sugerido + piso siempre visibles); errores por `field` inline en registro y nueva solicitud; componentes nuevos `PasswordInput`, `VerifiedBadge`, `FilterBar`, `Timeline`, `AuthCard`; `/conductor` muestra el viaje activo; corregido: el conductor no podía cerrar sesión (middleware), contraste AA (`ink-500` → `#5b6b7f`), roles ARIA en tabs/mapa/listas de datos/menú de usuario | ✅ |
| **Fase 3 web (2026-11-10)**: negociación (panel de hilos + chat con polling y error RN07 inline, contra-ofertas PYME/transportista, "Negociar" en Mis ofertas, "Preguntar" en cargas), reputación (`TrustScore` en ofertas/cargas, `/organizaciones/[id]`, calificación tras entrega con `canReview`), documentos (pestaña del envío, previsualización, descarga vía proxy `/documentos/[id]`, subida con `FileDropzone` ≤10 MB), incidencias (reportar, `/incidencias`, detalle con comentarios, banner RN11), notificaciones (campana con polling 30 s, `/notificaciones`), panel del operador `/operador` (métricas, alertas SLA, asignación manual RN10, `/operador/incidencias`, solicitud/envío en solo lectura). Componentes nuevos: `TrustScore`, `StarRating`, `FileDropzone`; `StatusPill` con incidencias/contra-ofertas. e2e: 55 tests en 2 corridas verdes (+ axe en pantallas nuevas) | ✅ |
| Fase 3 web — limitaciones: el contrato no ofrece listados globales de solicitudes/envíos para el operador; el e2e de asignación manual necesita backend con `-Dlogyx.operator.sla=PT0S` (con SLA 24 h se salta); sin push/tiempo real (polling) | ⬜ |
| Pendientes web: modo oscuro, polling/tiempo real, ruta por carretera en mapa; ver `web/AGENTS.md` | ⬜ |

Notas de contrato (web): adoptado el contrato aditivo 2026-11-10 (vehículo en ofertas, RUC verificado, vehículo mínimo, `field` en errores, filtros y paginación). Observación: el mensaje de login incorrecto de la API es "Credenciales inválidas" (CONTENT.md propone "Correo o contraseña incorrectos. Revísalos e inténtalo de nuevo.").

## Android (Kotlin) — 2026-11-10 (agente android)
Compila (`assembleDebug`), pasa `testDebugUnitTest` y 5 tests instrumentados de Compose (`connectedDebugAndroidTest`: login→home por rol, ofertar con piso, conductor hasta Entregado). **Probada en emulador (Pixel 7, API 36) contra el backend real (2026-11-10, QA android)**: login de los 3 roles, feed con filtros, detalle con mapa, oferta bajo piso y válida, aceptar oferta (PYME), asignar conductor, conductor hasta "Viaje entregado", modo oscuro y fuente 1.3x. Capturas en `android/docs/screenshots/`. Bugs corregidos en esa pasada: ver lista abajo.
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
| QA en emulador (2026-11-10) | ✅ | Corregido: `setBody` sin tipo (login 400), crash al abrir "Más filtros" (scroll anidado en hoja), texto "S/ 700.00en" (espacio inicial en strings), segmentado partía "Adjudicadas", chips de filtro en varias filas (ahora una fila con scroll), destino del mapa hueco, mapa claro en modo oscuro, precio ilegible en oscuro (`moneyText`), direcciones truncadas sin elipsis, iconos de la barra de estado en el login, `Ink500` alineado a #5B6B7F (AA). Textos de enums y mensajes de ViewModels pasaron a `strings.xml` (`UiMessage(@StringRes)`, `UiText`); `LogyxDropdown.onClear`, `LoadCard` con `minVehicleType`, `RouteMap.contentDescription`, componentes `RadioCardRow`/`InitialsAvatar`/`LoadingMoreFooter` promovidos a designsystem |
| **Backend dev: host 10.0.2.2 rechazado** | ⚠️ | En `quarkus:dev` el host-validation de Quarkus devuelve 400 vacío a `Host: 10.0.2.2:PORT` (el emulador no puede usar la API). Hasta que el backend añada `%dev.quarkus.http.host-validation.require-localhost=false`, arrancar con `-Dquarkus.http.host-validation.require-localhost=false` |
| Pendiente | ⬜ | Publicar solicitud desde móvil, push/notificaciones, GPS real del conductor, evidencia foto/firma, KMP para iOS, mapa: cambiar estilo claro/oscuro en caliente, reintento al fallar "cargar más" |
| Contrato aditivo 2026-11-10 adoptado en Android | ✅ | DTOs (`BidView.vehicle*`, `RequestView.requesterRucVerified/minVehicleType`, `UserInfo.organizationCity`, `DashboardSummary`), marketplace con filtros + scroll infinito en servidor, `GET /dashboard/summary` en el Home, `GET /driver/shipments/active` en conductor, errores con `field` en los campos de la oferta; tests de parseo actualizados |

Notas de contrato (Android): ver informe del agente — falta filtros/paginación en `GET /shipment-requests`, `ciudad` de la organización
solo en `/organizations/{id}`, `BidView` sin datos del vehículo, requester sin RUC verificado, sin endpoint de "viaje activo" del conductor.

## iOS (SwiftUI) — ⬜ fase posterior
