# LOGYX Web — guía para agentes

Astro 7 (SSR, `@astrojs/node` standalone) · Tailwind 4 · islas React + primitivas estilo shadcn/ui · lucide · MapLibre GL (OpenFreeMap positron) · Geist Sans/Mono (fontsource).
Contexto general: `../AGENTS.md`. Sistema de diseño: `../docs/design/DESIGN.md`. Contrato: `../docs/api/openapi.yaml`.

## Cómo correr
```bash
cd web && cp .env.example .env     # API_URL=http://localhost:8080
npm install                        # postinstall copia el worker de MapLibre a public/maplibre
npm run dev                        # http://localhost:4321
npm run check && npm run build     # obligatorio antes de dar algo por terminado
npm start                          # producción (node dist/server/entry.mjs); en local sin HTTPS usa INSECURE_COOKIES=1
```
Cuentas demo: ver `../AGENTS.md`. Solo el servidor de Astro habla con la API (`API_URL`); el JWT vive en la cookie httpOnly `logyx_session`.

## Estructura
```
src/
├── pages/                  # Rutas finas: leen sesión, llaman a features/*/server.ts, renderizan
│   ├── index, login, registro, privacidad, conductor, logout(POST)
│   ├── pyme/               # index (panel), solicitudes/{index,nueva,[id]}, envios
│   └── transportista/      # marketplace, ofertas, flota, envios
├── features/<feature>/     # auth, catalog, requests, shipments, marketplace, fleet, dashboard
│   ├── server.ts           #   llamadas a la API (solo servidor)
│   ├── actions.ts          #   Astro Actions (mutaciones), registradas en src/actions/index.ts
│   └── components/         #   islas React de la feature
├── components/ui/          # primitivas (button, input/select/field, card, badge, table, sheet, alert, skeleton)
├── components/domain/      # StatusPill, RouteLine, KpiTile, EmptyState, Countdown (React; sirven en SSR y en islas)
├── components/layout/      # AppShell (sidebar + topbar por rol), PageHeader, Logo
├── layouts/                # Base.astro, Public.astro
├── lib/api/                # schema.d.ts (GENERADO), client.ts (openapi-fetch + unwrap + ApiError), server.ts (tipos)
├── lib/session.ts          # cookie httpOnly, claims del JWT, homeFor(rol)
├── lib/actions.ts          # requireToken(ctx) y run(fn) → ApiError→ActionError con el mensaje en español de la API
├── lib/format.ts           # S/ 1,850.00 · 28 nov · 08:00 · etiquetas de enums
├── middleware.ts           # protege rutas, redirige por rol (PYME / CARRIER / DRIVER→/conductor), 401→/login?expired=1
└── styles/global.css       # tokens de DESIGN.md en @theme de Tailwind
```

## Cliente API
`npm run gen:api` regenera `src/lib/api/schema.d.ts` desde `../docs/api/openapi.yaml`. Úsalo así:
```ts
const data = await unwrap(apiFor(token).GET('/api/v1/vehicles'));   // desenvuelve {data}; lanza ApiError(status, code, message, field)
```
Si el backend cambia el contrato: `npm run gen:api` y corrige lo que rompa `npm run check`.

## Agregar una pantalla / feature (paso a paso)
1. `features/<f>/server.ts`: funciones que llaman a la API con `unwrap(apiFor(token)...)`.
2. Si hay mutaciones: `features/<f>/actions.ts` con `defineAction({ accept: 'json', input: z..., handler: (i, ctx) => run(() => fn(requireToken(ctx), i)) })` y regístralo en `src/actions/index.ts`.
3. Componentes interactivos en `features/<f>/components/*.tsx` (islas). Desde el cliente: `const { data, error } = await actions.<f>.<accion>(input)`; muestra `error.message` (ya viene en español).
4. Página en `src/pages/...astro`: obtén `Astro.locals.session!.token`, carga datos en el frontmatter, envuelve en `<AppShell title active>` + `<PageHeader>`, monta la isla con `client:load` (o `client:only="react"` si usa APIs del navegador, p. ej. mapas).
5. Agrega el enlace en `AppShell.astro` (objeto `NAV`) y, si es una ruta nueva de rol, revisa `middleware.ts`.
6. Estados vacío / carga / error en español (`EmptyState`, `Alert`). `npm run check && npm run build`.

Trampas conocidas:
- En `.astro`, `<Componente />` pasado como **prop** a un componente React NO es un elemento React (rompe el SSR con "htmlParts"). Pasa la referencia del componente (`icon={FileText}`) o usa slots/children.
- Componentes React usados en `.astro` reciben `className` (no `class`).
- Datos que cruzan a una isla deben ser serializables (sin funciones ni Date).
- MapLibre 6 necesita su worker servido por nosotros (`scripts/copy-maplibre-worker.mjs` → `public/maplibre/`, ignorado por git; `setWorkerUrl` en `TrackingMap.tsx`).
- Los formularios React llevan `method="post"` para que, si se envían antes de hidratar, las credenciales no acaben en la URL.

## Convenciones de UI (ver DESIGN.md)
- Tokens (Tailwind): `ink-900/700/500/400`, `steel-100/200`, `surface`, `petrol-700/800/50`, `emerald-500/700/50`, `amber-500/700/50`, `red-600/50`. `ink-500` y `emerald-700`/`amber-700` son variantes con contraste AA para texto (los hex de DESIGN.md sobre blanco no llegan a AA en texto pequeño).
- Dinero y oportunidad = emerald (`.money`: mono, tabulares, emerald-700). Cifras técnicas (placas, km, toneladas, montos en tabla) en `font-mono`.
- Radio: tarjetas `rounded-card` (10px), inputs `rounded-input` (8px), chips `rounded-full`.
- Siempre `StatusPill` para estados, `RouteLine` para rutas, `KpiTile` (máx. 4 por fila), `EmptyState` con camión.
- Formatos: `formatMoney` (`S/ 1,850.00`), `formatDateTime` (`28 nov · 08:00`, zona America/Lima), `formatDate`, `formatTons`, `formatKm`.
- Accesibilidad: todo input con `<Label>`/`Field`, errores con `role="alert"`, foco visible global, enlace "Saltar al contenido", navegación lateral colapsable en tablet/móvil.

## Pendientes conocidos
Modo oscuro, evidencia (foto/firma) de entrega, auto-refresco/polling de ofertas y tracking, mapa con ruta por carretera (hoy línea recta entre ciudades), paginación de listados (la API no la ofrece), tests e2e automatizados (el flujo se recorrió con Playwright ad hoc; capturas en `docs/screenshots/`).
