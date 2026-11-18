import { defineAction, ActionError } from 'astro:actions';
import { z } from 'astro/zod';
import { clearSession, writeSession } from '@/lib/session';
import { clientIpOf, requireToken, run } from '@/lib/actions';
import { changePassword, forgotPassword, inspectPasswordToken, logoutAll, resetPassword, PASSWORD_MAX, PASSWORD_MIN } from './password';

const newPassword = z.string().min(PASSWORD_MIN, `La contraseña debe tener al menos ${PASSWORD_MIN} caracteres.`).max(PASSWORD_MAX, `La contraseña admite hasta ${PASSWORD_MAX} caracteres.`);

export const password = {
  /** `POST /auth/forgot-password`: la respuesta es siempre la misma exista o no el correo. */
  forgot: defineAction({
    accept: 'json',
    input: z.object({ email: z.email('Revisa el correo: debe tener el formato nombre@empresa.pe.') }),
    handler: ({ email }, ctx) => run(() => forgotPassword(email.trim(), clientIpOf(ctx))),
  }),
  /** Qué enlace es (restablecer o invitación), correo enmascarado y vencimiento. Token inválido = 400 `AUTH-005`. */
  inspect: defineAction({
    accept: 'json',
    input: z.object({ token: z.string().min(10).max(200) }),
    handler: ({ token }, ctx) => run(() => inspectPasswordToken(token, clientIpOf(ctx))),
  }),
  /** Define la contraseña (restablecer) o activa la cuenta (invitación, con consentimiento). No inicia sesión. */
  reset: defineAction({
    accept: 'json',
    input: z.object({ token: z.string().min(10).max(200), newPassword, acceptPrivacyPolicy: z.boolean().optional() }),
    handler: (input, ctx) => run(() => resetPassword(input, clientIpOf(ctx))),
  }),
  /** Cambia la contraseña y reemplaza la cookie con el token nuevo (las demás sesiones quedan fuera). */
  change: defineAction({
    accept: 'json',
    input: z.object({ currentPassword: z.string().min(1, 'Ingresa tu contraseña actual.'), newPassword }),
    handler: async (input, ctx) => {
      const r = await run(() => changePassword(requireToken(ctx), input, clientIpOf(ctx)));
      if (!r.accessToken) throw new ActionError({ code: 'INTERNAL_SERVER_ERROR', message: 'No pudimos renovar tu sesión. Ingresa de nuevo.' });
      writeSession(ctx.cookies, r.accessToken, r.expiresIn ?? 28800);
      return { ok: true };
    },
  }),
  /** Cierra la sesión en todos los dispositivos (incluido este): el token actual deja de valer. */
  logoutAll: defineAction({
    accept: 'json',
    handler: async (_i, ctx) => {
      await run(() => logoutAll(requireToken(ctx)));
      clearSession(ctx.cookies);
      return { ok: true };
    },
  }),
};
