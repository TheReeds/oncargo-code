import type { APIRoute } from 'astro';
import { unsubscribe, isUnsubscribeToken } from '@/features/notifications/unsubscribe';

export const prerender = false;

/**
 * One-click de `List-Unsubscribe` (RFC 8058, V24): el cliente de correo hace `POST /baja/one-click?t=<token>` con el
 * cuerpo `List-Unsubscribe=One-Click` y sin cookies. Se reenvía al backend (baja de la categoría del enlace). Responde
 * sin contenido y no revela si el token era válido. Un `GET` (cliente que abre el enlace) va a la página `/baja`.
 */
export const POST: APIRoute = async ({ url, request, clientAddress }) => {
  const token = url.searchParams.get('t')?.trim() ?? '';
  const body = await request.text().catch(() => '');
  if (!isUnsubscribeToken(token) || !/List-Unsubscribe=One-Click/i.test(body)) {
    return new Response(null, { status: 400, headers: { 'Cache-Control': 'no-store' } });
  }
  let ip: string | undefined;
  try { ip = clientAddress || undefined; } catch { ip = undefined; }
  const r = await unsubscribe(token, 'CATEGORY', ip);
  // 200 si se aplicó; el resto igual devuelve 200/202 sin detalle (el cliente de correo no muestra nada al usuario).
  return new Response(null, { status: r.data ? 200 : r.status === 429 ? 429 : 202, headers: { 'Cache-Control': 'no-store', 'X-Robots-Tag': 'noindex' } });
};

export const GET: APIRoute = ({ url, redirect }) => {
  const t = url.searchParams.get('t') ?? '';
  return redirect(`/baja${isUnsubscribeToken(t) ? `?t=${encodeURIComponent(t)}` : ''}`, 303);
};
