import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import {
  awardBackup, backupOptions, backupRequests, confirmBackup, declineBackup, invitePreferred, listPreferences, markCarrier,
  requestBackup, unmarkCarrier,
} from './server';

const kind = z.enum(['PREFERRED', 'BLOCKED']);

/** Transportistas preferidos y bloqueados de la PYME (roadmap 2.9). */
export const preferences = {
  list: defineAction({ accept: 'json', input: z.object({ kind: kind.optional() }), handler: ({ kind }, ctx) => run(() => listPreferences(requireToken(ctx), kind)) }),
  mark: defineAction({ accept: 'json', input: z.object({ carrierOrgId: z.uuid(), kind }), handler: ({ carrierOrgId, kind }, ctx) => run(() => markCarrier(requireToken(ctx), carrierOrgId, kind)) }),
  unmark: defineAction({ accept: 'json', input: z.object({ carrierOrgId: z.uuid() }), handler: ({ carrierOrgId }, ctx) => run(() => unmarkCarrier(requireToken(ctx), carrierOrgId)) }),
  invite: defineAction({ accept: 'json', input: z.object({ requestId: z.uuid() }), handler: ({ requestId }, ctx) => run(() => invitePreferred(requireToken(ctx), requestId)) }),
};

/** Segunda oferta: la PYME pide confirmar y adjudica; el transportista confirma o declina (roadmap 2.9). */
export const backup = {
  options: defineAction({ accept: 'json', input: z.object({ requestId: z.uuid() }), handler: ({ requestId }, ctx) => run(() => backupOptions(requireToken(ctx), requestId)) }),
  request: defineAction({ accept: 'json', input: z.object({ bidId: z.uuid() }), handler: ({ bidId }, ctx) => run(() => requestBackup(requireToken(ctx), bidId)) }),
  award: defineAction({ accept: 'json', input: z.object({ bidId: z.uuid() }), handler: ({ bidId }, ctx) => run(() => awardBackup(requireToken(ctx), bidId)) }),
  mine: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => backupRequests(requireToken(ctx))) }),
  confirm: defineAction({ accept: 'json', input: z.object({ bidId: z.uuid() }), handler: ({ bidId }, ctx) => run(() => confirmBackup(requireToken(ctx), bidId)) }),
  decline: defineAction({ accept: 'json', input: z.object({ bidId: z.uuid() }), handler: ({ bidId }, ctx) => run(() => declineBackup(requireToken(ctx), bidId)) }),
};
