import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { assignDriver } from './server';

export const shipments = {
  assignDriver: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), driverId: z.uuid('Selecciona un conductor') }),
    handler: ({ id, driverId }, ctx) => run(() => assignDriver(requireToken(ctx), id, driverId)),
  }),
};
