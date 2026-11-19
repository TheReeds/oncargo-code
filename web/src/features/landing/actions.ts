import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { clientIpOf, run } from '@/lib/actions';
import { getPublicQuote } from '@/features/legal/server';

const CITIES = ['LIMA', 'ICA', 'NAZCA', 'AREQUIPA', 'MOQUEGUA', 'TACNA', 'JULIACA', 'PUNO', 'CUSCO', 'ABANCAY', 'AYACUCHO'] as const;
const CARGO = ['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED'] as const;

export const landing = {
  /** Cotización pública de la landing (sin sesión): `POST /public/pricing/quote` con la IP del visitante. */
  quote: defineAction({
    accept: 'json',
    input: z.object({
      originCity: z.enum(CITIES),
      destinationCity: z.enum(CITIES),
      weightKg: z.number().min(1).max(60000),
      cargoType: z.enum(CARGO),
    }),
    handler: (input, ctx) => run(() => getPublicQuote(input, clientIpOf(ctx))),
  }),
};
