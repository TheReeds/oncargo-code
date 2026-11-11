import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';

export const prerender = false;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const text = (status: number, message: string) => new Response(message, { status, headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'private, no-store' } });

/** Proxy autenticado de un documento: añade el Bearer de la sesión y transmite el binario. `?download=1` fuerza la descarga. */
export const GET: APIRoute = async ({ params, url, locals }) => {
  const session = locals.session;
  if (!session) return text(401, 'Tu sesión expiró. Inicia sesión nuevamente.');
  const id = params.id ?? '';
  if (!UUID.test(id)) return text(404, 'No encontramos el documento.');

  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}/api/v1/documents/${id}/file`, { headers: { authorization: `Bearer ${session.token}` } });
  } catch {
    return text(502, 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (!upstream.ok) {
    const msg = upstream.status === 404 ? 'No encontramos el documento.' : upstream.status === 403 ? 'No tienes permiso para ver este documento.' : upstream.status === 401 ? 'Tu sesión expiró. Inicia sesión nuevamente.' : 'No pudimos obtener el documento.';
    return text(upstream.status, msg);
  }

  const headers = new Headers();
  headers.set('content-type', upstream.headers.get('content-type') ?? 'application/octet-stream');
  const len = upstream.headers.get('content-length');
  if (len) headers.set('content-length', len);
  // Conserva el nombre de archivo que envía la API; solo cambia inline/attachment.
  const disp = upstream.headers.get('content-disposition') ?? '';
  const rest = disp.replace(/^\s*(inline|attachment)\s*;?\s*/i, '');
  headers.set('content-disposition', `${url.searchParams.get('download') === '1' ? 'attachment' : 'inline'}${rest ? '; ' + rest : ''}`);
  headers.set('cache-control', 'private, no-store');
  headers.set('x-content-type-options', 'nosniff');
  return new Response(upstream.body, { status: 200, headers });
};
