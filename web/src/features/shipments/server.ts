import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export const listShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipments'));
export const assignDriver = (token: string, id: string, driverId: string) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/driver', { params: { path: { id } }, body: { driverId } }));
export const driverShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/driver/shipments'));
