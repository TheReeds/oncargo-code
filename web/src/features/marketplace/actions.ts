import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { getCommission, getEligibility, placeBid, withdrawBid } from './server';
import { LIMITS } from '@/lib/limits';

export const marketplace = {
  placeBid: defineAction({
    accept: 'json',
    input: z.object({
      requestId: z.uuid(),
      vehicleId: z.uuid('Selecciona un vehículo'),
      amount: z.number().positive('Ingresa un monto válido'),
      notes: z.string().max(LIMITS.bidNotes).optional(),
    }),
    handler: ({ requestId, ...body }, ctx) => run(() => placeBid(requireToken(ctx), requestId, body)),
  }),
  /** RN02 por fecha: qué vehículos pueden ofertar a esta carga y su agenda en esas fechas. */
  eligibility: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid() }),
    handler: ({ requestId }, ctx) => run(() => getEligibility(requireToken(ctx), requestId)),
  }),
  /** Tasa de comisión vigente de OnCargo (porcentaje que se retiene al transportista). */
  commission: defineAction({ accept: 'json', input: z.object({}).optional(), handler: (_i, ctx) => run(() => getCommission(requireToken(ctx))) }),
  /** F08: retira la oferta propia pendiente (`LOGYX-BID-005` si la subasta ya no acepta cambios). */
  withdrawBid: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(() => withdrawBid(requireToken(ctx), id)),
  }),
};
