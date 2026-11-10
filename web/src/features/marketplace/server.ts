import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export const listOpen = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests'));
export const listMyBids = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/my-bids'));
export const placeBid = (token: string, id: string, body: Schemas['PlaceBid']) =>
  unwrap(apiFor(token).POST('/api/v1/shipment-requests/{id}/bids', { params: { path: { id } }, body }));
