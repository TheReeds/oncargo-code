// Estado de MI oferta (transportista) a partir de la oferta y de la solicitud (F15, F51) y la posición en la subasta (V15).
import type { RequestView } from '@/lib/api/server';

export type BidState = 'PENDING' | 'ACCEPTED' | 'LOST' | 'CANCELLED' | 'WITHDRAWN';

/**
 * - `ACCEPTED`: la PYME me eligió. - `WITHDRAWN`: la retiré yo.
 * - `CANCELLED`: la PYME canceló la solicitud (la oferta puede seguir "pendiente" en la API).
 * - `LOST`: otra oferta fue elegida (REJECTED, o la solicitud ya se adjudicó a otro).
 * - `PENDING`: sigue viva (la subasta puede estar abierta o cerrada esperando a la PYME).
 */
export function bidState(r: Pick<RequestView, 'status' | 'myBid'>): BidState {
  const s = r.myBid?.status;
  if (s === 'ACCEPTED') return 'ACCEPTED';
  if (s === 'WITHDRAWN') return 'WITHDRAWN';
  if (r.status === 'CANCELLED') return 'CANCELLED';
  if (s === 'REJECTED' || r.status === 'MATCHED') return 'LOST';
  return 'PENDING';
}

export const BID_STATE_LABEL: Record<BidState, string> = { PENDING: 'Pendiente', ACCEPTED: 'Aceptada', LOST: 'Perdida', CANCELLED: 'Cancelada', WITHDRAWN: 'Retirada' };

export const CLOSE_REASON_LABEL: Record<string, string> = {
  TIMEOUT: 'Venció el tiempo de la subasta',
  MAX_BIDS: 'Se alcanzó el máximo de ofertas',
  ACCEPTED: 'La PYME eligió una oferta',
  CANCELLED: 'La PYME canceló la solicitud',
};

const BAND_TEXT: Record<string, string> = {
  BEST: 'Tu oferta es la mejor',
  WITHIN_5_PCT: 'A menos de 5 % de la mejor',
  WITHIN_15_PCT: 'Entre 5 % y 15 % por encima de la mejor',
  ABOVE_15_PCT: 'A más de 15 % de la mejor',
};

/** Texto de la posición: "Vas 2.º de 5 · A menos de 5 % de la mejor". Nunca muestra montos de otras ofertas (RN04). */
export function standingText(s: RequestView['myBidStanding'] | undefined): { headline: string; band: string | null; tone: 'good' | 'warn' | 'neutral' } | null {
  if (!s || s.rank == null || !s.bidCount) return null;
  const pos = `${s.rank}.º de ${s.bidCount}`;
  const headline = s.finalStanding ? `Quedaste ${pos}` : s.isBest ? `Vas primero de ${s.bidCount}` : `Vas ${pos}`;
  const band = s.vsBest ? BAND_TEXT[s.vsBest] ?? null : null;
  const tone = s.isBest ? 'good' : s.vsBest === 'ABOVE_15_PCT' || s.vsBest === 'WITHIN_15_PCT' ? 'warn' : 'neutral';
  return { headline, band, tone };
}
