import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Handshake, MessageSquare, Trophy } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/avatar';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { TrustScore } from '@/components/domain/TrustScore';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { VEHICLE_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import type { BidView } from '@/lib/api/server';
import { CounterOfferCard } from '@/features/negotiation/components/CounterOfferCard';
import { CounterOfferDialog } from '@/features/negotiation/components/CounterOfferDialog';
import { useCounterOffers } from '@/features/negotiation/components/NegotiationSheet';
import { OPEN_THREAD_EVENT } from '@/features/negotiation/usePolling';

export function diffPct(amount: number, suggested?: number | null) {
  if (!suggested) return null;
  return ((amount - suggested) / suggested) * 100;
}

function BidRow({ b, isBest, suggested, canAccept, canNegotiate, floor, busyId, onAccept }: { b: BidView; isBest: boolean; suggested?: number | null; canAccept: boolean; canNegotiate: boolean; floor?: number | null; busyId: string | null; onAccept: (b: BidView) => void }) {
  const [dialog, setDialog] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const { offers, reload } = useCounterOffers(b.id, true);
  const d = diffPct(b.amount ?? 0, suggested);
  const pending = offers.find((o) => o.status === 'PENDING');
  const history = offers.filter((o) => o !== pending);
  const seen = useRef<Map<string, string>>(new Map());

  // Si el transportista aceptó una contra-oferta, el monto de la oferta cambió: recargamos.
  useEffect(() => {
    for (const o of offers) {
      const prev = seen.current.get(o.id!);
      if (prev === 'PENDING' && o.status === 'ACCEPTED') {
        flash('El transportista aceptó tu contra-oferta', 'success');
        window.location.reload();
      }
      seen.current.set(o.id!, o.status!);
    }
  }, [offers]);

  const canCounter = canNegotiate && b.status === 'PENDING';
  return (
    <li className={`flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-4 ${isBest ? 'bg-emerald-50/60' : ''}`}>
      <div className="flex min-w-56 flex-1 items-start gap-3">
        <Avatar name={b.carrierName ?? '?'} />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1.5 font-medium">
            <a href={`/organizaciones/${b.carrierOrgId}`} className="truncate rounded-sm hover:text-petrol-700 hover:underline">{b.carrierName}</a>
            <VerifiedBadge verified={b.carrierRucVerified} variant="icon" />
            <TrustScore score={b.carrierTrustScore} />
            {b.assignedByOperator && <Badge tone="petrol">Asignada por LOGYX</Badge>}
          </div>
          <div className="text-xs text-ink-500">
            {b.vehicleType ? <>{VEHICLE_LABELS[b.vehicleType] ?? b.vehicleType}{b.vehiclePlate && <> · <span className="font-mono">{b.vehiclePlate}</span></>} · </> : null}{formatDateTime(b.createdAt)}
          </div>
          {b.notes && <p className="mt-1 text-sm text-ink-700">{b.notes}</p>}
        </div>
      </div>
      <div className="text-right">
        <div className="money text-lg">{formatMoney(b.amount)}</div>
        {d != null && <div className={`font-mono text-xs tabular-nums ${d <= 0 ? 'text-emerald-700' : 'text-amber-700'}`}>{d > 0 ? '+' : ''}{d.toFixed(1)}% vs. sugerido</div>}
        {isBest && <div className="mt-1 flex justify-end"><Badge tone="emerald"><Trophy className="size-3" aria-hidden="true" />Mejor precio</Badge></div>}
      </div>
      <div className="flex min-w-28 flex-wrap items-center justify-end gap-2">
        {b.carrierOrgId && canNegotiate && (
          <Button size="sm" variant="ghost" onClick={() => window.dispatchEvent(new CustomEvent(OPEN_THREAD_EVENT, { detail: { carrierOrgId: b.carrierOrgId } }))}><MessageSquare />Conversar<span className="sr-only"> con {b.carrierName}</span></Button>
        )}
        {canCounter && (
          <Button size="sm" variant="outline" disabled={!!pending} onClick={() => setDialog(true)}><Handshake />Contraofertar<span className="sr-only"> a {b.carrierName}</span></Button>
        )}
        {canAccept && b.status === 'PENDING' ? (
          <Button size="sm" variant={isBest ? 'money' : 'outline'} loading={busyId === b.id} disabled={busyId !== null && busyId !== b.id} onClick={() => onAccept(b)}>Aceptar oferta</Button>
        ) : (
          <StatusPill status={b.status!} kind="bid" />
        )}
      </div>
      {(pending || history.length > 0) && (
        <div className="basis-full space-y-2 pl-0 sm:pl-11">
          {pending && (
            <div>
              <p className="mb-1 text-xs font-semibold text-amber-700">Contra-oferta pendiente: esperando la respuesta del transportista. No puedes enviar otra hasta que responda.</p>
              <CounterOfferCard offer={pending} />
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
      {canCounter && <CounterOfferDialog open={dialog} onOpenChange={setDialog} bidId={b.id!} carrierName={b.carrierName} currentAmount={b.amount ?? 0} floor={floor} onDone={reload} />}
    </li>
  );
}

export default function BidComparison({ bids, suggested, canAccept, floor, canNegotiate }: { bids: BidView[]; suggested?: number | null; canAccept: boolean; floor?: number | null; /** Solicitud publicada: permite conversar y contraofertar. */ canNegotiate?: boolean }) {
  const [busyId, setBusyId] = useState<string | null>(null);

  const sorted = [...bids].sort((a, b) => (a.amount ?? 0) - (b.amount ?? 0));
  const best = sorted.find((b) => b.status !== 'REJECTED');

  async function accept(b: BidView) {
    const ok = await confirm({
      title: `¿Aceptar la oferta de ${formatMoney(b.amount)}?`,
      description: `${b.carrierName} realizará el envío por ${formatMoney(b.amount)}. Se cerrará la subasta y se descartarán las demás ofertas.`,
      confirmLabel: 'Aceptar oferta',
      cancelLabel: 'Volver',
    });
    if (!ok) return;
    setBusyId(b.id!);
    const { data, error } = await actions.requests.accept({ bidId: b.id! });
    if (error) {
      toast.error(error.message);
      setBusyId(null);
      return;
    }
    flash('Oferta aceptada', 'success');
    window.location.href = `/pyme/envios?id=${data.shipmentId}`;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ofertas recibidas <span className="ml-1 font-mono text-ink-500">({bids.length})</span></CardTitle>
        {bids.length > 1 && <span className="text-xs text-ink-500">Ordenadas de menor a mayor monto</span>}
      </CardHeader>
      {bids.length === 0 ? (
        <EmptyState title="Aún no hay ofertas" description="Los transportistas compatibles ya pueden ver tu carga. Las ofertas aparecerán aquí apenas lleguen." />
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
