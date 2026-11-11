import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { assignRequest, getCandidates, getOverview } from './server';

export const operator = {
  /** Métricas + alertas SLA (polling del panel). */
  overview: defineAction({
    accept: 'json',
    handler: (_, ctx) => run(() => getOverview(requireToken(ctx))),
  }),
  candidates: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid() }),
    handler: ({ requestId }, ctx) => run(() => getCandidates(requireToken(ctx), requestId)),
  }),
  assign: defineAction({
    accept: 'json',
    input: z.object({
      requestId: z.uuid(),
      carrierOrgId: z.uuid('Selecciona un transportista'),
      vehicleId: z.uuid('Selecciona un vehículo'),
      amount: z.number().positive('Ingresa un monto válido').optional(),
      notes: z.string().max(500).optional(),
    }),
    handler: ({ requestId, ...body }, ctx) => run(() => assignRequest(requireToken(ctx), requestId, body)),
  }),
};
