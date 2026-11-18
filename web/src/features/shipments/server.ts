import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';

export const listShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipments'));
export const assignDriver = (token: string, id: string, driverId: string, reason?: string) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/driver', { params: { path: { id } }, body: reason ? { driverId, reason } : { driverId } }));
export const driverShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/driver/shipments'));
/** F06: cancela un envío con motivo (PYME y transportista antes del recojo; el operador también en tránsito). */
export const cancelShipment = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/cancel', { params: { path: { id } }, body: { reason } }));
/** F06: cambia el vehículo del envío (RN02, `CAP-*`); el motivo es obligatorio en ruta (`SHP-006`). */
export const changeVehicle = (token: string, id: string, vehicleId: string, reason?: string) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/vehicle', { params: { path: { id } }, body: reason ? { vehicleId, reason } : { vehicleId } }));
