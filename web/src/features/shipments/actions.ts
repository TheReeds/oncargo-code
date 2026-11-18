import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { listVehicles, listDrivers } from '@/features/fleet/server';
import { getRequest } from '@/features/requests/server';
import { LIMITS } from '@/lib/limits';
import { assignDriver, cancelShipment, changeVehicle, listShipments } from './server';

const reason = z.string().trim().max(LIMITS.shipmentReason, `El motivo admite hasta ${LIMITS.shipmentReason} caracteres.`);

export const shipments = {
  list: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => listShipments(requireToken(ctx))) }),
  assignDriver: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), driverId: z.uuid('Selecciona un conductor'), reason: reason.optional() }),
    handler: ({ id, driverId, reason }, ctx) => run(() => assignDriver(requireToken(ctx), id, driverId, reason || undefined)),
  }),
  cancel: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: reason.min(1, 'Indica el motivo de la cancelación.') }),
    handler: ({ id, reason }, ctx) => run(() => cancelShipment(requireToken(ctx), id, reason)),
  }),
  changeVehicle: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), vehicleId: z.uuid('Selecciona un vehículo'), reason: reason.optional() }),
    handler: ({ id, vehicleId, reason }, ctx) => run(() => changeVehicle(requireToken(ctx), id, vehicleId, reason || undefined)),
  }),
  /** Flota del transportista y datos de la carga (para la compatibilidad RN02 al cambiar de vehículo o conductor). */
  fleetContext: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid().optional() }),
    handler: ({ requestId }, ctx) => run(async () => {
      const token = requireToken(ctx);
      const [vehicles, drivers, request] = await Promise.all([
        listVehicles(token), listDrivers(token),
        requestId ? getRequest(token, requestId).catch(() => null) : Promise.resolve(null),
      ]);
      return { vehicles, drivers, request };
    }),
  }),
};
