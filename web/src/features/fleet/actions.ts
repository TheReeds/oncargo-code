import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { LIMITS } from '@/lib/limits';
import { requireToken, run } from '@/lib/actions';
import { listUnassigned, archiveVehicle, assignResources, getSchedule, getSuggestions, listFleetDocuments, patchDriver, updateVehicleConfig, vehicleDocuments, driverDocuments, createDriver, createVehicle, updateVehicle, deactivateDriver, reactivateDriver, resetDriverPassword, setVehicleStatus } from './server';

export const fleet = {
  createVehicle: defineAction({
    accept: 'json',
    input: z.object({
      plate: z.string().min(5, 'Ingresa la placa'),
      type: z.enum(['VAN', 'TRUCK_SMALL', 'TRUCK_MEDIUM', 'TRUCK_LARGE', 'PLATFORM', 'REFRIGERATED', 'TANKER']),
      maxWeightKg: z.number().positive('Ingresa la capacidad en kg'),
      maxVolumeM3: z.number().positive().optional(),
    }),
    handler: (input, ctx) => run(() => createVehicle(requireToken(ctx), input)),
  }),
  createDriver: defineAction({
    accept: 'json',
    input: z.object({
      fullName: z.string().min(2, 'Ingresa el nombre'), email: z.email('Correo inválido'),
      password: z.string().min(8, 'Mínimo 8 caracteres'), licenseNumber: z.string().min(3, 'Ingresa la licencia'),
    }),
    handler: (input, ctx) => run(() => createDriver(requireToken(ctx), input)),
  }),
  /** Corrige tipo, kg y m³ (la placa no cambia). Con envíos, ofertas pendientes o viajes solo el volumen (409 `LOGYX-FLT-004`). */
  updateVehicle: defineAction({
    accept: 'json',
    input: z.object({
      id: z.uuid(),
      type: z.enum(['VAN', 'TRUCK_SMALL', 'TRUCK_MEDIUM', 'TRUCK_LARGE', 'PLATFORM', 'REFRIGERATED', 'TANKER']).optional(),
      maxWeightKg: z.number().min(100, 'La capacidad mínima es 100 kg').optional(),
      maxVolumeM3: z.number().min(0.1, 'El volumen mínimo es 0.1 m³').optional(),
    }),
    handler: ({ id, ...body }, ctx) => run(() => updateVehicle(requireToken(ctx), id, body)),
  }),
  archiveVehicle: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => archiveVehicle(requireToken(ctx), id)) }),
  setVehicleStatus: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), status: z.enum(['AVAILABLE', 'IN_ROUTE', 'MAINTENANCE']) }),
    handler: ({ id, status }, ctx) => run(() => setVehicleStatus(requireToken(ctx), id, status)),
  }),
  deactivateDriver: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => deactivateDriver(requireToken(ctx), id)) }),
  reactivateDriver: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => reactivateDriver(requireToken(ctx), id)) }),
  resetDriverPassword: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), newPassword: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres.').max(72, 'La contraseña admite hasta 72 caracteres.') }),
    handler: ({ id, newPassword }, ctx) => run(() => resetDriverPassword(requireToken(ctx), id, newPassword)),
  }),
  // ── 2.4: documentos y configuración ──
  documents: defineAction({
    accept: 'json',
    input: z.object({ ownerType: z.enum(['VEHICLE', 'DRIVER']), id: z.uuid() }),
    handler: ({ ownerType, id }, ctx) => run(() => (ownerType === 'VEHICLE' ? vehicleDocuments(requireToken(ctx), id) : driverDocuments(requireToken(ctx), id))),
  }),
  expiries: defineAction({ accept: 'json', input: z.object({}).optional(), handler: (_i, ctx) => run(() => listFleetDocuments(requireToken(ctx))) }),
  /** Restringe los tipos de carga (nunca amplía la matriz RN02: `LOGYX-FLT-009`) y declara el equipamiento. */
  vehicleConfig: defineAction({
    accept: 'json',
    input: z.object({
      id: z.uuid(),
      equipment: z.array(z.enum(['GPS', 'RAMP', 'LIFTGATE', 'TARP', 'REEFER_CERTIFIED', 'STRAPS'])),
      acceptedCargoTypes: z.array(z.enum(['GENERAL', 'FRAGILE', 'PERISHABLE', 'HAZARDOUS', 'BULK', 'OVERSIZED'])),
    }),
    handler: ({ id, ...body }, ctx) => run(() => updateVehicleConfig(requireToken(ctx), id, body)),
  }),
  /** Solo ADMIN (`MANAGE_DRIVERS`): categoría de licencia y DNI. */
  patchDriver: defineAction({
    accept: 'json',
    input: z.object({ id: z.uuid(), licenseCategory: z.string().max(10).optional(), dni: z.string().max(12).optional() }),
    handler: ({ id, ...body }, ctx) => run(() => patchDriver(requireToken(ctx), id, body)),
  }),
  // ── 2.13: agenda y asignación ──
  schedule: defineAction({
    accept: 'json',
    input: z.object({ from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/) }),
    handler: ({ from, to }, ctx) => run(() => getSchedule(requireToken(ctx), from, to)),
  }),
  unassigned: defineAction({ accept: 'json', input: z.object({}).optional(), handler: (_i, ctx) => run(() => listUnassigned(requireToken(ctx))) }),
  suggestions: defineAction({ accept: 'json', input: z.object({ shipmentId: z.uuid() }), handler: ({ shipmentId }, ctx) => run(() => getSuggestions(requireToken(ctx), shipmentId)) }),
  /** Conductor y vehículo en un solo paso (atómico); `reason` obligatorio si el envío ya se recogió. */
  assign: defineAction({
    accept: 'json',
    input: z.object({ shipmentId: z.uuid(), driverId: z.uuid().optional(), vehicleId: z.uuid().optional(), reason: z.string().trim().max(LIMITS.shipmentReason).optional() }),
    handler: ({ shipmentId, ...body }, ctx) => run(() => assignResources(requireToken(ctx), shipmentId, body)),
  }),
};
