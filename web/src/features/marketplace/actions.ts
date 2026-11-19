import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { bidRevisions, createSavedSearch, deleteSavedSearch, getCommission, getEligibility, improveBid, laneStats, listSavedSearches, placeBid, updateSavedSearch, withdrawBid } from './server';
import { LIMITS } from '@/lib/limits';

const CITIES = ['LIMA', 'ICA', 'NAZCA', 'AREQUIPA', 'MOQUEGUA', 'TACNA', 'JULIACA', 'PUNO', 'CUSCO', 'ABANCAY', 'AYACUCHO'] as const;
const savedSearchInput = z.object({
  name: z.string().trim().min(1, 'Ponle un nombre a la búsqueda').max(80),
  originCity: z.enum(CITIES).optional(), destinationCity: z.enum(CITIES).optional(),
  cargoType: z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED']).optional(),
  pickupFrom: z.string().optional(), pickupTo: z.string().optional(),
  maxWeightKg: z.number().positive().optional(), vehicleId: z.uuid().optional(), active: z.boolean().optional(),
});

export const marketplace = {
  placeBid: defineAction({
    accept: 'json',
    input: z.object({
      requestId: z.uuid(),
      vehicleId: z.uuid('Selecciona un vehículo'),
      amount: z.number().positive('Ingresa un monto válido'),
      notes: z.string().max(LIMITS.bidNotes).optional(),
    }),
    handler: ({ requestId, ...body }, ctx) => run(() => placeBid(requireToken(ctx), requestId, body)),
  }),
  /** RN02 por fecha: qué vehículos pueden ofertar a esta carga y su agenda en esas fechas. */
  eligibility: defineAction({
    accept: 'json',
    input: z.object({ requestId: z.uuid() }),
    handler: ({ requestId }, ctx) => run(() => getEligibility(requireToken(ctx), requestId)),
  }),
  /** Tasa de comisión vigente de OnCargo (porcentaje que se retiene al transportista). */
  commission: defineAction({ accept: 'json', input: z.object({}).optional(), handler: (_i, ctx) => run(() => getCommission(requireToken(ctx))) }),
  /** F08: retira la oferta propia pendiente (`LOGYX-BID-005` si la subasta ya no acepta cambios). */
  withdrawBid: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid() }),
    handler: ({ id }, ctx) => run(() => withdrawBid(requireToken(ctx), id)),
  }),
  /** 2.8: baja el monto y/o cambia el vehículo de mi oferta pendiente (`BID-006` si sube, `BID-007` si no cambia nada). */
  improveBid: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), amount: z.number().positive('Ingresa un monto válido').optional(), vehicleId: z.uuid().optional() }),
    handler: ({ id, ...body }, ctx) => run(() => improveBid(requireToken(ctx), id, body)),
  }),
  bidRevisions: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => bidRevisions(requireToken(ctx), id)) }),
  /** 2.8: mediana adjudicada de la ruta; `null` si hay menos de 5 envíos (no se muestra nada). */
  laneStats: defineAction({ accept: 'json', input: z.object({ requestId: z.uuid() }), handler: ({ requestId }, ctx) => run(() => laneStats(requireToken(ctx), requestId)) }),

  // 2.3: búsquedas guardadas (cada persona ve solo las suyas).
  savedSearches: defineAction({ accept: 'json', input: z.object({}).optional(), handler: (_i, ctx) => run(() => listSavedSearches(requireToken(ctx))) }),
  saveSearch: defineAction({ accept: 'json', input: savedSearchInput, handler: (body, ctx) => run(() => createSavedSearch(requireToken(ctx), body)) }),
  updateSavedSearch: defineAction({
    accept: 'json',
    input: savedSearchInput.extend({ id: z.uuid() }),
    handler: ({ id, ...body }, ctx) => run(() => updateSavedSearch(requireToken(ctx), id, body)),
  }),
  deleteSavedSearch: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => deleteSavedSearch(requireToken(ctx), id)) }),
};
