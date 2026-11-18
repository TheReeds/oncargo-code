import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { createDriver, createVehicle, deactivateDriver, reactivateDriver, resetDriverPassword, setVehicleStatus } from './server';

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
};
