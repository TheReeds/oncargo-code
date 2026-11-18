import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { assignRequest, getCandidates, getOverview, rejectOrganization, suspendOrganization, verifyOrganization } from './server';
import { LIMITS } from '@/lib/limits';

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
      notes: z.string().max(LIMITS.operatorNotes).optional(),
    }),
    handler: ({ requestId, ...body }, ctx) => run(() => assignRequest(requireToken(ctx), requestId, body)),
  }),
  /** Bandeja de verificación (RN01): decisiones sobre una organización. */
  verifyOrganization: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), note: z.string().trim().max(LIMITS.organizationNote).optional() }),
    handler: ({ id, note }, ctx) => run(() => verifyOrganization(requireToken(ctx), id, note || undefined)),
  }),
  rejectOrganization: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: z.string().trim().min(1, 'Indica el motivo').max(LIMITS.organizationReason) }),
    handler: ({ id, reason }, ctx) => run(() => rejectOrganization(requireToken(ctx), id, reason)),
  }),
  suspendOrganization: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: z.string().trim().min(1, 'Indica el motivo').max(LIMITS.organizationReason) }),
    handler: ({ id, reason }, ctx) => run(() => suspendOrganization(requireToken(ctx), id, reason)),
  }),
};
