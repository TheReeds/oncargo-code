import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { LIMITS } from '@/lib/limits';
import { changeUserRole, deactivateUser, getOrganization, getOrganizationPreferences, inviteUser, listUsers, reactivateUser, resendInvitation, updateOrganization, updateOrganizationPreferences } from './server';

const role = z.enum(['ADMIN', 'OPS', 'VIEWER']);

export const organization = {
  get: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => getOrganization(requireToken(ctx))) }),
  /** null = sin cambio; "" borra ciudad, teléfono o dirección. `legalName`/`ruc` solo con `legalDataEditable`. */
  update: defineAction({
    accept: 'json',
    input: z.object({
      legalName: z.string().trim().min(2, 'Ingresa la razón social.').max(LIMITS.legalName).optional(),
      ruc: z.string().regex(/^\d{11}$/, 'El RUC debe tener 11 dígitos.').optional(),
      city: z.string().max(80).optional(),
      phone: z.string().trim().max(20).optional(),
      address: z.string().trim().max(200).optional(),
    }),
    handler: (input, ctx) => run(() => updateOrganization(requireToken(ctx), input)),
  }),
  users: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => listUsers(requireToken(ctx))) }),
  invite: defineAction({
    accept: 'json',
    input: z.object({
      fullName: z.string().trim().min(2, 'Ingresa el nombre completo.').max(LIMITS.fullName, `El nombre admite hasta ${LIMITS.fullName} caracteres.`),
      email: z.email('Revisa el correo: debe tener el formato nombre@empresa.pe.'),
      role,
    }),
    handler: (input, ctx) => run(() => inviteUser(requireToken(ctx), input)),
  }),
  resendInvitation: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => resendInvitation(requireToken(ctx), id)) }),
  changeRole: defineAction({ accept: 'json', input: z.object({ id: z.uuid(), role }), handler: ({ id, role }, ctx) => run(() => changeUserRole(requireToken(ctx), id, role)) }),
  deactivateUser: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => deactivateUser(requireToken(ctx), id)) }),
  reactivateUser: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => reactivateUser(requireToken(ctx), id)) }),
  /** V24 (2.2): preferencias de la organización. Lee cualquiera; escribe solo el ADMIN (la API responde AUTH-008). */
  preferences: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => getOrganizationPreferences(requireToken(ctx))) }),
  updatePreferences: defineAction({
    accept: 'json',
    input: z.object({
      cities: z.array(z.string().min(2).max(20)).max(30),
      routes: z.array(z.object({ origin: z.string().min(2).max(20), destination: z.string().min(2).max(20) })).max(20, 'Puedes declarar hasta 20 rutas.'),
      cargoTypes: z.array(z.string().min(2).max(20)).max(20),
      routeAlerts: z.boolean().optional(),
      minPricePerKm: z.number().positive().max(100).nullable().optional(),
      defaultPickupFrom: z.string().regex(/^\d{2}:\d{2}$/).nullable().optional(),
      defaultPickupTo: z.string().regex(/^\d{2}:\d{2}$/).nullable().optional(),
      paymentTermsDays: z.number().int().min(0).max(120).nullable().optional(),
    }),
    handler: (input, ctx) => run(() => updateOrganizationPreferences(requireToken(ctx), input as never)),
  }),
};
