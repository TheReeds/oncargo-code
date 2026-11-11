import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Countdown } from '@/components/domain/Countdown';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateTime } from '@/lib/format';
import { useLiveRefresh, useRealtime } from '@/lib/realtime';
import type { BidView } from '@/lib/api/server';
import CancelRequestButton from './CancelRequestButton';

/**
 * Detalle de solicitud de la PYME en vivo. `RequestLiveSync` (isla sin UI) escucha el stream, vuelve a pedir
 * solicitud + ofertas por Astro Action y avisa a las demás islas con un evento de ventana; `useRequestState` lo recibe.
 */
export const REQUEST_STATE_EVENT = 'logyx:request-state';

export type LiveAuction = { status?: string | null; closesAt?: string | null; closeReason?: string | null; bidCount?: number | null; maxBids?: number | null } | null;
export type LiveRequest = { id?: string; status?: string; auction?: LiveAuction; requesterRucVerified?: boolean | null };
type State = { request: LiveRequest; bids: BidView[] };

const EVENTS = ['bid.placed', 'counter_offer.updated', 'request.matched', 'auction.updated'];

export function RequestLiveSync({ requestId }: { requestId: string }) {
  const refresh = async () => {
    const { data } = await actions.requests.detail({ id: requestId });
    if (data) window.dispatchEvent(new CustomEvent(REQUEST_STATE_EVENT, { detail: data }));
  };
  useLiveRefresh({ types: EVENTS, match: (e) => e.data.requestId === requestId, refresh, fallbackMs: 60_000, debounceMs: 150 });
  useRealtime(['request.matched'], (e) => {
    if (e.data.requestId !== requestId) return;
    toast.success('Solicitud adjudicada', 'El envío ya fue creado. Puedes verlo en Envíos.');
  });
  return null;
}

/** Estado vivo de la solicitud/ofertas, partiendo de lo renderizado en el servidor. */
export function useRequestState(initial: State): State {
  const [state, setState] = useState<State>(initial);
  useEffect(() => {
    const on = (e: Event) => setState((e as CustomEvent<State>).detail);
    window.addEventListener(REQUEST_STATE_EVENT, on);
    return () => window.removeEventListener(REQUEST_STATE_EVENT, on);
  }, []);
  return state;
}

export function RequestHeaderActions({ requestId, status, verified }: { requestId: string; status: string; verified?: boolean | null }) {
  const { request } = useRequestState({ request: { status }, bids: [] });
  const st = request.status ?? status;
  return (
    <>
      <VerifiedBadge verified={verified} />
      <StatusPill status={st} />
      {st === 'PUBLISHED' && <CancelRequestButton id={requestId} />}
      {st === 'MATCHED' && <a href="/pyme/envios" className={buttonVariants()}>Ver envío</a>}
    </>
  );
}

const CLOSE_REASON: Record<string, string> = { TIMEOUT: 'Venció el tiempo', MAX_BIDS: 'Se alcanzó el máximo de ofertas', ACCEPTED: 'Oferta aceptada', CANCELLED: 'Solicitud cancelada' };

export function AuctionCard({ auction }: { auction: LiveAuction }) {
  const { request } = useRequestState({ request: { auction }, bids: [] });
  const a = request.auction !== undefined ? request.auction : auction;
  return (
    <Card>
      <CardHeader><CardTitle>Subasta</CardTitle></CardHeader>
      <CardBody className="space-y-4">
        {a ? (
          <>
            <div>
              <div className="text-xs font-medium text-ink-500">{a.status === 'OPEN' ? 'Cierra en' : 'Estado'}</div>
              {a.status === 'OPEN'
                ? <Countdown closesAt={a.closesAt} className="text-2xl font-semibold" />
                : <div className="font-medium">Cerrada{a.closeReason ? ` · ${CLOSE_REASON[a.closeReason] ?? a.closeReason}` : ''}</div>}
              <div className="text-xs text-ink-500">{formatDateTime(a.closesAt)}</div>
            </div>
            <div>
              <div className="mb-1 flex justify-between text-xs"><span className="text-ink-500">Ofertas</span><span className="font-mono font-semibold" data-testid="bid-count">{a.bidCount ?? 0}/{a.maxBids ?? 5}</span></div>
              <div className="h-2 overflow-hidden rounded-full bg-steel-200" role="progressbar" aria-label="Ofertas recibidas" aria-valuemin={0} aria-valuemax={a.maxBids ?? 5} aria-valuenow={a.bidCount ?? 0}>
                <div className="h-full rounded-full bg-petrol-700" style={{ width: `${Math.min(100, ((a.bidCount ?? 0) / (a.maxBids ?? 5)) * 100)}%` }} />
              </div>
            </div>
          </>
        ) : <p className="text-sm text-ink-500">Esta solicitud no tiene una subasta abierta.</p>}
      </CardBody>
    </Card>
  );
}
