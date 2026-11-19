import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { acceptBid, cancelRequest, createRequest, getRequest, listBids, quote, reopenRequest, republishRequest, similarRequests } from './server';
import { LIMITS } from '@/lib/limits';

const city = z.string().min(1, 'Selecciona una ciudad');
const hhmm = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Usa el formato HH:mm');
const opt = (max: number) => z.string().trim().max(max).optional().transform((v) => v || undefined);
const cargo = z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED']);

export const requests = {
  /** Solicitud + ofertas (refresco en vivo del detalle de la PYME). */
  detail: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(async () => {
      const token = requireToken(ctx);
      const [request, bids] = await Promise.all([getRequest(token, id), listBids(token, id)]);
      return { request, bids };
    }),
  }),
  quote: defineAction({
    accept: 'json',
    input: z.object({ originCity: city, destinationCity: city, weightKg: z.number().positive(), cargoType: cargo }),
    handler: (input, ctx) => run(() => quote(requireToken(ctx), input as any)),
  }),
  create: defineAction({
    accept: 'json',
    input: z.object({
      originCity: city, originAddress: z.string().min(3, 'Ingresa la dirección de recojo').max(LIMITS.originAddress, `La dirección admite hasta ${LIMITS.originAddress} caracteres`),
      destinationCity: city, destinationAddress: z.string().min(3, 'Ingresa la dirección de entrega').max(LIMITS.destinationAddress, `La dirección admite hasta ${LIMITS.destinationAddress} caracteres`),
      cargoType: cargo, cargoDescription: z.string().min(3, 'Describe la carga').max(LIMITS.cargoDescription, `La descripción admite hasta ${LIMITS.cargoDescription} caracteres`),
      weightKg: z.number().positive('Ingresa el peso'), volumeM3: z.number().positive().optional(),
      pickupDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Selecciona la fecha de recojo'),
      // Opcionales (V17): referencia propia, valor declarado, contacto / ventana / instrucciones (privados hasta adjudicar)
      customerReference: opt(LIMITS.customerReference), declaredValue: z.number().positive('El valor declarado debe ser mayor a 0').optional(),
      pickupContactName: opt(LIMITS.contactName), pickupContactPhone: opt(LIMITS.contactPhone),
      pickupWindowFrom: hhmm.optional(), pickupWindowTo: hhmm.optional(), pickupInstructions: opt(LIMITS.stopInstructions),
      deliveryContactName: opt(LIMITS.contactName), deliveryContactPhone: opt(LIMITS.contactPhone),
      deliveryWindowFrom: hhmm.optional(), deliveryWindowTo: hhmm.optional(), deliveryInstructions: opt(LIMITS.stopInstructions),
      originPlaceId: z.uuid().optional(), destinationPlaceId: z.uuid().optional(),
      /** Clave de idempotencia del formulario: un doble clic o un reintento no publica dos veces. */
      idempotencyKey: z.string().min(8).max(80).optional(),
    }),
    handler: ({ idempotencyKey, ...input }, ctx) => run(() => createRequest(requireToken(ctx), input as any, idempotencyKey)),
  }),
  /** «Ya publicaste una igual hoy»: misma ruta, tipo, peso y fecha en las últimas 24 h. */
  similar: defineAction({
    accept: 'json',
    input: z.object({ originCity: city, destinationCity: city, cargoType: cargo, weightKg: z.number().positive(), pickupDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/) }),
    handler: (input, ctx) => run(() => similarRequests(requireToken(ctx), input)),
  }),
  cancel: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: z.string().trim().max(LIMITS.requestCancelReason).optional() }),
    handler: ({ id, reason }, ctx) => run(() => cancelRequest(requireToken(ctx), id, reason || undefined)),
  }),
  /** F07: reabre la subasta que cerró por tiempo (nueva fecha de recojo opcional). */
  reopen: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), pickupDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional() }),
    handler: ({ id, pickupDate }, ctx) => run(() => reopenRequest(requireToken(ctx), id, pickupDate)),
  }),
  /** RF18: nueva solicitud a partir de una cancelada (201). */
  republish: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), pickupDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional() }),
    handler: ({ id, pickupDate }, ctx) => run(() => republishRequest(requireToken(ctx), id, pickupDate)),
  }),
  accept: defineAction({
    accept: 'json',
    input: z.object({ bidId: z.uuid() }),
    handler: ({ bidId }, ctx) => run(() => acceptBid(requireToken(ctx), bidId)),
  }),
};
