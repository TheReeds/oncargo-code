import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import * as s from './server';

export const routing = {
  /** Geometría por carretera para los mapas (ADR-0008). Devuelve el formato de `web/src/lib/routing`. */
  route: defineAction({
    accept: 'json',
    input: z.object({ stops: z.array(z.string().regex(/^[A-Z_]{2,20}$/)).min(2).max(25), optimize: z.boolean().optional() }),
    handler: ({ stops, optimize }, ctx) => run(async () => s.toRouteGeometry(await s.getRoute(requireToken(ctx), stops, optimize))),
  }),
};
