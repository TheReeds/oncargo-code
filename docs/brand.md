# Marca: OnCargo

El producto se llama **OnCargo** (antes LOGYX): marketplace B2B de carga en Perú. Esta guía fija qué cambió con el rebrand y qué conserva el nombre técnico `logyx`.

## Nombre
- Escritura: **OnCargo** (O y C mayúsculas, sin espacio). Nunca "ONCARGO" ni "On Cargo", salvo en el logotipo.
- En la UI, correos y documentos de producto se dice "OnCargo"; el equipo de soporte/operación es "el equipo de OnCargo".

## Logos (`media/logos/`)
| Archivo | Uso |
|---|---|
| `OnCargo-web-fondo-claro.png` | Logotipo horizontal sobre fondos claros (web claro, documentos, drawable Android claro). |
| `OnCargo-web-fondo-oscuro.png` | Logotipo horizontal sobre fondos oscuros (web oscuro, cabecera del login, drawable Android `night`). |
| `OnCargo-simbolo.png` | Solo el símbolo (aro con pin y ruta), transparente. Avatares, espacios pequeños, estados vacíos. |
| `OnCargo-icono-app.png` | Ícono de aplicación 1024x1024 con fondo azul `#081F46`. Origen del ícono de Android (y futuro iOS). |
| `OnCargo-favicon-16.png` / `-32.png` | Favicon de la web. |

Android: `mipmap-*/ic_launcher*` (adaptativo + monocromático) y `drawable(-night)-nodpi/oncargo_logo*.png` en `:core:designsystem`; ver `android/AGENTS.md`.

## Qué conserva el nombre técnico "logyx"
| Elemento | Ejemplo | Por qué se queda |
|---|---|---|
| Paquetes Java/Kotlin | `pe.logyx.*` | Renombrar toca cientos de archivos y rompe merges en paralelo. |
| `applicationId` / namespaces Android | `pe.logyx.app` | Es la identidad de la app en el dispositivo y en Play Store; cambiarla crea otra app. |
| Códigos de error | `LOGYX-XXX-NNN` | Contrato de API consumido por web y móvil. |
| Base de datos | nombres de BD, roles (`logyx_api`, `logyx_app`), esquemas | Migraciones Flyway y RLS dependen de ellos. |
| Variables de entorno y config | `LOGYX_*`, claves `logyx.*` | Despliegues e infra existentes. |
| Clases y tema | `LogyxTheme`, `LogyxApi`, `LogyxButton`, `LogyxApplication` | Identificadores internos; sin impacto visible. |
| Repo y carpetas | `logyx/`, `ProyectoEPE-LOGYX` | URLs, CI y referencias cruzadas. |
| Skill de diseño | `logyx-design-system` | Referenciada por agentes y flujos. |
| Dominio/cuentas demo | `*@logyx.pe`, `https://api.logyx.pe` | Hasta definir dominio de OnCargo. |

## Propuesta de renombre técnico futuro
1. Definir dominio definitivo (`oncargo.pe`) y migrar `api.*`, correos y cuentas demo; mantener redirección del dominio antiguo.
2. Códigos de error: añadir prefijo `ONC-` como alias, deprecar `LOGYX-` con una versión de contrato de por medio (compatibilidad con `scripts/openapi-compat.py`).
3. Android: nuevo `applicationId` solo si aún no hay publicación en Play Store; si ya existe, mantenerlo. Paquetes `pe.logyx` -> `pe.oncargo` con un único commit mecánico (IDE refactor) en una ventana sin otras ramas abiertas.
4. Backend: renombrar paquetes igual; roles y esquemas de BD con migración Flyway coordinada con `infra/` y variables de entorno (aceptar ambos nombres un ciclo).
5. Clases `Logyx*` -> `OnCargo*` y skill `logyx-design-system` -> `oncargo-design-system`; después el repo.
