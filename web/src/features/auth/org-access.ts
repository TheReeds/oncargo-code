import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { apiClient, unwrap } from '@/lib/api/client';
import { clientIpOf, run } from '@/lib/actions';

/**
 * «Pedir acceso a la organización» desde el registro (roadmap 2.15): tras `LOGYX-ORG-002` (RUC ya registrado), avisa por
 * correo a los ADMIN de esa empresa. Sin sesión; reenvía la IP del visitante (el límite del backend es por IP). La API
 * responde siempre lo mismo.
 */
export const orgAccess = {
  request: defineAction({
    accept: 'json',
    input: z.object({
      ruc: z.string().regex(/^\d{11}$/, 'El RUC debe tener 11 dígitos'),
      fullName: z.string().trim().min(2, 'Ingresa tu nombre').max(150),
      email: z.email('Ingresa un correo válido'),
    }),
    handler: (input, ctx) => run(() => unwrap(apiClient(undefined, clientIpOf(ctx)).POST('/api/v1/public/organization-access-requests', { body: input }))),
  }),
};
