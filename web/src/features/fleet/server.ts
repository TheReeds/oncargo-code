import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas, ShipmentView } from '@/lib/api/server';
import { listShipmentsPage } from '@/features/shipments/server';
import { ACTIVE_STATUSES } from '@/features/shipments/filters';

export const listVehicles = (token: string) => unwrap(apiFor(token).GET('/api/v1/vehicles'));
export const listDrivers = (token: string) => unwrap(apiFor(token).GET('/api/v1/drivers'));
export const createVehicle = (token: string, body: Schemas['VehicleRequest']) => unwrap(apiFor(token).POST('/api/v1/vehicles', { body }));
export const createDriver = (token: string, body: Schemas['DriverRequest']) => unwrap(apiFor(token).POST('/api/v1/drivers', { body }));
export const updateVehicle = (token: string, id: string, body: Schemas['VehiclePatch']) =>
  unwrap(apiFor(token).PATCH('/api/v1/vehicles/{id}', { params: { path: { id } }, body }));
export const archiveVehicle = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/vehicles/{id}/archive', { params: { path: { id } } }));
export const setVehicleStatus = (token: string, id: string, status: Schemas['StatusChange']['status']) =>
  unwrap(apiFor(token).PATCH('/api/v1/vehicles/{id}/status', { params: { path: { id } }, body: { status } }));

// Cuentas de conductores (V14): solo ADMIN de transportista (`MANAGE_DRIVERS`).
export const deactivateDriver = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/drivers/{id}/deactivate', { params: { path: { id } } }));
export const reactivateDriver = (token: string, id: string) => unwrap(apiFor(token).POST('/api/v1/drivers/{id}/reactivate', { params: { path: { id } } }));
export const resetDriverPassword = (token: string, id: string, newPassword: string) =>
  unwrap(apiFor(token).POST('/api/v1/drivers/{id}/reset-password', { params: { path: { id } }, body: { newPassword } }));

// ── Documentos, configuración y agenda de flota (Ola 2, 2.4 y 2.13) ──
export type FleetDocument = Schemas['FleetDocumentView'];
/** Documentos vigentes de toda la flota ordenados por vencimiento (tablero de vencimientos). */
export const listFleetDocuments = (token: string) => unwrap(apiFor(token).GET('/api/v1/fleet/documents'));
export const vehicleDocuments = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/vehicles/{id}/documents', { params: { path: { id } } }));
export const driverDocuments = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/drivers/{id}/documents', { params: { path: { id } } }));
export const updateVehicleConfig = (token: string, id: string, body: Schemas['VehicleConfig']) =>
  unwrap(apiFor(token).PUT('/api/v1/vehicles/{id}/config', { params: { path: { id } }, body }));
export const patchDriver = (token: string, id: string, body: Schemas['DriverPatch']) =>
  unwrap(apiFor(token).PATCH('/api/v1/drivers/{id}', { params: { path: { id } }, body }));
export const getSchedule = (token: string, from: string, to: string) => unwrap(apiFor(token).GET('/api/v1/fleet/schedule', { params: { query: { from, to } } }));
export const getSuggestions = (token: string, shipmentId: string) => unwrap(apiFor(token).GET('/api/v1/fleet/assignment-suggestions', { params: { query: { shipmentId } } }));
export const assignResources = (token: string, id: string, body: Schemas['AssignResources']) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/assignment', { params: { path: { id } }, body }));
/** Envíos activos (por recoger o en tránsito) que aún no tienen conductor: lo que se puede asignar desde la agenda. */
export async function listUnassigned(token: string): Promise<ShipmentView[]> {
  const { items } = await listShipmentsPage(token, { status: ACTIVE_STATUSES, size: 100 });
  return items.filter((s) => !s.driverId && (s.status === 'PENDING' || s.status === 'IN_TRANSIT'));
}
