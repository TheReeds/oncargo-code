import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { placeBid, withdrawBid } from './server';
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
  /** F08: retira la oferta propia pendiente (`LOGYX-BID-005` si la subasta ya no acepta cambios). */
  withdrawBid: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(() => withdrawBid(requireToken(ctx), id)),
  }),
};
