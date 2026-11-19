import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { requestAccess } from './server';

export const access = {
  /** Cualquier rol (también Lector): avisa a los ADMIN para que le cambien el rol. 429 `ACC-002` si lo pidió hace poco. */
  request: defineAction({
    accept: 'json',
    input: z.object({
      permission: z.enum(['READ', 'OPERATE', 'MANAGE_USERS', 'MANAGE_ORGANIZATION', 'MANAGE_DRIVERS']),
      reason: z.string().trim().max(300).optional(),
    }),
    handler: (i, ctx) => run(() => requestAccess(requireToken(ctx), { permission: i.permission, reason: i.reason || undefined })),
  }),
};
