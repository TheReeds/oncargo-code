// MapLibre 6 carga su worker desde una URL junto al bundle; al empaquetar con Vite ese archivo no existe.
// Lo servimos desde /maplibre/ (public) y lo registramos con setWorkerUrl en TrackingMap.
import { cpSync, mkdirSync } from 'node:fs';
const src = 'node_modules/maplibre-gl/dist';
mkdirSync('public/maplibre', { recursive: true });
for (const f of ['maplibre-gl-worker.mjs', 'maplibre-gl-shared.mjs']) cpSync(`${src}/${f}`, `public/maplibre/${f}`);
