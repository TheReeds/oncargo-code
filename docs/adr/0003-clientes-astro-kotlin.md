# ADR-0003 — Web en Astro, móvil nativo Kotlin (luego Swift)

**Estado:** aceptado · 2026-11-10 · Modifica RT02 (Angular) y RT03 (Flutter) del SRS del EPE.

## Decisión
- **Web:** Astro 7 en modo SSR (adapter Node). Páginas renderizadas en servidor; el JWT vive en cookie
  httpOnly y solo el servidor Astro habla con la API. Interactividad con islas React + shadcn/ui
  (MIT, código propio copiado al repo, conocido por IAs). Mapas con MapLibre GL + teselas OpenFreeMap (sin API key).
- **Móvil:** una sola app Android nativa (Kotlin + Compose) con navegación por rol: transportista
  (marketplace de cargas), PYME (mis solicitudes/ofertas) y conductor (modo ruta). El operador queda en web.
- `core:model` y `core:network` en Kotlin puro (Ktor + kotlinx.serialization, sin dependencias Android) para
  convertirlos a Kotlin Multiplatform cuando se haga la app SwiftUI.

## Consecuencias
- Se actualizan RT02/RT03 en la documentación del EPE.
- Tres apps Flutter del ADR-03 original → una app con flujos por rol (puede dividirse en flavors después).
