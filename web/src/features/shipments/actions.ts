import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { assignDriver, listShipments } from './server';

export const shipments = {
  list: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => listShipments(requireToken(ctx))) }),
  assignDriver: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), driverId: z.uuid('Selecciona un conductor') }),
    handler: ({ id, driverId }, ctx) => run(() => assignDriver(requireToken(ctx), id, driverId)),
  }),
};
