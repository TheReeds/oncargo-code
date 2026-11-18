import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { RouteGeometry } from '@/lib/routing';

export type RouteView = Schemas['RouteView'];

/** La ruta entre ciudades del catálogo es un dato estático (dataset del backend, ADR-0008): se cachea en memoria. */
const cache = new Map<string, { at: number; route: RouteView }>();
const TTL_MS = 6 * 60 * 60_000;
const MAX_ENTRIES = 500;

export async function getRoute(token: string, stops: string[], optimize = false): Promise<RouteView> {
  const key = `${optimize ? 'opt:' : ''}${stops.join(',')}`;
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return hit.route;
  const route = await unwrap(apiFor(token).GET('/api/v1/routes', { params: { query: { stops: stops.join(','), optimize } } }));
  if (cache.size >= MAX_ENTRIES) cache.delete(cache.keys().next().value!);
  cache.set(key, { at: Date.now(), route });
  return route;
}

/** RouteView (contrato) → RouteGeometry (utilidad de mapa): `polylinePrecision` → `precision`. */
export function toRouteGeometry(r: RouteView): RouteGeometry {
  return {
    polyline: r.polyline ?? '',
    precision: r.polylinePrecision ?? 5,
    distanceKm: r.distanceKm,
    durationMin: r.durationMin,
    attribution: r.attribution,
  };
}
