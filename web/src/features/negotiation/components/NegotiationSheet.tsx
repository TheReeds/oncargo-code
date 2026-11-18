import { useCan } from '@/lib/use-can';
import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { formatMoney } from '@/lib/format';
import type { CounterOffer } from '../server';
import { notifyNegotiationChanged, useNegotiationLive } from '../usePolling';
import { ChatPanel } from './ChatPanel';
import { CounterOfferCard } from './CounterOfferCard';
import { CounterOfferDialog } from './CounterOfferDialog';
import { Handshake, Undo2 } from 'lucide-react';

export interface MyBid { id: string; amount?: number | null; status?: string | null }

/** Carga las contra-ofertas de una oferta y las refresca con el tiempo real de la negociación. */
export function useCounterOffers(bidId: string | undefined, enabled = true, pollMs = 30000) {
  const [offers, setOffers] = useState<CounterOffer[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    if (!bidId) return;
    const { data, error } = await actions.negotiation.counterOffers({ bidId });
    if (error) { setError(splitActionError(error).message); setLoaded(true); return; }
    setError(null);
    setOffers(data);
    setLoaded(true);
  }, [bidId]);
  useEffect(() => { if (enabled) load(); }, [load, enabled]);
  useNegotiationLive(() => { load(); }, {
    types: ['counter_offer.updated'],
    match: (e) => e.data.bidId === bidId,
    fallbackMs: pollMs,
    enabled: enabled && !!bidId,
  });
  return { offers, loaded, error, reload: load };
}

/**
 * Transportista: chat de SU hilo con la PYME y la negociación de su oferta (RF26/RF28/F08).
 *  - Contra-oferta de la PYME pendiente: aceptar o rechazar. Contra-oferta propia pendiente: se muestra, sin botones.
 *  - Puede proponer un nuevo monto (`floor` = piso RN03) y retirar la oferta mientras la subasta acepta ofertas (`canWithdraw`).
 */
export default function NegotiationSheet({ open, onOpenChange, requestId, title = 'Conversación con la PYME', description, bid, closed, carrierOrgId: orgProp, floor, canWithdraw }: { open: boolean; onOpenChange: (o: boolean) => void; requestId: string; title?: string; description?: string; bid?: MyBid | null; closed?: boolean; carrierOrgId?: string; floor?: number | null; canWithdraw?: boolean }) {
  const [orgId, setOrgId] = useState<string | null>(orgProp ?? null);
  const [orgError, setOrgError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'accept' | 'reject' | 'withdraw' | null>(null);
  const [proposing, setProposing] = useState(false);
  const { offers, reload } = useCounterOffers(bid?.id, open && !!bid);

  useEffect(() => {
    if (!open || orgId) return;
    actions.negotiation.myOrg({}).then(({ data, error }) => {
      if (error) setOrgError(splitActionError(error).message);
      else setOrgId(data.orgId);
    });
  }, [open, orgId]);

  const bidPending = bid?.status === 'PENDING';
  const pending = bidPending ? offers.find((o) => o.status === 'PENDING') : undefined;
  const received = pending && !pending.proposedByCarrier ? pending : undefined; // la PYME me propuso un monto
  const mine = pending?.proposedByCarrier ? pending : undefined; // yo propuse y espero a la PYME
  const history = offers.filter((o) => o.status !== 'PENDING' || !bidPending);
  const canOperate = useCan('OPERATE'); // Lector: ve la oferta y el chat, sin contraofertar, responder ni retirar
  const canCounter = canOperate && bidPending && !closed && !mine;

  async function accept(o: CounterOffer) {
    const ok = await confirm({
      title: `¿Aceptar la contra-oferta de ${formatMoney(o.amount)}?`,
      description: `Tu oferta pasará de ${formatMoney(o.previousAmount)} a ${formatMoney(o.amount)}. La PYME sigue decidiendo si te adjudica la carga.`,
      confirmLabel: 'Aceptar contra-oferta',
      cancelLabel: 'Volver',
    });
    if (!ok) return;
    setBusy('accept');
    const { error } = await actions.negotiation.accept({ id: o.id! });
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash('Contra-oferta aceptada', 'success');
    window.location.reload();
  }
  async function reject(o: CounterOffer) {
    const ok = await confirm({
      title: `¿Rechazar la contra-oferta de ${formatMoney(o.amount)}?`,
      description: 'Tu oferta se mantiene en el monto actual. Puedes seguir conversando con la PYME.',
      confirmLabel: 'Rechazar contra-oferta',
      cancelLabel: 'Volver',
      tone: 'destructive',
    });
    if (!ok) return;
    setBusy('reject');
    const { error } = await actions.negotiation.reject({ id: o.id! });
    setBusy(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Contra-oferta rechazada');
    notifyNegotiationChanged();
    reload();
  }
  async function withdraw() {
    if (!bid) return;
    const ok = await confirm({
      title: `¿Retirar tu oferta de ${formatMoney(bid.amount)}?`,
      description: 'La PYME dejará de verla y no podrás volver a ofertar por esta solicitud. No se puede deshacer.',
      confirmLabel: 'Retirar oferta',
      cancelLabel: 'Volver',
      tone: 'destructive',
    });
    if (!ok) return;
    setBusy('withdraw');
    const { error } = await actions.marketplace.withdrawBid({ id: bid.id });
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash('Oferta retirada');
    window.location.reload();
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <div className="flex h-full min-h-0 flex-col">
        {bid && (
          <div className="space-y-3 border-b border-steel-200 bg-steel-100 p-4">
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="text-ink-700">Tu oferta</span>
              <span className="flex items-center gap-2"><span className="money text-base">{formatMoney(bid.amount)}</span>{bid.status && <StatusPill status={bid.status} kind="bid" />}</span>
            </div>
            {received && (
              <div>
                <p className="mb-1.5 text-xs font-semibold text-ink-700">La PYME te propuso un nuevo monto</p>
                <CounterOfferCard
                  offer={received}
                  actions={!canOperate ? undefined : <>
                    <Button size="sm" variant="money" loading={busy === 'accept'} disabled={busy === 'reject'} onClick={() => accept(received)}>Aceptar contra-oferta</Button>
                    <Button size="sm" variant="outline" loading={busy === 'reject'} disabled={busy === 'accept'} onClick={() => reject(received)}>Rechazar</Button>
                  </>}
                />
              </div>
            )}
            {mine && (
              <div>
                <p className="mb-1.5 text-xs font-semibold text-amber-700">Tu contra-oferta espera la respuesta de la PYME. No puedes enviar otra hasta que responda.</p>
                <CounterOfferCard offer={mine} />
              </div>
            )}
            {(canCounter || (canOperate && bidPending && canWithdraw)) && (
              <div className="flex flex-wrap gap-2">
                {canCounter && <Button size="sm" variant="outline" disabled={!!busy} onClick={() => setProposing(true)}><Handshake />{received ? 'Responder con otro monto' : 'Contraofertar'}</Button>}
                {canOperate && bidPending && canWithdraw && <Button size="sm" variant="destructive" loading={busy === 'withdraw'} disabled={!!busy && busy !== 'withdraw'} onClick={withdraw}><Undo2 />Retirar oferta</Button>}
              </div>
            )}
            {bid.status === 'WITHDRAWN' && <p className="text-xs text-ink-700">Retiraste esta oferta: la PYME ya no la ve y no puedes volver a ofertar por esta solicitud.</p>}
            {bidPending && !canWithdraw && !closed && <p className="text-xs text-ink-500">La subasta ya cerró: no puedes retirar la oferta, pero la PYME aún puede adjudicarla.</p>}
            {history.length > 0 && (
              <details className="text-sm">
                <summary className="cursor-pointer rounded-sm text-xs font-medium text-petrol-700 hover:underline">Historial de contra-ofertas ({history.length})</summary>
                <ul className="mt-2 space-y-2">{history.map((o) => <li key={o.id}><CounterOfferCard offer={o} /></li>)}</ul>
              </details>
            )}
          </div>
        )}
        {orgError ? (
          <div className="p-4"><Alert tone="error" title="No pudimos abrir la conversación">{orgError}</Alert></div>
        ) : orgId ? (
          <ChatPanel requestId={requestId} carrierOrgId={orgId} closed={closed || bid?.status === 'WITHDRAWN'} className="flex-1" emptyHint={bid ? undefined : 'Pregunta lo que necesites antes de ofertar: horarios, tipo de carga o accesos.'} />
        ) : (
          <div className="p-4 text-sm text-ink-500" role="status">Abriendo conversación…</div>
        )}
      </div>
      {bid && bidPending && (
        <CounterOfferDialog open={proposing} onOpenChange={setProposing} bidId={bid.id} perspective="CARRIER" currentAmount={bid.amount ?? 0} floor={floor} replacesTheirs={!!received} onDone={reload} />
      )}
    </Sheet>
  );
}
