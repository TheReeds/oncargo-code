import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { placeBid } from './server';

export const marketplace = {
  placeBid: defineAction({
    accept: 'json',
    input: z.object({
      requestId: z.uuid(),
      vehicleId: z.uuid('Selecciona un vehículo'),
      amount: z.number().positive('Ingresa un monto válido'),
      notes: z.string().max(500).optional(),
    }),
    handler: ({ requestId, ...body }, ctx) => run(() => placeBid(requireToken(ctx), requestId, body)),
  }),
};
