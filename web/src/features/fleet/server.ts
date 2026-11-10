import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export const listVehicles = (token: string) => unwrap(apiFor(token).GET('/api/v1/vehicles'));
export const listDrivers = (token: string) => unwrap(apiFor(token).GET('/api/v1/drivers'));
export const createVehicle = (token: string, body: Schemas['VehicleRequest']) => unwrap(apiFor(token).POST('/api/v1/vehicles', { body }));
export const createDriver = (token: string, body: Schemas['DriverRequest']) => unwrap(apiFor(token).POST('/api/v1/drivers', { body }));
export const setVehicleStatus = (token: string, id: string, status: Schemas['StatusChange']['status']) =>
  unwrap(apiFor(token).PATCH('/api/v1/vehicles/{id}/status', { params: { path: { id } }, body: { status } }));
