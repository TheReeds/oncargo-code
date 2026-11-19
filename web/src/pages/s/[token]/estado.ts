import type { APIRoute } from 'astro';
import { publicTracking } from '@/features/shipments/server';

export const prerender = false;

const headers = { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-robots-tag': 'noindex, nofollow', 'referrer-policy': 'no-referrer' };

/**
 * Consulta periódica del seguimiento público (cada 60 s desde `PublicTracking`): proxy sin sesión hacia
 * `GET /api/v1/public/tracking/{token}`, reenviando la IP del visitante (el límite del backend es por IP). Responde solo
 * el `data` de la API (lista blanca) o 404 sin detalle.
 */
export const GET: APIRoute = async ({ params, clientAddress }) => {
  let ip: string | undefined;
  try { ip = clientAddress; } catch { ip = undefined; }
  const { data, status } = await publicTracking(params.token ?? '', ip);
  if (!data) return new Response('{}', { status: status === 429 ? 429 : status === 502 ? 503 : 404, headers });
  return new Response(JSON.stringify(data), { status: 200, headers });
};
