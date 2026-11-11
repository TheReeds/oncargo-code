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
import { notifyNegotiationChanged, usePolling } from '../usePolling';
import { ChatPanel } from './ChatPanel';
import { CounterOfferCard } from './CounterOfferCard';

export interface MyBid { id: string; amount?: number | null; status?: string | null }

/** Carga las contra-ofertas de una oferta y las refresca con el polling de la negociación. */
export function useCounterOffers(bidId: string | undefined, enabled = true, pollMs = 8000) {
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
  usePolling(() => { if (enabled) load(); }, pollMs, enabled && !!bidId);
  return { offers, loaded, error, reload: load };
}

/** Transportista: chat de SU hilo con la PYME y la contra-oferta pendiente (aceptar / rechazar). */
export default function NegotiationSheet({ open, onOpenChange, requestId, title = 'Conversación con la PYME', description, bid, closed, carrierOrgId: orgProp }: { open: boolean; onOpenChange: (o: boolean) => void; requestId: string; title?: string; description?: string; bid?: MyBid | null; closed?: boolean; carrierOrgId?: string }) {
  const [orgId, setOrgId] = useState<string | null>(orgProp ?? null);
  const [orgError, setOrgError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'accept' | 'reject' | null>(null);
  const { offers, reload } = useCounterOffers(bid?.id, open && !!bid && bid.status === 'PENDING');

  useEffect(() => {
    if (!open || orgId) return;
    actions.negotiation.myOrg({}).then(({ data, error }) => {
      if (error) setOrgError(splitActionError(error).message);
      else setOrgId(data.orgId);
    });
  }, [open, orgId]);

  const pending = offers.find((o) => o.status === 'PENDING');

  async function accept(o: CounterOffer) {
    const ok = await confirm({
      title: `¿Aceptar la contra-oferta de ${formatMoney(o.amount)}?`,
      description: `Tu oferta pasará de ${formatMoney(o.previousAmount)} a ${formatMoney(o.amount)}. La PYME podrá aceptarla cuando quiera.`,
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

  return (
    <Sheet open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <div className="flex h-full min-h-0 flex-col">
        {bid && (
          <div className="space-y-3 border-b border-steel-200 bg-steel-100 p-4">
            <div className="flex items-center justify-between gap-2 text-sm">
              <span className="text-ink-700">Tu oferta</span>
              <span className="flex items-center gap-2"><span className="money text-base">{formatMoney(bid.amount)}</span>{bid.status && <StatusPill status={bid.status} kind="bid" />}</span>
            </div>
            {pending && (
              <div>
                <p className="mb-1.5 text-xs font-semibold text-ink-700">La PYME te propuso un nuevo monto</p>
                <CounterOfferCard
                  offer={pending}
                  actions={<>
                    <Button size="sm" variant="money" loading={busy === 'accept'} disabled={busy === 'reject'} onClick={() => accept(pending)}>Aceptar contra-oferta</Button>
                    <Button size="sm" variant="outline" loading={busy === 'reject'} disabled={busy === 'accept'} onClick={() => reject(pending)}>Rechazar</Button>
                  </>}
                />
              </div>
            )}
          </div>
        )}
        {orgError ? (
          <div className="p-4"><Alert tone="error" title="No pudimos abrir la conversación">{orgError}</Alert></div>
        ) : orgId ? (
          <ChatPanel requestId={requestId} carrierOrgId={orgId} closed={closed} className="flex-1" emptyHint={bid ? undefined : 'Pregunta lo que necesites antes de ofertar: horarios, tipo de carga o accesos.'} />
        ) : (
          <div className="p-4 text-sm text-ink-500" role="status">Abriendo conversación…</div>
        )}
      </div>
    </Sheet>
  );
}
