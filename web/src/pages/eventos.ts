import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';

export const prerender = false;

const plain = (status: number, message: string) =>
  new Response(message, { status, headers: { 'content-type': 'text/plain; charset=utf-8', 'cache-control': 'no-store' } });

/**
 * Proxy autenticado del stream de tiempo real (ADR-0007): el navegador abre `EventSource('/eventos')` (mismo origen,
 * `connect-src 'self'`), Astro añade el Bearer de la cookie httpOnly y transmite `GET /api/v1/events/stream` tal cual.
 * Reenvía `Last-Event-ID` (cabecera que pone EventSource al reconectar) o `?lastEventId=` (reconexión manual).
 * Si el navegador cierra, se aborta la petición al backend (libera la conexión y el cupo de 5 por usuario).
 */
export const GET: APIRoute = async ({ locals, request, url }) => {
  const session = locals.session;
  if (!session) return plain(401, 'Tu sesión expiró. Inicia sesión nuevamente.');

  const last = request.headers.get('last-event-id') ?? url.searchParams.get('lastEventId');
  const headers: Record<string, string> = { authorization: `Bearer ${session.token}`, accept: 'text/event-stream' };
  if (last && /^\d{1,19}$/.test(last)) headers['last-event-id'] = last;

  const abort = new AbortController();
  request.signal?.addEventListener('abort', () => abort.abort(), { once: true });

  let upstream: Response;
  try {
    upstream = await fetch(`${API_URL}/api/v1/events/stream`, { headers, signal: abort.signal });
  } catch {
    return plain(502, 'No pudimos conectar con el servidor en vivo.');
  }
  if (!upstream.ok || !upstream.body) {
    upstream.body?.cancel().catch(() => {});
    return plain(upstream.status === 401 || upstream.status === 429 ? upstream.status : 502,
      upstream.status === 429 ? 'Demasiadas conexiones en vivo abiertas.' : 'No pudimos abrir la conexión en vivo.');
  }

  // Si el navegador corta, la lectura del body se cancela: abortamos también la petición al backend.
  const reader = upstream.body.getReader();
  const body = new ReadableStream<Uint8Array>({
    async pull(controller) {
      try {
        const { done, value } = await reader.read();
        if (done) controller.close();
        else controller.enqueue(value);
      } catch {
        controller.close();
      }
    },
    cancel() {
      abort.abort();
      reader.cancel().catch(() => {});
    },
  });

  return new Response(body, {
    status: 200,
    headers: {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-store, no-transform',
      'x-accel-buffering': 'no',
      connection: 'keep-alive',
    },
  });
};
