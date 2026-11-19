import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas, RequestView } from '@/lib/api/server';
import { unwrapPage } from '@/lib/api/page';

export const listMine = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/mine'));
/** Filtros de «Mis solicitudes» en el servidor (`page` base 0). `fromDate/toDate` son fechas de recojo. */
export type MyRequestsQuery = { status?: string; originCity?: string; destinationCity?: string; fromDate?: string; toDate?: string; q?: string; withBids?: boolean; sort?: string; page?: number; size?: number };
export const listMinePage = (token: string, query: MyRequestsQuery = {}) =>
  unwrapPage<RequestView>(apiFor(token).GET('/api/v1/shipment-requests/mine', { params: { query } }) as never);
export const myCounts = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/mine/counts'));
export const similarRequests = (token: string, query: { originCity: string; destinationCity: string; cargoType: Schemas['CreateRequest']['cargoType']; weightKg: number; pickupDate: string }) =>
  unwrap(apiFor(token).GET('/api/v1/shipment-requests/mine/similar', { params: { query } }));
export const getRequest = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}', { params: { path: { id } } }));
export const listBids = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/bids', { params: { path: { id } } }));
export const quote = (token: string, body: Schemas['QuoteRequest']) => unwrap(apiFor(token).POST('/api/v1/pricing/calculate', { body }));
/** `idempotencyKey` (UUID del formulario): doble clic o reintento devuelven la misma solicitud, no otra. */
export const createRequest = (token: string, body: Schemas['CreateRequest'], idempotencyKey?: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipment-requests', { body, params: { header: { 'Idempotency-Key': idempotencyKey } } }));
export const cancelRequest = (token: string, id: string, reason?: string) => unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/cancel', { params: { path: { id } }, body: reason ? { reason } : {} }));
export const reopenRequest = (token: string, id: string, pickupDate?: string) => unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/reopen', { params: { path: { id } }, body: pickupDate ? { pickupDate } : {} }));
export const republishRequest = (token: string, id: string, pickupDate?: string) => unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/republish', { params: { path: { id } }, body: pickupDate ? { pickupDate } : {} }));
export const acceptBid = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/bids/{id}/accept', { params: { path: { id } } }));
