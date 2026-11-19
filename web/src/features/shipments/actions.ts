import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { listVehicles, listDrivers } from '@/features/fleet/server';
import { getRequest } from '@/features/requests/server';
import { LIMITS } from '@/lib/limits';
import { assignDriver, cancelShipment, changeVehicle, createShareLink, decideAdjustment, listShareLinks, listShipments, listShipmentsPage, officeStop, proposeAdjustment, revokeShareLink, setPickupRequirements, shipmentAdjustments, withdrawAdjustment } from './server';
import { getShipment } from './server';

const reason = z.string().trim().max(LIMITS.shipmentReason, `El motivo admite hasta ${LIMITS.shipmentReason} caracteres.`);
const listQuery = z.object({
  status: z.string().max(60).optional(), originCity: z.string().max(40).optional(), destinationCity: z.string().max(40).optional(),
  fromDate: z.string().max(10).optional(), toDate: z.string().max(10).optional(), q: z.string().max(80).optional(),
  page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional(),
});

export const shipments = {
  /** Sin filtros: todos (compatibilidad). Con `query`: la página filtrada en el servidor, como la ve `TrackingSplit`. */
  list: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => listShipments(requireToken(ctx))) }),
  page: defineAction({ accept: 'json', input: listQuery, handler: (query, ctx) => run(() => listShipmentsPage(requireToken(ctx), query)) }),
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
  /** Enlaces públicos de seguimiento (roadmap 1.6): la PYME los crea (ADMIN/OPS), los ve (también el Lector) y revoca. */
  shareLinks: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => listShareLinks(requireToken(ctx), id)) }),
  createShareLink: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), expiresInDays: z.number().int().min(1).max(30).optional() }),
    handler: ({ id, expiresInDays }, ctx) => run(() => createShareLink(requireToken(ctx), id, expiresInDays)),
  }),
  revokeShareLink: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), linkId: z.uuid() }),
    handler: ({ id, linkId }, ctx) => run(() => revokeShareLink(requireToken(ctx), id, linkId)),
  }),
  /** Envío por id (refresco en sitio tras una acción). */
  get: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => getShipment(requireToken(ctx), id)) }),
  // ----- Ajustes del envío (2.6)
  adjustments: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => shipmentAdjustments(requireToken(ctx), id)) }),
  proposeAdjustment: defineAction({
    accept: 'json',
    input: z.object({
      id: z.uuid(), type: z.enum(['WEIGHT_DIFF', 'DETENTION', 'TOLL', 'HANDLING', 'OTHER']),
      amount: z.number().refine((n) => n !== 0, 'Indica un monto distinto de 0.'),
      note: z.string().trim().max(500, 'La nota admite hasta 500 caracteres.').optional(),
      stopId: z.uuid().optional(), evidenceDocumentId: z.uuid().optional(), idempotencyKey: z.string().min(8).max(80).optional(),
    }),
    handler: ({ id, idempotencyKey, note, ...body }, ctx) => run(() => proposeAdjustment(requireToken(ctx), id, { ...body, note: note || undefined }, idempotencyKey)),
  }),
  decideAdjustment: defineAction({
    accept: 'json',
    input: z.object({ adjustmentId: z.uuid(), accept: z.boolean(), note: z.string().trim().max(500).optional() }),
    handler: ({ adjustmentId, accept, note }, ctx) => run(() => decideAdjustment(requireToken(ctx), adjustmentId, accept, note || undefined)),
  }),
  withdrawAdjustment: defineAction({
    accept: 'json', input: z.object({ adjustmentId: z.uuid() }),
    handler: ({ adjustmentId }, ctx) => run(() => withdrawAdjustment(requireToken(ctx), adjustmentId)),
  }),
  // ----- Recojo y registro desde la oficina (2.5)
  pickupRequirements: defineAction({
    accept: 'json', input: z.object({ id: z.uuid(), photoRequired: z.boolean(), weightRequired: z.boolean() }),
    handler: ({ id, photoRequired, weightRequired }, ctx) => run(() => setPickupRequirements(requireToken(ctx), id, photoRequired, weightRequired)),
  }),
  officeStop: defineAction({
    accept: 'json',
    input: z.object({
      id: z.uuid(), stopId: z.uuid(), step: z.enum(['arrive', 'complete']),
      reason: z.string().trim().min(3, 'Indica el motivo (mínimo 3 caracteres).').max(300),
      weightKg: z.number().positive().optional(), receivedByName: z.string().trim().max(120).optional(), receivedByDoc: z.string().trim().max(20).optional(),
    }),
    handler: ({ id, stopId, step, ...body }, ctx) => run(() => officeStop(requireToken(ctx), id, stopId, step, {
      reason: body.reason, weightKg: body.weightKg, receivedByName: body.receivedByName || undefined, receivedByDoc: body.receivedByDoc || undefined,
    })),
  }),
};
