import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { LIMITS } from '@/lib/limits';
import {
  archiveTemplate, createTemplate, discardDraft, listDrafts, listTemplates, pauseSchedule, publishDraft, publishTemplate, removeSchedule,
  restoreTemplate, resumeSchedule, setSchedule, templateFromRequest, updateTemplate,
} from './server';

const city = z.string().min(1, 'Selecciona una ciudad');
const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Usa el formato HH:mm');
const opt = (max: number) => z.string().trim().max(max).optional().transform((v) => v || undefined);
const cargo = z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED']);
const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Selecciona la fecha');
const name = z.string().trim().min(1, 'Ponle un nombre a la plantilla.').max(LIMITS.templateName, `El nombre admite hasta ${LIMITS.templateName} caracteres.`);

/** Los mismos campos que `requests.create`, sin la fecha de recojo y con el nombre de la plantilla. */
const templateInput = z.object({
  name,
  originCity: city, originAddress: z.string().min(3, 'Ingresa la dirección de recojo').max(LIMITS.originAddress),
  destinationCity: city, destinationAddress: z.string().min(3, 'Ingresa la dirección de entrega').max(LIMITS.destinationAddress),
  cargoType: cargo, cargoDescription: z.string().min(3, 'Describe la carga').max(LIMITS.cargoDescription),
  weightKg: z.number().positive('Ingresa el peso'), volumeM3: z.number().positive().optional(),
  customerReference: opt(LIMITS.customerReference), declaredValue: z.number().positive().optional(),
  pickupContactName: opt(LIMITS.contactName), pickupContactPhone: opt(LIMITS.contactPhone),
  pickupWindowFrom: hhmm.optional(), pickupWindowTo: hhmm.optional(), pickupInstructions: opt(LIMITS.stopInstructions),
  deliveryContactName: opt(LIMITS.contactName), deliveryContactPhone: opt(LIMITS.contactPhone),
  deliveryWindowFrom: hhmm.optional(), deliveryWindowTo: hhmm.optional(), deliveryInstructions: opt(LIMITS.stopInstructions),
  originPlaceId: z.uuid().optional(), destinationPlaceId: z.uuid().optional(),
  invitePreferred: z.boolean().optional(),
});

const schedule = z.object({
  id: z.uuid(),
  frequency: z.enum(['WEEKLY', 'BIWEEKLY']),
  pickupWeekday: z.number().int().min(1).max(7),
  leadDays: z.number().int().min(0).max(14),
  endsOn: isoDate.optional(),
  skipHolidays: z.boolean(),
  mode: z.enum(['PUBLISH', 'DRAFT']),
});

export const templates = {
  list: defineAction({ accept: 'json', input: z.object({ includeArchived: z.boolean().optional() }), handler: ({ includeArchived }, ctx) => run(() => listTemplates(requireToken(ctx), includeArchived ?? true)) }),
  drafts: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => listDrafts(requireToken(ctx))) }),
  create: defineAction({ accept: 'json', input: templateInput, handler: (input, ctx) => run(() => createTemplate(requireToken(ctx), input as never)) }),
  update: defineAction({ accept: 'json', input: templateInput.extend({ id: z.uuid() }), handler: ({ id, ...input }, ctx) => run(() => updateTemplate(requireToken(ctx), id, input as never)) }),
  fromRequest: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid(), name, invitePreferred: z.boolean().optional() }),
    handler: ({ requestId, name, invitePreferred }, ctx) => run(() => templateFromRequest(requireToken(ctx), requestId, name, invitePreferred)),
  }),
  archive: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => archiveTemplate(requireToken(ctx), id)) }),
  restore: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => restoreTemplate(requireToken(ctx), id)) }),
  publish: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), pickupDate: isoDate, idempotencyKey: z.string().min(8).max(80).optional() }),
    handler: ({ id, pickupDate, idempotencyKey }, ctx) => run(() => publishTemplate(requireToken(ctx), id, pickupDate, idempotencyKey)),
  }),
  schedule: defineAction({
    accept: 'json',
    input: schedule,
    handler: ({ id, ...body }, ctx) => run(() => setSchedule(requireToken(ctx), id, body)),
  }),
  pause: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => pauseSchedule(requireToken(ctx), id)) }),
  resume: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => resumeSchedule(requireToken(ctx), id)) }),
  unschedule: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => removeSchedule(requireToken(ctx), id)) }),
  publishDraft: defineAction({ accept: 'json', input: z.object({ runId: z.uuid() }), handler: ({ runId }, ctx) => run(() => publishDraft(requireToken(ctx), runId)) }),
  discardDraft: defineAction({ accept: 'json', input: z.object({ runId: z.uuid() }), handler: ({ runId }, ctx) => run(() => discardDraft(requireToken(ctx), runId)) }),
};
