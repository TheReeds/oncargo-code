# LOGYX — Guía para agentes (humanos e IAs)

> Archivo canónico de contexto. `CLAUDE.md` solo lo importa. Léelo completo antes de tocar código.
> Si cambias una decisión de arquitectura, actualiza este archivo **y** el ADR correspondiente.

## Qué es
Marketplace B2B de fletes para PYMEs del corredor Lima–Sierra Sur (Perú). Las PYMEs publican
cargas, los transportistas compiten en una **subasta inversa**, la PYME acepta una oferta, se
genera un **envío** y el **conductor** lo ejecuta parada por parada.
Especificación funcional completa: repo hermano `../ProyectoEPE-LOGYX/docs/software/` (SRS, modelo de datos).

## Mapa del monorepo
| Carpeta | Stack | Guía local |
|---|---|---|
| `backend/` | Quarkus 3.40 · Java 21 · PostgreSQL 16 · Flyway · JWT RSA | `backend/AGENTS.md` |
| `web/` | Astro 7 (SSR, Node) · Tailwind 4 · React islands + shadcn/ui · MapLibre | `web/AGENTS.md` |
| `android/` | Kotlin · Jetpack Compose · Material 3 · Ktor · Koin · MapLibre | `android/AGENTS.md` |
| `ios/` | SwiftUI (fase posterior; reutilizará `core:model`/`core:network` vía KMP) | `ios/README.md` |
| `docs/api/openapi.yaml` | **Contrato de la API (fuente de verdad)** — generado por el backend | — |
| `docs/architecture/` | Visión general y capas | `overview.md` |
| `docs/adr/` | Decisiones de arquitectura numeradas | — |
| `docs/design/DESIGN.md` | Sistema de diseño (tokens, componentes, pantallas por rol) | — |
| `docs/domain/business-rules.md` | Reglas RN01–RN12 → dónde se implementan | — |
| `docs/STATUS.md` | Estado de cada módulo/pantalla. **Actualízalo al terminar una tarea.** | — |

## Reglas de oro
1. **El contrato manda.** Web y móvil consumen `docs/api/openapi.yaml`. Si el backend cambia un
   endpoint, regenera el contrato (`cd backend && ./mvnw package -DskipTests`) y avisa en `docs/STATUS.md`.
2. **Aislamiento por organización (ADR-0001).** Nunca recibas `organizationId` desde el cliente: sale del JWT
   (`TenantContext`). Todo acceso a datos de usuario va dentro de `@Transactional` para que se active RLS.
3. **Respuestas** siempre `{ data, meta }`; errores `{ error: { code, message, field }, meta }` con códigos `LOGYX-XXX-NNN`.
4. **Idioma:** dominio, UI y mensajes en español; identificadores de código en inglés.
5. **Una carpeta por feature/módulo**, con la misma forma interna en todo el monorepo (ver guías locales).
   Si creas un módulo nuevo, copia la forma de uno existente (`marketplace` es la referencia).
6. **Tests antes de declarar terminado:** backend `./mvnw test`, web `npm run check && npm run build`,
   android `./gradlew assembleDebug testDebugUnitTest`.
7. No subas secretos. Las claves JWT de dev/test se generan en memoria; en prod vienen por env.

## Roles y JWT
| Usuario | `groups` en JWT | Claims |
|---|---|---|
| Usuario de PYME | `PYME` | `org_id`, `org_type=PYME`, `role` |
| Usuario de transportista | `CARRIER` | `org_id`, `org_type=CARRIER`, `role` |
| Conductor | `DRIVER` (solo) | `org_id` del transportista, `role=DRIVER` |
| Operador LOGYX | `OPERATOR` | (fase posterior) |

## Cuentas demo (perfil dev del backend)
`pyme@logyx.pe`, `carrier@logyx.pe`, `driver@logyx.pe` — contraseña `Logyx2026!` (ver `backend/AGENTS.md`).

## Cómo levantar todo
```bash
cd backend && ./mvnw quarkus:dev          # API en :8080 (Dev Services levanta Postgres con Docker)
cd web && npm install && npm run dev      # Web en :4321 (API_URL=http://localhost:8080)
cd android && ./gradlew installDebug      # Emulador: API en http://10.0.2.2:8080
```
Requiere `JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (el JDK por defecto de la máquina es 27).
