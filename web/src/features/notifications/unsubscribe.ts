import { apiClient, unwrap, ApiError } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

/** Lista blanca que devuelve la API para la página pública `/baja` (V24, 2.2). */
export type UnsubscribeView = Schemas['UnsubscribeView'];

/** Forma del token de baja (`base64url.base64url`, HMAC del backend): lo demás ni se envía. */
export const isUnsubscribeToken = (t: string | null | undefined): t is string => !!t && t.length <= 200 && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(t);

type Outcome = { data: UnsubscribeView | null; status: number };

async function call(fn: () => Promise<UnsubscribeView | undefined>): Promise<Outcome> {
  try {
    const data = await fn();
    return { data: data ?? null, status: 200 };
  } catch (e) {
    return { data: null, status: e instanceof ApiError ? e.status : 502 };
  }
}

/**
 * Qué da de baja el enlace (categoría y email enmascarado). Sin sesión: reenvía la IP del visitante (`X-Forwarded-For`),
 * el límite del backend es por IP. `status` 404 = enlace inválido (misma respuesta para cualquier token malo).
 */
export const inspectUnsubscribe = (token: string, clientIp?: string) =>
  isUnsubscribeToken(token) ? call(() => unwrap(apiClient(undefined, clientIp).POST('/api/v1/public/unsubscribe/inspect', { body: { token } })))
    : Promise.resolve<Outcome>({ data: null, status: 404 });

/** Baja de la categoría del enlace (`CATEGORY`) o de todo el correo de avisos (`ALL`). Idempotente. */
export const unsubscribe = (token: string, scope: 'CATEGORY' | 'ALL', clientIp?: string) =>
  isUnsubscribeToken(token) ? call(() => unwrap(apiClient(undefined, clientIp).POST('/api/v1/public/unsubscribe', { body: { token, scope } })))
    : Promise.resolve<Outcome>({ data: null, status: 404 });
