import { useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import type { RouteGeometry } from '@/lib/routing';

/** Caché por pestaña: la ruta entre ciudades del catálogo no cambia (ADR-0008). `null` = no se pudo obtener. */
const cache = new Map<string, Promise<RouteGeometry | null>>();

export const routeKey = (stops: string[]) => stops.join(',');

function fetchRoute(stops: string[]): Promise<RouteGeometry | null> {
  const key = routeKey(stops);
  let p = cache.get(key);
  if (!p) {
    p = actions.routing
      .route({ stops })
      .then(({ data, error }) => (error || !data?.polyline ? null : data))
      .catch(() => null);
    // Un fallo no se cachea para siempre: se reintenta en el siguiente montaje.
    p.then((g) => { if (!g) cache.delete(key); });
    cache.set(key, p);
  }
  return p;
}

/**
 * Geometrías por carretera de varias rutas (lista de códigos de ciudad en orden de visita), indexadas por
 * {@link routeKey}. Mientras cargan (o si fallan) la clave no está y el mapa dibuja la recta de respaldo.
 */
export function useRouteGeometries(routes: string[][]): Record<string, RouteGeometry> {
  // Solo cambia cuando cambian las rutas (no en cada render con un arreglo nuevo).
  const signature = routes.map(routeKey).join('|');
  const valid = useMemo(() => {
    const uniq = new Map<string, string[]>();
    for (const r of routes) if (r.length >= 2) uniq.set(routeKey(r), r);
    return [...uniq.values()];
  }, [signature]);
  const [geoms, setGeoms] = useState<Record<string, RouteGeometry>>({});

  useEffect(() => {
    let alive = true;
    for (const stops of valid) {
      const key = routeKey(stops);
      fetchRoute(stops).then((g) => {
        if (alive && g) setGeoms((prev) => (prev[key] === g ? prev : { ...prev, [key]: g }));
      });
    }
    return () => { alive = false; };
  }, [valid]);

  return geoms;
}
