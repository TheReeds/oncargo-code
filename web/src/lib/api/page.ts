import { ApiError } from './client';

export type PageMeta = { page: number; size: number; total: number };
export type Paged<T> = { items: T[]; meta: PageMeta };

/** Como `unwrap`, pero conserva `meta` (page 0-based, size, total) de los listados paginados. */
export async function unwrapPage<T>(p: Promise<{ data?: { data?: T[]; meta?: { page?: number; size?: number; total?: number } }; error?: unknown; response: Response }>): Promise<Paged<T>> {
  let res;
  try {
    res = await p;
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  const body = res.data!;
  const items = body.data ?? [];
  return { items, meta: { page: body.meta?.page ?? 0, size: body.meta?.size ?? items.length, total: body.meta?.total ?? items.length } };
}
