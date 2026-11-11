import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { clearSession, homeFor, writeSession, readSession } from '@/lib/session';
import { login, register } from './server';
import { run, clientIpOf } from '@/lib/actions';

const homeAfter = (cookies: any) => {
  const s = readSession(cookies)!;
  return homeFor(s);
};

export const auth = {
  login: defineAction({
    accept: 'json',
    input: z.object({ email: z.email('Ingresa un correo válido'), password: z.string().min(1, 'Ingresa tu contraseña') }),
    handler: async (input, ctx) => {
      const r = await run(() => login(input.email, input.password, clientIpOf(ctx)));
      writeSession(ctx.cookies, r.accessToken!, r.expiresIn ?? 28800);
      return { home: homeAfter(ctx.cookies) };
    },
  }),
  register: defineAction({
    accept: 'json',
    input: z.object({
      organizationType: z.enum(['PYME', 'CARRIER']),
      legalName: z.string().min(2, 'Ingresa la razón social'),
      ruc: z.string().regex(/^\d{11}$/, 'El RUC debe tener 11 dígitos'),
      city: z.string().optional(),
      phone: z.string().optional(),
      fullName: z.string().min(2, 'Ingresa tu nombre'),
      email: z.email('Ingresa un correo válido'),
      password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres'),
      acceptPrivacyPolicy: z.literal(true, { error: 'Debes aceptar la política de privacidad' }),
    }),
    handler: async (input, ctx) => {
      const r = await run(() => register(input, clientIpOf(ctx)));
      writeSession(ctx.cookies, r.accessToken!, r.expiresIn ?? 28800);
      return { home: homeAfter(ctx.cookies) };
    },
  }),
  logout: defineAction({
    handler: async (_i, ctx) => {
      clearSession(ctx.cookies);
      return { ok: true };
    },
  }),
};
