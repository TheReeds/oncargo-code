# Estado del proyecto

Leyenda: ✅ hecho · 🔄 en progreso · ⬜ pendiente. Actualiza la fila al terminar una tarea (fecha + agente).

## Backend
| Módulo | Estado | Notas |
|---|---|---|
| shared (respuestas, errores, tenancy/RLS, auditoría) | ✅ | 2026-11-10 |
| identity (registro, login, me, organizaciones) | ✅ | |
| fleet (vehículos, conductores) | ✅ | |
| catalog (ciudades, tipos de carga) | ✅ | |
| pricing (motor de costos por reglas) | ✅ | Haversine × 1.35; ORS pendiente |
| marketplace (solicitudes, subasta inversa, ofertas) | ✅ | |
| shipment (envíos, paradas, app conductor) | ✅ | Falta evidencia foto/firma |
| Refactor a hexagonal por módulo (ADR-0002) | 🔄 | |
| Seed de datos demo (perfil dev) | 🔄 | |
| negotiation, reputation, documents, notification, incident, operator, smartload, returnload | ⬜ | |

## Web (Astro)
| Pantalla | Estado |
|---|---|
| Base: shell, sesión, cliente API tipado, design tokens | 🔄 |
| Login / registro | 🔄 |
| PYME: panel, solicitudes, nueva solicitud con cotización, detalle + comparación de ofertas, tracking | 🔄 |
| Transportista: marketplace, mis ofertas, flota, envíos | 🔄 |

## Android (Kotlin)
| Pantalla | Estado |
|---|---|
| Base: módulos, tema, red, sesión | 🔄 |
| Login | 🔄 |
| Transportista: home, feed de cargas, detalle + ofertar, mis ofertas | 🔄 |
| Conductor: viaje activo + paradas | 🔄 |
| PYME: solicitudes y ofertas | 🔄 |

## iOS (SwiftUI) — ⬜ fase posterior
