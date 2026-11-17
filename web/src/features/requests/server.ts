import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export const listMine = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/mine'));
export const getRequest = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}', { params: { path: { id } } }));
export const listBids = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}/bids', { params: { path: { id } } }));
export const quote = (token: string, body: Schemas['QuoteRequest']) => unwrap(apiFor(token).POST('/api/v1/pricing/calculate', { body }));
export const createRequest = (token: string, body: Schemas['CreateRequest']) => unwrap(apiFor(token).POST('/api/v1/shipment-requests', { body }));
export const cancelRequest = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/cancel', { params: { path: { id } }, body: {} }));
export const acceptBid = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/bids/{id}/accept', { params: { path: { id } } }));
