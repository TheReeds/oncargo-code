import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { clearSession, homeFor, writeSession, readSession } from '@/lib/session';
import { acceptTerms, login, me, register } from './server';
import { run, clientIpOf, requireToken } from '@/lib/actions';

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
      legalName: z.string().min(2, 'Ingresa la razón social').max(200, 'La razón social admite hasta 200 caracteres'),
      ruc: z.string().regex(/^\d{11}$/, 'El RUC debe tener 11 dígitos'),
      city: z.string().optional(),
      phone: z.string().optional(),
      fullName: z.string().min(2, 'Ingresa tu nombre').max(150, 'El nombre admite hasta 150 caracteres'),
      email: z.email('Ingresa un correo válido'),
      password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres'),
      acceptPrivacyPolicy: z.literal(true, { error: 'Debes aceptar la política de privacidad' }),
      acceptTerms: z.literal(true, { error: 'Debes aceptar los términos y condiciones' }),
      /** Versión de los términos que el usuario vio (`GET /public/legal`); si ya cambió, la API responde 409 AUTH-012. */
      termsVersion: z.string().max(40).optional(),
    }),
    handler: async (input, ctx) => {
      const r = await run(() => register(input, clientIpOf(ctx)));
      writeSession(ctx.cookies, r.accessToken!, r.expiresIn ?? 28800);
      return { home: homeAfter(ctx.cookies) };
    },
  }),
  /** Estado de verificación de mi organización (RN01), para el banner en vivo. */
  verification: defineAction({
    accept: 'json',
    handler: async (_i, ctx) => {
      const u = await me(requireToken(ctx));
      return { status: u?.verificationStatus ?? null, note: u?.verificationNote ?? null };
    },
  }),
  /** V24 (2.17): aceptar la versión vigente de los Términos (diálogo de `AppShell`). Cualquier rol, también Lector. */
  acceptTerms: defineAction({
    accept: 'json',
    input: z.object({ termsVersion: z.string().min(1).max(40) }),
    handler: ({ termsVersion }, ctx) => run(() => acceptTerms(requireToken(ctx), termsVersion)),
  }),
  logout: defineAction({
    handler: async (_i, ctx) => {
      clearSession(ctx.cookies);
      return { ok: true };
    },
  }),
};
