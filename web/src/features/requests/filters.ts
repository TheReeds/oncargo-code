import type { MyRequestsQuery } from './server';

/** Filtros de «Mis solicitudes» tal como viajan en la URL de la página (y de la exportación CSV). */
export function filtersFromUrl(params: URLSearchParams) {
  const get = (k: string) => params.get(k) ?? '';
  return { status: get('estado'), origin: get('origen'), dest: get('destino'), from: get('desde'), to: get('hasta'), q: get('q').trim(), sort: get('orden') || 'recent' };
}

/** URL → parámetros de la API (`estado=REVIEW` = publicadas con ofertas por elegir). */
export function apiQuery(f: ReturnType<typeof filtersFromUrl>): MyRequestsQuery {
  return {
    status: f.status && f.status !== 'REVIEW' ? f.status : undefined,
    withBids: f.status === 'REVIEW' ? true : undefined,
    originCity: f.origin || undefined, destinationCity: f.dest || undefined,
    fromDate: f.from || undefined, toDate: (f.to || f.from) || undefined,
    q: f.q || undefined, sort: f.sort !== 'recent' ? f.sort : undefined,
  };
}
