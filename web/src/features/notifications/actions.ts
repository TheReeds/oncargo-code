import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { listNotifications, markAllRead, markRead, unreadCount } from './server';

export const notifications = {
  list: defineAction({
    accept: 'json',
    input: z.object({ unread: z.boolean().optional(), page: z.number().int().min(0).optional(), size: z.number().int().min(1).max(100).optional() }),
    handler: (q, ctx) => run(async () => ({ ...(await listNotifications(requireToken(ctx), q)), orgType: ctx.locals.session?.orgType ?? null })),
  }),
  unreadCount: defineAction({ accept: 'json', handler: (_i, ctx) => run(async () => ({ unread: await unreadCount(requireToken(ctx)) })) }),
  markRead: defineAction({ accept: 'json', input: z.object({ id: z.uuid() }), handler: ({ id }, ctx) => run(() => markRead(requireToken(ctx), id)) }),
  markAllRead: defineAction({ accept: 'json', handler: (_i, ctx) => run(() => markAllRead(requireToken(ctx))) }),
};
