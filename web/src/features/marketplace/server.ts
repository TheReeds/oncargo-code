import { apiFor } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';
import type { RequestView, Schemas } from '@/lib/api/server';

export type OpenQuery = { originCity?: string; destinationCity?: string; pickupFrom?: string; pickupTo?: string; maxWeightKg?: number; cargoType?: string; sort?: string; page?: number; size?: number };

/** Cargas abiertas con filtros y paginación en servidor (`page` base 0). */
export async function listOpen(token: string, query: OpenQuery = {}): Promise<{ items: RequestView[]; page: number; size: number; total: number }> {
  let res;
  try {
    res = await apiFor(token).GET('/api/v1/shipment-requests', { params: { query } });
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  const body = res.data as { data?: RequestView[]; meta?: { page?: number; size?: number; total?: number } };
  const items = body.data ?? [];
  return { items, page: body.meta?.page ?? 0, size: body.meta?.size ?? 20, total: body.meta?.total ?? items.length };
}
export const listMyBids = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/my-bids'));
/** RN02 por fecha: elegibilidad de cada vehículo de mi flota para la solicitud (agenda, avisos y impedimentos). */
export const getEligibility = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/vehicle-eligibility', { params: { path: { id } } }));
/** Tasa de comisión vigente (para «cobras S/ X neto»). */
export const getCommission = (token: string) => unwrap(apiFor(token).GET('/api/v1/pricing/commission', { params: { query: {} } }));
export const placeBid = (token: string, id: string, body: Schemas['PlaceBid']) =>
  unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/bids', { params: { path: { id } }, body }));
export const withdrawBid = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/withdraw', { params: { path: { id } } }));

// ── Búsquedas guardadas y alertas de carga (2.3) ─────────────────────
export type SavedSearch = Schemas['SavedSearchView'];
export const listSavedSearches = (token: string) => unwrap(apiFor(token).GET('/api/v1/saved-searches'));
export const createSavedSearch = (token: string, body: Schemas['SavedSearchRequest']) => unwrap(apiFor(token).POST('/api/v1/saved-searches', { body }));
export const updateSavedSearch = (token: string, id: string, body: Schemas['SavedSearchRequest']) =>
  unwrap(apiFor(token).PUT('/api/v1/saved-searches/{id}', { params: { path: { id } }, body }));
export const deleteSavedSearch = (token: string, id: string) => unwrap(apiFor(token).DELETE('/api/v1/saved-searches/{id}', { params: { path: { id } } }));

// ── Mejorar oferta, historial y precio de la ruta (2.8) ──────────────
export const improveBid = (token: string, id: string, body: Schemas['ImproveBid']) =>
  unwrap(apiFor(token).POST('/api/v1/bids/{id}/improve', { params: { path: { id } }, body }));
export const bidRevisions = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/bids/{id}/revisions', { params: { path: { id } } }));
/** `null` cuando no hay k ≥ 5 envíos en la ruta (la API responde `data: null`). */
export const laneStats = async (token: string, id: string) =>
  (await unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/lane-stats', { params: { path: { id } } }))) ?? null;
