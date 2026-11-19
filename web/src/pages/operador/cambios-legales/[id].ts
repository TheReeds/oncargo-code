import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';

export const prerender = false;

/** Descarga del documento de sustento de un cambio legal (2.15): solo el operador; el token no sale del servidor. */
export const GET: APIRoute = async ({ locals, params }) => {
  const session = locals.session;
  if (!session || session.orgType !== 'OPERATOR') return new Response('No autorizado', { status: 403 });
  const id = String(params.id ?? '');
  if (!/^[0-9a-f-]{36}$/i.test(id)) return new Response('No encontrado', { status: 404 });
  const res = await fetch(`${API_URL}/api/v1/operator/legal-changes/${id}/file`, { headers: { authorization: `Bearer ${session.token}`, accept: '*/*' } }).catch(() => null);
  if (!res || !res.ok || !res.body) return new Response('No encontrado', { status: res?.status === 403 ? 403 : 404 });
  const headers = new Headers({ 'cache-control': 'private, no-store', 'x-content-type-options': 'nosniff' });
  for (const h of ['content-type', 'content-disposition', 'content-length']) { const v = res.headers.get(h); if (v) headers.set(h, v); }
  return new Response(res.body, { status: 200, headers });
};
