/**
 * Pintar rutas por carretera en MapLibre (ADR-0008). Utilidad sin React: la usan los mapas existentes
 * (`TrackingMap`) en la fase 2 en lugar de la línea recta entre ciudades.
 *
 * Uso:
 *   ensureRoutePathLayer(map, { id: 'routes', color: ROUTE_COLOR[theme] });   // en 'load' y en cada 'style.load'
 *   setRoutePaths(map, 'routes', shipments.map((s) => routePathFeature({
 *     id: s.id, selected: s.id === selectedId, geometry: s.route, fallback: routeCoords(s, byCode) })));
 *   map.fitBounds(boundsOf(features), { padding: 56, maxZoom: 9 });
 *
 * Si la geometría no llegó (o viene vacía), se dibuja la línea recta entre paradas: nunca un mapa sin ruta.
 */
import type { Feature, LineString } from 'geojson';
import type { GeoJSONSource, LngLatBoundsLike, Map as MlMap } from 'maplibre-gl';
import { decodePolyline, type LngLat } from './polyline';

/**
 * Geometría de una ruta. Sale de `GET /api/v1/routes` vía `features/routing` (`toRouteGeometry` mapea
 * `polylinePrecision` → `precision`).
 */
export interface RouteGeometry {
  /** Polyline codificada del recorrido completo (todas las paradas). */
  polyline: string;
  /** Precisión de la polyline (5 por omisión). */
  precision?: number;
  distanceKm?: number;
  durationMin?: number;
  /** Atribución de los datos (ODbL de OpenStreetMap). */
  attribution?: string;
}

/** Atribución de las rutas (datos de OpenStreetMap, ODbL). Se muestra en el control de atribución del mapa. */
export const OSM_ATTRIBUTION = 'Rutas © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';

export interface RoutePathProps {
  id: string;
  /** 1 = resaltada (ruta seleccionada), 0 = secundaria. */
  sel: 0 | 1;
  /** true si se dibujó recta por falta de geometría (útil para estilos o avisos). */
  approx: boolean;
}

export type RoutePathFeature = Feature<LineString, RoutePathProps>;

/** Coordenadas de una geometría codificada; `fallback` (p. ej. ciudades en orden) si no hay geometría válida. */
export function routePathCoords(geometry: RouteGeometry | string | null | undefined, fallback: LngLat[] = []): { coords: LngLat[]; approx: boolean } {
  const g = typeof geometry === 'string' ? { polyline: geometry } : geometry;
  if (g?.polyline) {
    try {
      const coords = decodePolyline(g.polyline, g.precision ?? 5);
      if (coords.length >= 2) return { coords, approx: false };
    } catch {
      // Geometría corrupta: se cae a la recta.
    }
  }
  return { coords: fallback, approx: true };
}

export function routePathFeature(opts: { id: string; selected?: boolean; geometry?: RouteGeometry | string | null; fallback?: LngLat[] }): RoutePathFeature {
  const { coords, approx } = routePathCoords(opts.geometry, opts.fallback);
  return {
    type: 'Feature',
    properties: { id: opts.id, sel: opts.selected ? 1 : 0, approx },
    geometry: { type: 'LineString', coordinates: coords },
  };
}

export interface RoutePathLayerOptions {
  id: string;
  color: string;
  /** Ancho de la ruta resaltada / secundaria (px). */
  width?: [number, number];
  /** Opacidad de la ruta resaltada / secundaria. */
  opacity?: [number, number];
  /** Capa del estilo debajo de la cual insertar (p. ej. etiquetas). */
  beforeId?: string;
  /** Atribución de la fuente (por omisión {@link OSM_ATTRIBUTION}). */
  attribution?: string;
}

/**
 * Agrega (si falta) la fuente GeoJSON y la capa de línea. Idempotente: llámala en `load` y tras cada `setStyle`
 * (evento `style.load`), porque cambiar de estilo borra fuentes y capas propias.
 */
export function ensureRoutePathLayer(map: MlMap, o: RoutePathLayerOptions): void {
  const [wSel, wOther] = o.width ?? [4, 2];
  const [opSel, opOther] = o.opacity ?? [1, 0.45];
  if (!map.getSource(o.id)) {
    map.addSource(o.id, { type: 'geojson', data: { type: 'FeatureCollection', features: [] }, attribution: o.attribution ?? OSM_ATTRIBUTION });
  }
  if (!map.getLayer(o.id)) {
    map.addLayer(
      {
        id: o.id,
        type: 'line',
        source: o.id,
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: {
          'line-color': o.color,
          'line-width': ['case', ['==', ['get', 'sel'], 1], wSel, wOther],
          'line-opacity': ['case', ['==', ['get', 'sel'], 1], opSel, opOther],
          // Ruta aproximada (sin geometría): punteada, para no fingir precisión.
          'line-dasharray': ['case', ['get', 'approx'], ['literal', [2, 1.5]], ['literal', [1, 0]]],
        },
      },
      o.beforeId,
    );
  } else {
    map.setPaintProperty(o.id, 'line-color', o.color);
  }
}

/** Reemplaza las rutas dibujadas. La seleccionada se pinta al final (encima). */
export function setRoutePaths(map: MlMap, id: string, features: RoutePathFeature[]): void {
  const src = map.getSource(id) as GeoJSONSource | undefined;
  if (!src) return;
  const ordered = [...features].sort((a, b) => a.properties.sel - b.properties.sel);
  src.setData({ type: 'FeatureCollection', features: ordered.filter((f) => f.geometry.coordinates.length >= 2) });
}

/** Caja que contiene las rutas dadas (o null si no hay puntos), para `map.fitBounds`. */
export function boundsOf(features: RoutePathFeature[]): LngLatBoundsLike | null {
  let minLng = Infinity;
  let minLat = Infinity;
  let maxLng = -Infinity;
  let maxLat = -Infinity;
  for (const f of features) {
    for (const [lng, lat] of f.geometry.coordinates) {
      if (lng < minLng) minLng = lng;
      if (lat < minLat) minLat = lat;
      if (lng > maxLng) maxLng = lng;
      if (lat > maxLat) maxLat = lat;
    }
  }
  return Number.isFinite(minLng) ? [[minLng, minLat], [maxLng, maxLat]] : null;
}

/** Inicio y fin de una ruta (para los marcadores de origen y destino). */
export function routeEnds(f: RoutePathFeature): { start: LngLat; end: LngLat } | null {
  const c = f.geometry.coordinates as LngLat[];
  return c.length >= 2 ? { start: c[0], end: c[c.length - 1] } : null;
}
