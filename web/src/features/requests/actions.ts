import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { acceptBid, cancelRequest, createRequest, quote } from './server';

const city = z.string().min(1, 'Selecciona una ciudad');
const cargo = z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED']);

export const requests = {
  quote: defineAction({
    accept: 'json',
    input: z.object({ originCity: city, destinationCity: city, weightKg: z.number().positive(), cargoType: cargo }),
    handler: (input, ctx) => run(() => quote(requireToken(ctx), input as any)),
  }),
  create: defineAction({
    accept: 'json',
    input: z.object({
      originCity: city, originAddress: z.string().min(3, 'Ingresa la dirección de recojo'),
      destinationCity: city, destinationAddress: z.string().min(3, 'Ingresa la dirección de entrega'),
      cargoType: cargo, cargoDescription: z.string().min(3, 'Describe la carga'),
      weightKg: z.number().positive('Ingresa el peso'), volumeM3: z.number().positive().optional(),
      pickupDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Selecciona la fecha de recojo'),
    }),
    handler: (input, ctx) => run(() => createRequest(requireToken(ctx), input as any)),
  }),
  cancel: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(() => cancelRequest(requireToken(ctx), id)),
  }),
  accept: defineAction({
    accept: 'json',
    input: z.object({ bidId: z.uuid() }),
    handler: ({ bidId }, ctx) => run(() => acceptBid(requireToken(ctx), bidId)),
  }),
};
