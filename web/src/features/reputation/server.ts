import { apiFor } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type ReviewView = Schemas['ReviewView'];
export type ReputationView = Schemas['ReputationView'];
export type ShipmentReviewsView = Schemas['ShipmentReviewsView'];

export const getShipmentReviews = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipments/{id}/reviews', { params: { path: { id } } }));

export const submitReview = (token: string, id: string, body: Schemas['SubmitReview']) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/reviews', { params: { path: { id } }, body }));

export const getReputation = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/organizations/{id}/reputation', { params: { path: { id } } }));

/** Calificaciones recibidas por una organización. `page` base 0 (como la API). */
export async function listOrgReviews(token: string, id: string, page = 0, size = 10): Promise<{ items: ReviewView[]; page: number; size: number; total: number }> {
  let res;
  try {
    res = await apiFor(token).GET('/api/v1/organizations/{id}/reviews', { params: { path: { id }, query: { page, size } } });
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  const body = res.data as { data?: ReviewView[]; meta?: { page?: number; size?: number; total?: number } };
  const items = body.data ?? [];
  return { items, page: body.meta?.page ?? page, size: body.meta?.size ?? size, total: body.meta?.total ?? items.length };
}
