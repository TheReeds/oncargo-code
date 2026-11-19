/**
 * Filtros de «Envíos» (PYME y transportista, roadmap 1.5) tal como viajan en la URL de la página y del CSV
 * (`/exportar/envios`). `estado` vacío = «En curso» (por recoger + en tránsito); `todos` = sin filtro de estado.
 */
export type ShipmentFilters = { status: string; origin: string; dest: string; from: string; to: string; q: string };
export type ShipmentsQuery = { status?: string; originCity?: string; destinationCity?: string; fromDate?: string; toDate?: string; q?: string; page?: number; size?: number };

export const SHIPMENTS_PAGE_SIZE = 20;
export const ACTIVE_STATUSES = 'PENDING,IN_TRANSIT';

export function filtersFromUrl(params: URLSearchParams): ShipmentFilters {
  const get = (k: string) => params.get(k) ?? '';
  return { status: get('estado'), origin: get('origen'), dest: get('destino'), from: get('desde'), to: get('hasta'), q: get('q').trim().slice(0, 80) };
}

export function pageFromUrl(params: URLSearchParams): number {
  const n = Number(params.get('pagina') ?? '1');
  return Number.isInteger(n) && n >= 1 ? n : 1;
}

const STATUS_PARAM: Record<string, string | undefined> = {
  '': ACTIVE_STATUSES, todos: undefined, PENDING: 'PENDING', IN_TRANSIT: 'IN_TRANSIT', DELIVERED: 'DELIVERED', CANCELLED: 'CANCELLED',
};

/** URL → parámetros de la API (`GET /shipments` y `/shipments/export`). */
export function apiQuery(f: ShipmentFilters): ShipmentsQuery {
  return {
    status: f.status in STATUS_PARAM ? STATUS_PARAM[f.status] : ACTIVE_STATUSES,
    originCity: f.origin || undefined, destinationCity: f.dest || undefined,
    fromDate: f.from || undefined, toDate: (f.to || f.from) || undefined,
    q: f.q || undefined,
  };
}

/** Filtros → parámetros de la URL de la página (y del enlace de exportación). */
export function toUrlQuery(f: ShipmentFilters, page = 1, id?: string | null): URLSearchParams {
  const q = new URLSearchParams();
  if (f.status) q.set('estado', f.status);
  if (f.origin) q.set('origen', f.origin);
  if (f.dest) q.set('destino', f.dest);
  if (f.from) { q.set('desde', f.from); if (f.to) q.set('hasta', f.to); }
  if (f.q) q.set('q', f.q);
  if (page > 1) q.set('pagina', String(page));
  if (id) q.set('id', id);
  return q;
}

/** Hay algún filtro fino aplicado (además de la pestaña de estado). */
export const hasFineFilters = (f: ShipmentFilters) => !!(f.origin || f.dest || f.from || f.q);
