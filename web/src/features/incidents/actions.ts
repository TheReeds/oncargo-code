import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { addComment, changeStatus, listIncidents, reportIncident, shipmentIncidents } from './server';

const CATEGORY = z.enum(['CARGO_DAMAGE', 'DELAY', 'LOST_CARGO', 'WRONG_DELIVERY', 'DRIVER_CONDUCT', 'OTHER']);
const STATUS = z.enum(['OPEN', 'IN_REVIEW', 'RESOLVED', 'CLOSED']);

export const incidents = {
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
    input: z.object({ shipmentId: z.uuid(), category: CATEGORY, description: z.string() }),
    handler: ({ shipmentId, category, description }, ctx) => run(() => reportIncident(requireToken(ctx), shipmentId, category, description.trim())),
  }),
  comment: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), body: z.string() }),
    handler: ({ id, body }, ctx) => run(() => addComment(requireToken(ctx), id, body.trim())),
  }),
  changeStatus: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), status: STATUS, resolutionNotes: z.string().optional() }),
    handler: ({ id, status, resolutionNotes }, ctx) => run(() => changeStatus(requireToken(ctx), id, status, resolutionNotes?.trim())),
  }),
};
