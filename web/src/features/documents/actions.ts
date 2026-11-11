import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { listDocuments } from './server';

// La subida (multipart) va por /documentos/subir; aquí solo la lectura del panel.
export const documents = {
  list: defineAction({
    accept: 'json',
    input: z.object({ shipmentId: z.uuid() }),
    handler: async ({ shipmentId }, ctx) => {
      const documents = await run(() => listDocuments(requireToken(ctx), shipmentId));
      return { documents, orgId: ctx.locals.session!.orgId };
    },
  }),
};
