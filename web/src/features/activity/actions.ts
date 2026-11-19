import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { getActivity } from './server';

export const activity = {
  get: defineAction({
    accept: 'json',
    input: z.object({ subject: z.enum(['REQUEST', 'BID', 'SHIPMENT', 'TRIP', 'INCIDENT']), id: z.uuid() }),
    handler: ({ subject, id }, ctx) => run(() => getActivity(requireToken(ctx), subject, id)),
  }),
};
