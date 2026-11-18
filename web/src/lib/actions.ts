import { ActionError, type ActionAPIContext } from 'astro:actions';
import { ApiError } from './api/client';
import { encodeActionMeta } from './action-error';

export { FIELD_SEP } from './action-error';

/** Exige sesión en una action y devuelve el token. */
export function requireToken(ctx: ActionAPIContext): string {
  const s = ctx.locals.session;
  if (!s) throw new ActionError({ code: 'UNAUTHORIZED', message: 'Tu sesión expiró. Inicia sesión nuevamente.' });
  return s.token;
}

/**
 * Ejecuta una llamada a la API y convierte ApiError en ActionError (mensaje en español de la API).
 * El `field` y el código `LOGYX-XXX-NNN` viajan anexos e invisibles en el mensaje: léelos con `splitActionError()`.
 */
export async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof ApiError) {
      const code = e.status === 401 ? 'UNAUTHORIZED' : e.status === 403 ? 'FORBIDDEN' : e.status === 404 ? 'NOT_FOUND' : e.status >= 500 || e.status === 0 ? 'INTERNAL_SERVER_ERROR' : 'BAD_REQUEST';
      throw new ActionError({ code, message: `${e.message}${encodeActionMeta({ field: e.field, code: e.code?.startsWith('LOGYX-') ? e.code : null })}` });
    }
    throw e;
  }
}

/** IP del navegador (para X-Forwarded-For hacia la API). `clientAddress` lanza si el adaptador no la conoce. */
export function clientIpOf(ctx: ActionAPIContext): string | undefined {
  try {
    return ctx.clientAddress || undefined;
  } catch {
    return undefined;
  }
}
