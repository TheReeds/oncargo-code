import { useEffect, useRef } from 'react';
import type { Map as MlMap, Marker } from 'maplibre-gl';
import type { CityItem, ShipmentView } from '@/lib/api/server';

import { useResolvedTheme, type Theme } from '@/lib/theme';
import { boundsOf, ensureRoutePathLayer, routePathFeature, setRoutePaths } from '@/lib/routing';
import { routeKey, useRouteGeometries } from '@/features/routing/use-route-geometries';

const STYLES: Record<Theme, string> = {
  light: 'https://tiles.openfreemap.org/styles/positron',
  dark: 'https://tiles.openfreemap.org/styles/dark',
};
const ROUTE_COLOR: Record<Theme, string> = { light: '#0B4F6C', dark: '#38A3C9' };
const MARKER_FILL: Record<Theme, string> = { light: '#ffffff', dark: '#0B1220' };

/** Ciudades de la ruta de un envío (paradas en orden; si no hay, origen→destino), sin repetir consecutivas. */
export function routeStops(s: ShipmentView): string[] {
  const fromStops = (s.stops ?? []).slice().sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).map((st) => st.city!).filter(Boolean);
  const list = fromStops.length >= 2 ? fromStops : [s.originCity!, s.destinationCity!].filter(Boolean);
  return list.filter((c, i) => i === 0 || c !== list[i - 1]);
}

/** Coordenadas de los centros de las ciudades de la ruta (respaldo en línea recta y marcadores). */
export function routeCoords(s: ShipmentView, cities: Record<string, CityItem>): [number, number][] {
  return routeStops(s).map((code) => cities[code]).filter(Boolean).map((c) => [c.lng!, c.lat!] as [number, number]);
}

/**
 * Mapa MapLibre (OpenFreeMap positron/dark). Dibuja una o varias rutas por carretera (`GET /api/v1/routes`, ADR-0008);
 * mientras carga o si falla, la recta punteada entre ciudades. La seleccionada va resaltada.
 */
export default function TrackingMap({ shipments, cities, selectedId, height = 420 }: { shipments: ShipmentView[]; cities: CityItem[]; selectedId?: string | null; height?: number | string }) {
  const el = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MlMap | null>(null);
  const markers = useRef<Marker[]>([]);
  const readyRef = useRef(false);
  const theme = useResolvedTheme();
  const themeRef = useRef<Theme>(theme);
  themeRef.current = theme;
  const appliedTheme = useRef<Theme | null>(null);
  const geoms = useRouteGeometries(shipments.map(routeStops));
  const latest = useRef({ shipments, selectedId, geoms });
  latest.current = { shipments, selectedId, geoms };
  const byCode = Object.fromEntries(cities.map((c) => [c.code!, c]));

  function draw() {
    const map = mapRef.current;
    if (!map || !readyRef.current) return;
    import('maplibre-gl').then((ml) => {
      const { shipments, selectedId, geoms } = latest.current;
      const features = shipments.map((s) => routePathFeature({
        id: s.id!,
        selected: s.id === selectedId || shipments.length === 1,
        geometry: geoms[routeKey(routeStops(s))],
        fallback: routeCoords(s, byCode),
      }));
      const th = themeRef.current;
      const PETROL = ROUTE_COLOR[th];
      setRoutePaths(map, 'routes', features);
      markers.current.forEach((m) => m.remove());
      markers.current = [];
      const focus = features.filter((f) => !selectedId || f.properties.id === selectedId);
      for (const s of shipments) {
        const coords = routeCoords(s, byCode);
        if (coords.length < 2) continue;
        const sel = s.id === selectedId || shipments.length === 1;
        const mk = (c: [number, number], square: boolean) => {
          const d = document.createElement('div');
          d.style.cssText = `width:12px;height:12px;background:${square ? PETROL : MARKER_FILL[th]};border:3px solid ${PETROL};border-radius:${square ? '2px' : '50%'};opacity:${sel ? 1 : 0.55}`;
          return new ml.Marker({ element: d }).setLngLat(c).addTo(map);
        };
        markers.current.push(mk(coords[0], false), mk(coords[coords.length - 1], true));
      }
      const bounds = boundsOf(focus);
      if (bounds) map.fitBounds(bounds, { padding: 56, maxZoom: 9, duration: 500 });
    });
  }

  useEffect(() => {
    let cancelled = false;
    import('maplibre-gl').then((ml) => {
      if (cancelled || !el.current) return;
      ml.setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');
      themeRef.current = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
      appliedTheme.current = themeRef.current;
      const map = new ml.Map({ container: el.current, style: STYLES[themeRef.current], center: [-74, -14], zoom: 4.5, attributionControl: { compact: true } });
      mapRef.current = map;
      map.addControl(new ml.NavigationControl({ showCompass: false }), 'top-right');
      // Fuente + capa de rutas (idempotente; con atribución de OSM para las rutas por carretera).
      const addRoutes = () => ensureRoutePathLayer(map, { id: 'routes', color: ROUTE_COLOR[themeRef.current] });
      map.on('load', () => {
        addRoutes();
        readyRef.current = true;
        draw();
      });
      // Cambio de tema: nuevo estilo → se vuelven a agregar la fuente/capa y los marcadores.
      map.on('style.load', () => {
        if (!readyRef.current) return;
        addRoutes();
        draw();
      });
    });
    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
      readyRef.current = false;
    };
  }, []);

  useEffect(draw, [shipments, selectedId, cities, geoms]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !readyRef.current || appliedTheme.current === theme) return;
    appliedTheme.current = theme;
    map.setStyle(STYLES[theme]);
  }, [theme]);

  return <div ref={el} style={{ height }} className="w-full overflow-hidden rounded-card border border-steel-200 bg-steel-100" role="region" aria-label="Mapa de la ruta del envío" />;
}
