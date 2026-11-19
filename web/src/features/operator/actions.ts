import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { assignRequest, getCandidates, getOverview, rejectOrganization, requestThreads, suspendOrganization, threadMessages, updateParameter, verifyOrganization } from './server';
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
  /** Parámetros de sistema (V18): comisión, SLA y umbrales de incidencias. Cada cambio pide motivo y queda en el historial. */
  updateParameter: defineAction({
    accept: 'json',
    input: z.object({
      key: z.string().min(1).max(60),
      value: z.string().trim().min(1, 'Indica el valor').max(20),
      reason: z.string().trim().min(3, 'Indica el motivo del cambio').max(LIMITS.parameterReason),
      expectedVersion: z.number().int().min(0).optional(),
    }),
    handler: ({ key, ...body }, ctx) => run(() => updateParameter(requireToken(ctx), key, body)),
  }),
  /** Chat de una solicitud en solo lectura. Cada consulta queda auditada (`OPERATOR_VIEWED_CHAT`). */
  threads: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid(), reason: z.string().trim().max(200).optional() }),
    handler: ({ requestId, reason }, ctx) => run(() => requestThreads(requireToken(ctx), requestId, reason || undefined)),
  }),
  threadMessages: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid(), carrierOrgId: z.uuid(), before: z.uuid().optional(), reason: z.string().trim().max(200).optional() }),
    handler: ({ requestId, carrierOrgId, ...opts }, ctx) => run(() => threadMessages(requireToken(ctx), requestId, carrierOrgId, { before: opts.before, reason: opts.reason || undefined })),
  }),
};
