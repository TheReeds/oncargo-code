import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { addCalendarDay, calendarDays, cancelTariff, createTariff, removeCalendarDay } from './server';

const money = z.string().regex(/^\d+(\.\d{1,4})?$/, 'Usa un número con punto decimal');
const reason = z.string().trim().min(3, 'Indica el motivo del cambio').max(300);
const CITY = z.string().regex(/^[A-Z_]{2,30}$/);

/** Operador: tarifario y calendario (2.14). El backend valida rangos (LOGYX-TRF-001) y fechas (TRF-003, CAL-001). */
export const config = {
  createTariff: defineAction({
    accept: 'json',
    input: z.object({
      baseFee: money, fuelPerKm: money, ratePerKmTon: money, tollEveryKm: money, tollPrice: money, handlingRate: money,
      cargoMultipliers: z.record(z.string(), money),
      effectiveFrom: z.iso.datetime({ offset: true }).optional(),
      reason,
      expectedLatestVersion: z.number().int().min(0).optional(),
    }),
    handler: (i, ctx) => run(() => createTariff(requireToken(ctx), {
      ...i,
      baseFee: Number(i.baseFee), fuelPerKm: Number(i.fuelPerKm), ratePerKmTon: Number(i.ratePerKmTon),
      tollEveryKm: Number(i.tollEveryKm), tollPrice: Number(i.tollPrice), handlingRate: Number(i.handlingRate),
      cargoMultipliers: Object.fromEntries(Object.entries(i.cargoMultipliers).map(([k, v]) => [k, Number(v)])),
    })),
  }),
  cancelTariff: defineAction({
    accept: 'json',
    input: z.object({ version: z.number().int().min(1), reason }),
    handler: ({ version, reason }, ctx) => run(() => cancelTariff(requireToken(ctx), version, reason)),
  }),
  addCalendarDay: defineAction({
    accept: 'json',
    input: z.object({
      day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Elige la fecha'),
      kind: z.enum(['HOLIDAY', 'STRIKE', 'OTHER']),
      name: z.string().trim().min(1, 'Indica el nombre').max(120),
      cities: z.array(CITY).max(20).optional(),
      skipRecurrence: z.boolean().optional(),
      note: z.string().trim().max(300).optional(),
    }),
    handler: (i, ctx) => run(() => addCalendarDay(requireToken(ctx), i)),
  }),
  removeCalendarDay: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), reason }),
    handler: ({ id, reason }, ctx) => run(() => removeCalendarDay(requireToken(ctx), id, reason)),
  }),
  /** Feriados y paros de un rango (cualquier rol): aviso al elegir la fecha de recojo. */
  calendar: defineAction({
    accept: 'json',
    input: z.object({ from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
      city: CITY.optional() }),
    handler: (q, ctx) => run(() => calendarDays(requireToken(ctx), q)),
  }),
};
