import { useCan } from '@/lib/use-can';
import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Gavel, Handshake, MessageSquare, Trophy, TrendingDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/avatar';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { TrustScore } from '@/components/domain/TrustScore';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { reportFailure } from '@/components/domain/VerificationBlock';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { VEHICLE_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { BidView } from '@/lib/api/server';
import { CounterOfferCard } from '@/features/negotiation/components/CounterOfferCard';
import { CounterOfferDialog } from '@/features/negotiation/components/CounterOfferDialog';
import { useCounterOffers } from '@/features/negotiation/components/NegotiationSheet';
import type { CounterOffer } from '@/features/negotiation/server';
import { useRequestState } from './RequestLive';
import { OPEN_THREAD_EVENT } from '@/features/negotiation/usePolling';

import { diffPct, formatDiff } from '../diff';
export { diffPct };

function BidRow({ b, isBest, suggested, canAccept, canNegotiate, floor, busyId, onAccept }: { b: BidView; isBest: boolean; suggested?: number | null; canAccept: boolean; canNegotiate: boolean; floor?: number | null; busyId: string | null; onAccept: (b: BidView) => void }) {
  const [dialog, setDialog] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const { offers, reload } = useCounterOffers(b.id, true);
  const d = diffPct(b.amount ?? 0, suggested);
  const [busy, setBusy] = useState<'accept' | 'reject' | null>(null);
  const pending = offers.find((o) => o.status === 'PENDING');
  const received = pending?.proposedByCarrier ? pending : undefined; // el transportista me propuso un monto (RF26)
  const mine = pending && !pending.proposedByCarrier ? pending : undefined; // yo propuse y espero su respuesta
  const history = offers.filter((o) => o !== pending);
  const seen = useRef<Map<string, string>>(new Map());

  // Si el transportista aceptó mi contra-oferta, el monto de la oferta cambió: recargamos.
  useEffect(() => {
    for (const o of offers) {
      const prev = seen.current.get(o.id!);
      if (prev === 'PENDING' && o.status === 'ACCEPTED' && !o.proposedByCarrier) {
        flash('El transportista aceptó tu contra-oferta', 'success');
        window.location.reload();
      }
      seen.current.set(o.id!, o.status!);
    }
  }, [offers]);

  async function acceptCounter(o: CounterOffer) {
    const ok = await confirm({
      title: `¿Aceptar la propuesta de ${formatMoney(o.amount)}?`,
      description: `La oferta de ${b.carrierName} pasará de ${formatMoney(o.previousAmount)} a ${formatMoney(o.amount)}. Aceptarla no adjudica el envío: después puedes aceptar la oferta.`,
      confirmLabel: 'Aceptar propuesta',
      cancelLabel: 'Volver',
    });
    if (!ok) return;
    setBusy('accept');
    const { error } = await actions.negotiation.accept({ id: o.id! });
    if (error) { reportFailure(splitActionError(error)); setBusy(null); return; }
    flash('Propuesta aceptada', 'success');
    window.location.reload();
  }
  async function rejectCounter(o: CounterOffer) {
    const ok = await confirm({
      title: `¿Rechazar la propuesta de ${formatMoney(o.amount)}?`,
      description: 'La oferta se mantiene en su monto actual. Puedes conversar o proponer otro monto.',
      confirmLabel: 'Rechazar propuesta',
      cancelLabel: 'Volver',
      tone: 'destructive',
    });
    if (!ok) return;
    setBusy('reject');
    const { error } = await actions.negotiation.reject({ id: o.id! });
    setBusy(null);
    if (error) { reportFailure(splitActionError(error)); return; }
    toast.success('Propuesta rechazada');
    reload();
  }

  const canOperate = useCan('OPERATE'); // Lector: ve las ofertas y conversa en modo lectura, pero no acepta ni contraoferta
  const canCounter = canOperate && canNegotiate && b.status === 'PENDING';
  const dead = b.status === 'REJECTED' || b.status === 'WITHDRAWN';
  return (
    <li className={cn('border-l-4 px-4 py-4 sm:px-5', isBest ? 'border-emerald-500 bg-emerald-50/60' : 'border-transparent', dead && 'opacity-70')}>
      <div className="grid gap-x-6 gap-y-3 sm:grid-cols-[minmax(0,1fr)_auto]">
        {/* Transportista */}
        <div className="flex min-w-0 items-start gap-3">
          <Avatar name={b.carrierName ?? '?'} />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1 font-medium">
              <a href={`/organizaciones/${b.carrierOrgId}`} className="max-w-full truncate rounded-sm hover:text-petrol-700 hover:underline">{b.carrierName}</a>
              <VerifiedBadge verified={b.carrierRucVerified} variant="icon" />
              <TrustScore score={b.carrierTrustScore} />
              {b.assignedByOperator && <Badge tone="petrol">Asignada por LOGYX</Badge>}
            </div>
            <div className="mt-0.5 text-xs text-ink-500">
              {b.vehicleType ? <>{VEHICLE_LABELS[b.vehicleType] ?? b.vehicleType}{b.vehiclePlate && <> · <span className="font-mono">{b.vehiclePlate}</span></>} · </> : null}{formatDateTime(b.createdAt)}
            </div>
            {b.notes && <p className="mt-1.5 rounded-input bg-steel-100 px-2.5 py-1.5 text-sm text-ink-700">{b.notes}</p>}
          </div>
        </div>
        {/* Monto */}
        <div className="flex items-baseline justify-between gap-3 sm:block sm:min-w-40 sm:text-right">
          <div className="money shrink-0 whitespace-nowrap text-xl">{formatMoney(b.amount)}</div>
          <div className="flex flex-wrap items-center justify-end gap-x-2 gap-y-1 sm:mt-0.5">
            {d != null && <span className={cn('font-mono text-xs tabular-nums', d <= 0 ? 'text-emerald-700' : 'text-amber-700')}>{formatDiff(d)} vs. sugerido</span>}
            {isBest && <Badge tone="emerald"><Trophy className="size-3" aria-hidden="true" />Mejor precio</Badge>}
          </div>
        </div>
        {/* Acciones */}
        <div className="flex flex-wrap items-center gap-2 sm:col-span-2 sm:pl-12">
          {b.carrierOrgId && canNegotiate && (
            <Button size="sm" variant="ghost" className="max-sm:flex-1" onClick={() => window.dispatchEvent(new CustomEvent(OPEN_THREAD_EVENT, { detail: { carrierOrgId: b.carrierOrgId } }))}><MessageSquare />Conversar<span className="sr-only"> con {b.carrierName}</span></Button>
          )}
          {canCounter && (
            <Button size="sm" variant="ghost" className="max-sm:flex-1" disabled={!!mine} onClick={() => setDialog(true)}><Handshake />{received ? 'Responder con otro monto' : 'Contraofertar'}<span className="sr-only"> a {b.carrierName}</span></Button>
          )}
          <div className="flex max-sm:order-first max-sm:w-full sm:ml-auto">
            {canAccept && canOperate && b.status === 'PENDING' ? (
              <Button size="sm" className="max-sm:w-full" variant={isBest ? 'money' : 'outline'} loading={busyId === b.id} disabled={busyId !== null && busyId !== b.id} onClick={() => onAccept(b)}>Aceptar oferta</Button>
            ) : (
              <StatusPill status={b.status!} kind="bid" />
            )}
          </div>
        </div>
      </div>
      {(pending || history.length > 0) && (
        <div className="mt-3 space-y-2 sm:pl-12">
          {mine && (
            <div>
              <p className="mb-1 text-xs font-semibold text-amber-700">Contra-oferta pendiente: esperando la respuesta del transportista. No puedes enviar otra hasta que responda.</p>
              <CounterOfferCard offer={mine} />
            </div>
          )}
          {received && (
            <div>
              <p className="mb-1 text-xs font-semibold text-amber-700">{b.carrierName} te propone un nuevo monto</p>
              <CounterOfferCard
                offer={received}
                actions={canNegotiate && canOperate ? <>
                  <Button size="sm" variant="money" loading={busy === 'accept'} disabled={busy === 'reject'} onClick={() => acceptCounter(received)}>Aceptar propuesta</Button>
                  <Button size="sm" variant="outline" loading={busy === 'reject'} disabled={busy === 'accept'} onClick={() => rejectCounter(received)}>Rechazar</Button>
                </> : undefined}
              />
            </div>
          )}
          {history.length > 0 && (
            <div>
              <button type="button" onClick={() => setShowAll((v) => !v)} aria-expanded={showAll} className="rounded-sm text-xs font-medium text-petrol-700 hover:underline">
                {showAll ? 'Ocultar historial de contra-ofertas' : `Ver historial de contra-ofertas (${history.length})`}
              </button>
              {showAll && <ul className="mt-2 space-y-2">{history.map((o) => <li key={o.id}><CounterOfferCard offer={o} /></li>)}</ul>}
            </div>
          )}
        </div>
      )}
      {canCounter && <CounterOfferDialog open={dialog} onOpenChange={setDialog} bidId={b.id!} carrierName={b.carrierName} currentAmount={b.amount ?? 0} floor={floor} replacesTheirs={!!received} onDone={reload} />}
    </li>
  );
}

function Stat({ label, value, hint, tone }: { label: string; value: React.ReactNode; hint?: React.ReactNode; tone?: 'money' }) {
  return (
    <div className="min-w-0 px-4 py-3 sm:px-5">
      <div className="text-xs font-medium text-ink-500">{label}</div>
      <div className={cn('mt-0.5 truncate font-mono text-base font-semibold tabular-nums', tone === 'money' ? 'text-emerald-700' : 'text-ink-900')}>{value}</div>
      {hint && <div className="truncate text-xs text-ink-500">{hint}</div>}
    </div>
  );
}

export default function BidComparison({ bids: initialBids, suggested, requestId, requestStatus, floor }: { bids: BidView[]; suggested?: number | null; requestId: string; requestStatus: string; floor?: number | null }) {
  // Se actualiza en vivo (RequestLiveSync) sin recargar la página.
  const live = useRequestState({ request: { id: requestId, status: requestStatus }, bids: initialBids });
  const bids = live.bids;
  const published = (live.request.status ?? requestStatus) === 'PUBLISHED';
  const canAccept = published;
  const canNegotiate = published;
  const [busyId, setBusyId] = useState<string | null>(null);

  // Las retiradas y rechazadas no compiten: van al final.
  const active = (b: BidView) => b.status !== 'REJECTED' && b.status !== 'WITHDRAWN';
  const sorted = [...bids].sort((a, b) => Number(active(b)) - Number(active(a)) || (a.amount ?? 0) - (b.amount ?? 0));
  const best = sorted.find(active);
  const saving = best && suggested ? suggested - (best.amount ?? 0) : null;

  async function accept(b: BidView) {
    const d = diffPct(b.amount ?? 0, suggested);
    const ok = await confirm({
      title: `¿Aceptar la oferta de ${formatMoney(b.amount)}?`,
      description: `${b.carrierName} realizará el envío por ${formatMoney(b.amount)}${d != null ? ` (${formatDiff(d)} frente al precio sugerido de ${formatMoney(suggested)})` : ''}. Se cerrará la subasta y se descartarán las demás ofertas. No se puede deshacer.`,
      confirmLabel: 'Aceptar oferta',
      cancelLabel: 'Volver',
    });
    if (!ok) return;
    setBusyId(b.id!);
    const { data, error } = await actions.requests.accept({ bidId: b.id! });
    if (error) {
      reportFailure(splitActionError(error));
      setBusyId(null);
      return;
    }
    flash('Oferta aceptada', 'success');
    window.location.href = `/pyme/envios?id=${data.shipmentId}`;
  }

  return (
    <Card className="overflow-hidden">
      <CardHeader>
        <CardTitle>Ofertas recibidas <span className="ml-1 font-mono text-ink-500">({bids.length})</span></CardTitle>
        {bids.length > 1 && <span className="text-xs text-ink-500">De menor a mayor monto</span>}
      </CardHeader>
      {bids.length > 0 && best && (
        <div className="grid grid-cols-2 divide-x divide-steel-200 border-b border-steel-200 bg-steel-100/50 sm:grid-cols-3">
          <Stat label="Mejor oferta" value={formatMoney(best.amount)} tone="money" hint={best.carrierName} />
          <Stat label="Precio sugerido" value={formatMoney(suggested)} hint={floor ? `Piso ${formatMoney(floor)}` : undefined} />
          <div className="max-sm:hidden"><Stat label="Ahorro vs. sugerido" value={saving != null && saving > 0 ? <span className="inline-flex items-center gap-1"><TrendingDown className="size-4" aria-hidden="true" />{formatMoney(saving)}</span> : '—'} tone={saving != null && saving > 0 ? 'money' : undefined} hint={saving != null && saving > 0 && suggested ? `${((saving / suggested) * 100).toFixed(1)}% menos` : 'Sin ahorro frente al sugerido'} /></div>
        </div>
      )}
      {bids.length === 0 ? (
        <EmptyState compact icon={Gavel} title="Aún no hay ofertas" description="Los transportistas compatibles ya pueden ver tu carga. Las ofertas aparecerán aquí apenas lleguen." />
      ) : (
        <ul className="divide-y divide-steel-200">
          {sorted.map((b) => (
            <BidRow key={b.id} b={b} isBest={best?.id === b.id} suggested={suggested} canAccept={canAccept} canNegotiate={canNegotiate ?? canAccept} floor={floor} busyId={busyId} onAccept={accept} />
          ))}
        </ul>
      )}
    </Card>
  );
}
