import { useRealtimeRefresh } from '@/lib/live-region';

/**
 * Refresco suave de una página renderizada en el servidor (sin isla de datos): al llegar un evento de `types` (y, si se
 * indica, con `data[matchKey] === matchValue`) vuelve a pedir la misma URL y reemplaza el contenido de
 * `<div data-live-region="nombre">…</div>` sin recargar la página. No actúa mientras haya un diálogo abierto o el usuario
 * escriba en un campo de la región. Colócalo FUERA de la región. Polling de respaldo lento solo si no hay stream.
 */
export default function LiveRegion({ types, region = 'main', matchKey, matchValue, fallbackMs = 60_000 }: { types: string[]; region?: string; matchKey?: string; matchValue?: string; fallbackMs?: number }) {
  useRealtimeRefresh({ types, region, matchKey, matchValue, fallbackMs });
  return null;
}
