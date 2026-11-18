import { defineAction } from 'astro:actions';
import { z } from 'astro/zod';
import { requireToken, run } from '@/lib/actions';
import { LIMITS } from '@/lib/limits';
import { getShipmentReviews, submitReview } from './server';

const score = (msg: string) => z.number({ error: msg }).int(msg).min(1, msg).max(5, msg);

export const reputation = {
  forShipment: defineAction({
    accept: 'json',
    input: z.object({ shipmentId: z.uuid() }),
    handler: ({ shipmentId }, ctx) => run(() => getShipmentReviews(requireToken(ctx), shipmentId)),
  }),
  submit: defineAction({
    accept: 'json',
    input: z.object({
      shipmentId: z.uuid(),
      punctuality: score('Califica la puntualidad.'),
      cargoCondition: score('Califica la carga.'),
      communication: score('Califica la comunicación.'),
      comment: z.string().trim().max(LIMITS.reviewComment, `El comentario admite hasta ${LIMITS.reviewComment} caracteres.`).optional(),
    }),
    handler: ({ shipmentId, comment, ...scores }, ctx) => run(() => submitReview(requireToken(ctx), shipmentId, { ...scores, ...(comment ? { comment } : {}) })),
  }),
};
