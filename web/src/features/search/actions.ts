import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { search } from './server';

export const searchActions = {
  /** Ctrl+K: solicitudes, envíos y contrapartes (solo lo que el usuario ya puede ver). */
  query: defineAction({
    accept: 'json',
    input: z.object({ q: z.string().trim().min(2).max(80) }),
    handler: ({ q }, ctx) => run(() => search(requireToken(ctx), q)),
  }),
};
