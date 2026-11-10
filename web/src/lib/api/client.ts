import createClient from 'openapi-fetch';
import type { paths } from './schema';

export const API_URL: string = import.meta.env.API_URL ?? process.env.API_URL ?? 'http://localhost:8080';

/** Error normalizado de la API: `{ error: { code, message, field } }`. */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public field?: string | null,
  ) {
    super(message);
  }
}

export function apiClient(token?: string) {
  return createClient<paths>({
    baseUrl: API_URL,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
}

type Envelope<T> = { data?: T };
type Result<T> = { data?: Envelope<T>; error?: unknown; response: Response };

const FALLBACK: Record<number, string> = {
  401: 'Tu sesión expiró. Inicia sesión nuevamente.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'No encontramos lo que buscas.',
};

/** Desenvuelve `data` o lanza ApiError en español. */
export async function unwrap<T>(p: Promise<Result<T>>): Promise<T> {
  let res: Result<T>;
  try {
    res = await p;
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    const status = res.response.status;
    throw new ApiError(status, e.error?.code ?? `HTTP-${status}`, e.error?.message ?? FALLBACK[status] ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  return (res.data as Envelope<T>).data as T;
}
