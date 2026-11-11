import { apiFor } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type ChatThread = Schemas['ChatThreadView'];
export type ChatMessage = Schemas['MessageView'];
export type CounterOffer = Schemas['CounterOfferView'];

export const listThreads = (token: string, requestId: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/threads', { params: { path: { id: requestId } } }));

/** Mensajes en orden cronológico; `before` = `nextCursor` de la página anterior. */
export async function listMessages(token: string, requestId: string, carrierOrgId: string, before?: string, size = 30): Promise<{ items: ChatMessage[]; nextCursor: string | null }> {
  let res;
  try {
    res = await apiFor(token).GET('/api/v1/shipment-requests/{id}/threads/{carrierOrgId}/messages', {
      params: { path: { id: requestId, carrierOrgId }, query: { before, size } },
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  const body = res.data as { data?: ChatMessage[]; meta?: { nextCursor?: string } };
  return { items: body.data ?? [], nextCursor: body.meta?.nextCursor ?? null };
}

export const postMessage = (token: string, requestId: string, carrierOrgId: string, body: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/threads/{carrierOrgId}/messages', { params: { path: { id: requestId, carrierOrgId } }, body: { body } }));

export const listCounterOffers = (token: string, bidId: string) =>
  unwrap(apiFor(token).GET('/api/v1/bids/{id}/counter-offers', { params: { path: { id: bidId } } }));
export const createCounterOffer = (token: string, bidId: string, body: Schemas['ProposeCounterOffer']) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/counter-offers', { params: { path: { id: bidId } }, body }));
export const acceptCounterOffer = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/counter-offers/{id}/accept', { params: { path: { id } } }));
export const rejectCounterOffer = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/counter-offers/{id}/reject', { params: { path: { id } } }));
