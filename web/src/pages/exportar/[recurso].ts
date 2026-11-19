import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';
import { apiQuery, filtersFromUrl } from '@/features/requests/filters';

export const prerender = false;
const text = (status: number, message: string) => new Response(message, { status, headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'private, no-store' } });

/**
 * Exportaciones CSV: proxy autenticado (cookie → Bearer), como `/documentos/[id]`. Recibe los mismos parámetros de URL
 * que la página del listado (`estado`, `origen`, `destino`, `desde`, `hasta`, `q`, `orden`) y los traduce a la API, así
 * el CSV es exactamente lo que se ve. Para exportar otro recurso agrega su entrada a `RESOURCES`.
 */
const RESOURCES: Record<string, { path: string; query: (p: URLSearchParams) => URLSearchParams }> = {
  solicitudes: {
    path: '/api/v1/shipment-requests/mine/export',
    query: (p) => {
      const q = apiQuery(filtersFromUrl(p));
      const out = new URLSearchParams();
      for (const [k, v] of Object.entries(q)) if (v !== undefined && v !== '') out.set(k, String(v));
      return out;
    },
  },
};

export const GET: APIRoute = async ({ params, url, locals }) => {
  const session = locals.session;
  if (!session) return text(401, 'Tu sesión expiró. Inicia sesión nuevamente.');
  const resource = RESOURCES[params.recurso ?? ''];
  if (!resource) return text(404, 'No existe esa exportación.');
  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}${resource.path}?${resource.query(url.searchParams)}`, { headers: { authorization: `Bearer ${session.token}`, accept: 'text/csv' } });
  } catch {
    return text(502, 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (!upstream.ok) {
    let message = upstream.status === 401 ? 'Tu sesión expiró. Inicia sesión nuevamente.' : upstream.status === 403 ? 'No tienes permiso para exportar esto.' : 'No pudimos generar el archivo.';
    // La API explica en español por qué (p. ej. más de 10 000 filas: «acota por estado, ciudad o fechas»).
    if (upstream.status === 400) { try { message = ((await upstream.json()) as { error?: { message?: string } }).error?.message ?? message; } catch { /* texto genérico */ } }
    return text(upstream.status, message);
  }
  const headers = new Headers();
  headers.set('content-type', 'text/csv; charset=utf-8');
  headers.set('content-disposition', upstream.headers.get('content-disposition') ?? 'attachment; filename="exportacion.csv"');
  headers.set('cache-control', 'private, no-store');
  headers.set('x-content-type-options', 'nosniff');
  return new Response(upstream.body, { status: 200, headers });
};
