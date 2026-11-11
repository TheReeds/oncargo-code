import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { acceptCounterOffer, createCounterOffer, listCounterOffers, listMessages, listThreads, postMessage, rejectCounterOffer } from './server';

const ids = { requestId: z.uuid(), carrierOrgId: z.uuid() };

export const negotiation = {
  /** Organización del usuario en sesión (el transportista la necesita para su hilo). */
  myOrg: defineAction({ accept: 'json', handler: (_i, ctx) => { requireToken(ctx); return { orgId: ctx.locals.session!.orgId }; } }),
  threads: defineAction({ accept: 'json', input: z.object({ requestId: z.uuid() }), handler: ({ requestId }, ctx) => run(() => listThreads(requireToken(ctx), requestId)) }),
  messages: defineAction({
    accept: 'json',
    input: z.object({ ...ids, before: z.string().optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: ({ requestId, carrierOrgId, before, size }, ctx) => run(() => listMessages(requireToken(ctx), requestId, carrierOrgId, before, size)),
  }),
  postMessage: defineAction({
    accept: 'json',
    input: z.object({ ...ids, body: z.string().trim().min(1, 'Escribe un mensaje.').max(1000, 'El mensaje supera los 1000 caracteres.') }),
    handler: ({ requestId, carrierOrgId, body }, ctx) => run(() => postMessage(requireToken(ctx), requestId, carrierOrgId, body)),
  }),
  counterOffers: defineAction({ accept: 'json', input: z.object({ bidId: z.uuid() }), handler: ({ bidId }, ctx) => run(() => listCounterOffers(requireToken(ctx), bidId)) }),
  propose: defineAction({
    accept: 'json',
    input: z.object({ bidId: z.uuid(), amount: z.number().positive('Ingresa un monto válido.'), message: z.string().max(500).optional() }),
    handler: ({ bidId, ...body }, ctx) => run(() => createCounterOffer(requireToken(ctx), bidId, body)),
  }),
  accept: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => acceptCounterOffer(requireToken(ctx), id)) }),
  reject: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => rejectCounterOffer(requireToken(ctx), id)) }),
};
