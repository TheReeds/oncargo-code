import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { addComment, assignIncident, changeStatus, getIncident, listIncidents, operatorIncidents, reopenIncident, reportIncident, shipmentIncidents, withdrawIncident } from './server';
import { LIMITS } from '@/lib/limits';

const CATEGORY = z.enum(['CARGO_DAMAGE', 'DELAY', 'LOST_CARGO', 'WRONG_DELIVERY', 'DRIVER_CONDUCT', 'OTHER', 'PICKUP_NO_SHOW', 'SHIPPER_NO_SHOW',
  'BREAKDOWN', 'ACCIDENT', 'THEFT', 'ROAD_BLOCKED', 'PARTIAL_DELIVERY', 'PAYMENT_DISPUTE']);
const SEVERITY = z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);
const STATUS = z.enum(['OPEN', 'IN_REVIEW', 'RESOLVED', 'CLOSED']);

export const incidents = {
  get: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(() => getIncident(requireToken(ctx), id)),
  }),
  list: defineAction({
    accept: 'json',
    input: z.object({ status: z.string().optional(), active: z.boolean().optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: (q, ctx) => run(() => listIncidents(requireToken(ctx), q)),
  }),
  forShipment: defineAction({
    accept: 'json',
    input: z.object({ shipmentId: z.uuid() }),
    handler: ({ shipmentId }, ctx) => run(() => shipmentIncidents(requireToken(ctx), shipmentId)),
  }),
  report: defineAction({
    accept: 'json',
    input: z.object({
      shipmentId: z.uuid(), category: CATEGORY, description: z.string().max(LIMITS.incidentText), severity: SEVERITY.optional(),
      claimedAmount: z.number().positive().optional(), attachmentIds: z.array(z.uuid()).max(5).optional(), idempotencyKey: z.string().min(8).max(80).optional(),
    }),
    handler: ({ shipmentId, idempotencyKey, description, ...rest }, ctx) =>
      run(() => reportIncident(requireToken(ctx), shipmentId, { ...rest, description: description.trim() }, idempotencyKey)),
  }),
  comment: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), body: z.string(), internal: z.boolean().optional() }),
    handler: ({ id, body, internal }, ctx) => run(() => addComment(requireToken(ctx), id, body.trim(), !!internal)),
  }),
  withdraw: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => withdrawIncident(requireToken(ctx), id)) }),
  reopen: defineAction({
    accept: 'json', input: z.object({ id: z.uuid(), reason: z.string().trim().min(3).max(LIMITS.incidentText) }),
    handler: ({ id, reason }, ctx) => run(() => reopenIncident(requireToken(ctx), id, reason)),
  }),
  assign: defineAction({
    accept: 'json', input: z.object({ id: z.uuid(), unassign: z.boolean().optional() }),
    handler: ({ id, unassign }, ctx) => run(() => assignIncident(requireToken(ctx), id, !!unassign)),
  }),
  operatorInbox: defineAction({
    accept: 'json',
    input: z.object({
      status: z.string().max(60).optional(), severity: z.string().max(60).optional(), assignee: z.enum(['me', 'none']).optional(),
      breached: z.boolean().optional(), order: z.enum(['due', 'recent']).optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional(),
    }),
    handler: (q, ctx) => run(() => operatorIncidents(requireToken(ctx), q)),
  }),
  changeStatus: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), status: STATUS, resolutionNotes: z.string().optional(), shipmentOutcome: z.enum(['RESUME', 'DELIVERED', 'CANCELLED']).optional() }),
    handler: ({ id, status, resolutionNotes, shipmentOutcome }, ctx) => run(() => changeStatus(requireToken(ctx), id, status, resolutionNotes?.trim(), shipmentOutcome)),
  }),
};
