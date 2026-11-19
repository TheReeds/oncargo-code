import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { archivePlace, createPlace, listPlaces, restorePlace, updatePlace } from './server';

const time = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Usa el formato HH:mm');
const optional = (max: number) => z.string().trim().max(max).optional().transform((v) => v || undefined);

const placeInput = z.object({
  label: z.string().trim().min(1, 'Ponle un nombre a la dirección').max(80, 'El nombre admite hasta 80 caracteres'),
  city: z.string().min(1, 'Selecciona la ciudad'),
  address: z.string().trim().min(3, 'Ingresa la dirección').max(200, 'La dirección admite hasta 200 caracteres'),
  contactName: optional(120), contactPhone: optional(20), openingHours: optional(120),
  windowFrom: time.optional().or(z.literal('').transform(() => undefined)), windowTo: time.optional().or(z.literal('').transform(() => undefined)),
  instructions: optional(300),
  kind: z.enum(['PICKUP', 'DELIVERY', 'BOTH']).default('BOTH'),
});

export const places = {
  /** Direcciones activas para el autocompletado del formulario (la usada más recientemente primero). */
  list: defineAction({
    accept: 'json',
    input: z.object({ city: z.string().optional(), kind: z.enum(['PICKUP', 'DELIVERY', 'BOTH']).optional(), q: z.string().max(80).optional(), includeArchived: z.boolean().optional() }),
    handler: (input, ctx) => run(() => listPlaces(requireToken(ctx), input)),
  }),
  create: defineAction({ accept: 'json', input: placeInput, handler: (input, ctx) => run(() => createPlace(requireToken(ctx), input as never)) }),
  update: defineAction({
    accept: 'json',
    input: placeInput.extend({ id: z.uuid() }),
    handler: ({ id, ...input }, ctx) => run(() => updatePlace(requireToken(ctx), id, input as never)),
  }),
  archive: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => archivePlace(requireToken(ctx), id)) }),
  restore: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => restorePlace(requireToken(ctx), id)) }),
};
