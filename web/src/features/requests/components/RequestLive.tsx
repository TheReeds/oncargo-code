import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Countdown } from '@/components/domain/Countdown';
import { buttonVariants } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateTime, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';
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

/** Estado y sello de verificación de la solicitud, en el slot `meta` del encabezado (vivo). */
export function RequestHeaderMeta({ status, verified }: { status: string; verified?: boolean | null }) {
  const { request } = useRequestState({ request: { status }, bids: [] });
  return (
    <>
      <StatusPill status={request.status ?? status} />
      <VerifiedBadge verified={verified} />
    </>
  );
}

export function RequestHeaderActions({ requestId, status }: { requestId: string; status: string; verified?: boolean | null }) {
  const { request } = useRequestState({ request: { status }, bids: [] });
  const st = request.status ?? status;
  return (
    <>
      {st === 'PUBLISHED' && <CancelRequestButton id={requestId} />}
      {st === 'MATCHED' && <a href="/pyme/envios" className={buttonVariants()}>Ver envío</a>}
    </>
  );
}

const CLOSE_REASON: Record<string, string> = { TIMEOUT: 'Venció el tiempo', MAX_BIDS: 'Se alcanzó el máximo de ofertas', ACCEPTED: 'Oferta aceptada', CANCELLED: 'Solicitud cancelada' };

/** Subasta (cuenta regresiva, n/máx) y precios de referencia, en una sola tarjeta. */
export function AuctionCard({ auction, suggested, floor }: { auction: LiveAuction; suggested?: number | null; floor?: number | null }) {
  const { request } = useRequestState({ request: { auction }, bids: [] });
  const a = request.auction !== undefined ? request.auction : auction;
  const max = a?.maxBids ?? 5;
  const count = a?.bidCount ?? 0;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Subasta</CardTitle>
        {a && <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', a.status === 'OPEN' ? 'bg-amber-50 text-amber-700' : 'bg-steel-100 text-ink-700')}>{a.status === 'OPEN' ? 'Abierta' : 'Cerrada'}</span>}
      </CardHeader>
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
              <div className="mb-1 flex justify-between text-xs"><span className="text-ink-500">Ofertas</span><span className="font-mono font-semibold" data-testid="bid-count">{count}/{max}</span></div>
              <div className="h-2 overflow-hidden rounded-full bg-steel-200" role="progressbar" aria-label="Ofertas recibidas" aria-valuemin={0} aria-valuemax={max} aria-valuenow={count}>
                <div className={cn('h-full rounded-full', count >= max ? 'bg-amber-500' : 'bg-petrol-700')} style={{ width: `${Math.min(100, (count / max) * 100)}%` }} />
              </div>
              <p className="mt-1 text-xs text-ink-500">La subasta cierra al llegar a {max} ofertas o al terminar el tiempo.</p>
            </div>
          </>
        ) : <p className="text-sm text-ink-500">Esta solicitud no tiene una subasta abierta.</p>}
        <dl className="space-y-2 border-t border-steel-200 pt-4 text-sm">
          <div className="flex items-baseline justify-between gap-3"><dt className="text-ink-700">Precio sugerido</dt><dd className="font-mono font-semibold tabular-nums text-ink-900">{formatMoney(suggested)}</dd></div>
          <div className="flex items-baseline justify-between gap-3"><dt className="text-ink-700">Piso de subasta</dt><dd className="font-mono tabular-nums text-ink-700">{formatMoney(floor)}</dd></div>
        </dl>
      </CardBody>
    </Card>
  );
}
