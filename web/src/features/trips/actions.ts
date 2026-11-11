import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import * as s from './server';

const id = z.object({ id: z.uuid() });

export const trips = {
  // Lecturas para el refresco en vivo (trip_booking.updated, trip.cancelled, smart_load.*).
  myBookings: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => s.listMyBookings(requireToken(ctx))) }),
  smartLoads: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => s.listSmartLoads(requireToken(ctx))) }),
  detail: defineAction({
    accept: 'json',
    input: id,
    handler: ({ id }, ctx) => run(async () => {
      const token = requireToken(ctx);
      const [trip, bookings] = await Promise.all([s.getTrip(token, id), s.listTripBookings(token, id)]);
      return { trip, bookings };
    }),
  }),
  create: defineAction({
    accept: 'json',
    input: z.object({
      originCity: z.string().min(1), destinationCity: z.string().min(1), departureAt: z.string().min(10), vehicleId: z.uuid(),
      capacityKg: z.number().positive(), pricingMode: z.enum(['PER_KG', 'PER_TRIP']), rate: z.number().positive(), notes: z.string().max(500).optional(),
    }),
    handler: (i, ctx) => run(() => s.createTrip(requireToken(ctx), i)),
  }),
  close: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.closeTrip(requireToken(ctx), id)) }),
  cancel: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.cancelTrip(requireToken(ctx), id)) }),
  confirmBooking: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.confirmBooking(requireToken(ctx), id)) }),
  rejectBooking: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason: z.string().max(300).optional() }),
    handler: ({ id, reason }, ctx) => run(() => s.rejectBooking(requireToken(ctx), id, reason)),
  }),
  book: defineAction({
    accept: 'json',
    input: z.object({
      tripId: z.uuid(), weightKg: z.number().positive(), cargoDescription: z.string().min(1).max(500),
      cargoType: z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED']), pickupAddress: z.string().min(1).max(300), deliveryAddress: z.string().min(1).max(300),
    }),
    handler: ({ tripId, ...body }, ctx) => run(() => s.createBooking(requireToken(ctx), tripId, body)),
  }),
  cancelBooking: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.cancelBooking(requireToken(ctx), id)) }),
  generateSmartLoads: defineAction({ accept: 'json', handler: (_, ctx) => run(() => s.generateSmartLoads(requireToken(ctx))) }),
  acceptSmartLoad: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.acceptSmartLoad(requireToken(ctx), id)) }),
  rejectSmartLoad: defineAction({ accept: 'json', input: id, handler: ({ id }, ctx) => run(() => s.rejectSmartLoad(requireToken(ctx), id)) }),
};
