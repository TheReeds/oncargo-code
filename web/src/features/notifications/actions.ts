import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import type { NotificationPreferenceChange } from './server';
import { getEmailSettings, getPreferences, listNotifications, markAllRead, markRead, unreadCount, updateEmailSettings, updatePreferences } from './server';

export const notifications = {
  list: defineAction({
    accept: 'json',
    input: z.object({ unread: z.boolean().optional(), mine: z.boolean().optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: (q, ctx) => run(async () => ({ ...(await listNotifications(requireToken(ctx), q)), orgType: ctx.locals.session?.orgType ?? null })),
  }),
  unreadCount: defineAction({ accept: 'json', handler: (_i, ctx) => run(async () => ({ unread: await unreadCount(requireToken(ctx)) })) }),
  markRead: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => markRead(requireToken(ctx), id)) }),
  markAllRead: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => markAllRead(requireToken(ctx))) }),
  /** V16: preferencias de aviso de la persona (cualquier rol, también Lector). */
  preferences: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => getPreferences(requireToken(ctx))) }),
  updatePreferences: defineAction({
    accept: 'json',
    input: z.object({ preferences: z.array(z.object({ category: z.string().min(1).max(24), inApp: z.boolean(), email: z.boolean() })).min(1).max(20) }),
    handler: ({ preferences }, ctx) => run(() => updatePreferences(requireToken(ctx), preferences as NotificationPreferenceChange[])),
  }),
  /** V24 (2.2): correo activo, inmediato o resumen diario y horario de silencio (cualquier rol, también Lector). */
  emailSettings: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => getEmailSettings(requireToken(ctx))) }),
  updateEmailSettings: defineAction({
    accept: 'json',
    input: z.object({
      emailEnabled: z.boolean(),
      mode: z.enum(['IMMEDIATE', 'DAILY']),
      digestHour: z.number().int().min(0).max(23),
      quietFrom: z.string().regex(/^\d{2}:\d{2}$/).nullable(),
      quietTo: z.string().regex(/^\d{2}:\d{2}$/).nullable(),
    }),
    handler: (input, ctx) => run(() => updateEmailSettings(requireToken(ctx), input as never)),
  }),
};
