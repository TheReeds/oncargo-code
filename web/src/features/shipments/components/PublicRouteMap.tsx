import { useEffect, useRef } from 'react';
import type { Map as MlMap, Marker } from 'maplibre-gl';
import { useResolvedTheme, type Theme } from '@/lib/theme';
import { boundsOf, ensureRoutePathLayer, routeEnds, routePathFeature, setRoutePaths } from '@/lib/routing';

const STYLES: Record<Theme, string> = {
  light: 'https://tiles.openfreemap.org/styles/positron',
  dark: 'https://tiles.openfreemap.org/styles/dark',
};
const ROUTE_COLOR: Record<Theme, string> = { light: '#0B4F6C', dark: '#38A3C9' };
const MARKER_FILL: Record<Theme, string> = { light: '#ffffff', dark: '#0B1220' };

/**
 * Mapa del seguimiento público (ADR-0011): dibuja la ruta por carretera que trae la propia respuesta pública (polyline,
 * ADR-0008), sin llamar a `/routes` (que exige sesión). Sin polyline muestra el mapa del corredor sin ruta.
 */
export default function PublicRouteMap({ polyline, precision = 5, label }: { polyline?: string | null; precision?: number; label: string }) {
  const el = useRef<HTMLDivElement>(null);
  const mapRef = useRef<MlMap | null>(null);
  const markers = useRef<Marker[]>([]);
  const ready = useRef(false);
  const theme = useResolvedTheme();
  const themeRef = useRef<Theme>(theme);
  themeRef.current = theme;
  const applied = useRef<Theme | null>(null);
  const latest = useRef({ polyline, precision });
  latest.current = { polyline, precision };

  function draw() {
    const map = mapRef.current;
    if (!map || !ready.current) return;
    import('maplibre-gl').then((ml) => {
      const { polyline, precision } = latest.current;
      const th = themeRef.current;
      const feature = routePathFeature({ id: 'public', selected: true, geometry: polyline ? { polyline, precision } : null });
      setRoutePaths(map, 'route', [feature]);
      markers.current.forEach((m) => m.remove());
      markers.current = [];
      const ends = routeEnds(feature);
      if (ends) {
        const mk = (c: [number, number], square: boolean) => {
          const d = document.createElement('div');
          d.style.cssText = `width:12px;height:12px;background:${square ? ROUTE_COLOR[th] : MARKER_FILL[th]};border:3px solid ${ROUTE_COLOR[th]};border-radius:${square ? '2px' : '50%'}`;
          return new ml.Marker({ element: d }).setLngLat(c).addTo(map);
        };
        markers.current.push(mk(ends.start as [number, number], false), mk(ends.end as [number, number], true));
      }
      const b = boundsOf([feature]);
      if (b) map.fitBounds(b, { padding: 48, maxZoom: 9, duration: 0 });
    });
  }

  useEffect(() => {
    let cancelled = false;
    import('maplibre-gl').then((ml) => {
      if (cancelled || !el.current) return;
      ml.setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');
      themeRef.current = document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
      applied.current = themeRef.current;
      const map = new ml.Map({ container: el.current, style: STYLES[themeRef.current], center: [-72.5, -14.5], zoom: 4.8, attributionControl: { compact: true } });
      mapRef.current = map;
      map.addControl(new ml.NavigationControl({ showCompass: false }), 'top-right');
      // Atribución de OpenStreetMap (ODbL) para la ruta: la de lib/routing, con enlace.
      const add = () => ensureRoutePathLayer(map, { id: 'route', color: ROUTE_COLOR[themeRef.current] });
      map.on('load', () => { add(); ready.current = true; draw(); });
      map.on('style.load', () => { if (!ready.current) return; add(); draw(); });
    });
    return () => { cancelled = true; mapRef.current?.remove(); mapRef.current = null; ready.current = false; };
  }, []);

  useEffect(draw, [polyline, precision]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready.current || applied.current === theme) return;
    applied.current = theme;
    map.setStyle(STYLES[theme]);
  }, [theme]);

  return <div ref={el} style={{ height: 'clamp(220px, 38vh, 380px)' }} className="w-full overflow-hidden rounded-card border border-steel-200 bg-steel-100" role="region" aria-label={label} />;
}
