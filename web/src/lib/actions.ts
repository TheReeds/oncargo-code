import { ActionError, type ActionAPIContext } from 'astro:actions';
import { ApiError } from './api/client';

/** Exige sesión en una action y devuelve el token. */
export function requireToken(ctx: ActionAPIContext): string {
  const s = ctx.locals.session;
  if (!s) throw new ActionError({ code: 'UNAUTHORIZED', message: 'Tu sesión expiró. Inicia sesión nuevamente.' });
  return s.token;
}

/** Ejecuta una llamada a la API y convierte ApiError en ActionError (mensaje en español de la API). */
export async function run<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof ApiError) {
      const code = e.status === 401 ? 'UNAUTHORIZED' : e.status === 403 ? 'FORBIDDEN' : e.status === 404 ? 'NOT_FOUND' : e.status >= 500 || e.status === 0 ? 'INTERNAL_SERVER_ERROR' : 'BAD_REQUEST';
      throw new ActionError({ code, message: e.field ? `${e.message}` : e.message });
    }
    throw e;
  }
}
