import { useState } from 'react';
import { actions } from 'astro:actions';
import { Trophy } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/avatar';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { VEHICLE_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import type { BidView } from '@/lib/api/server';

export function diffPct(amount: number, suggested?: number | null) {
  if (!suggested) return null;
  return ((amount - suggested) / suggested) * 100;
}

export default function BidComparison({ bids, suggested, canAccept }: { bids: BidView[]; suggested?: number | null; canAccept: boolean }) {
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
        <>
        <ul className="divide-y divide-steel-200">
          {sorted.map((b) => {
            const d = diffPct(b.amount ?? 0, suggested);
            const isBest = best?.id === b.id;
            return (
              <li key={b.id} className={`flex flex-wrap items-center gap-x-6 gap-y-3 px-5 py-4 ${isBest ? 'bg-emerald-50/60' : ''}`}>
                <div className="flex min-w-56 flex-1 items-start gap-3">
                  <Avatar name={b.carrierName ?? '?'} />
                  <div className="min-w-0">
                    <div className="flex items-center gap-1.5 font-medium">
                      <span className="truncate">{b.carrierName}</span>
                      <VerifiedBadge verified={b.carrierRucVerified} variant="icon" />
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
                <div className="flex min-w-28 justify-end">
                  {canAccept && b.status === 'PENDING' ? (
                    <Button size="sm" variant={isBest ? 'money' : 'outline'} loading={busyId === b.id} disabled={busyId !== null && busyId !== b.id} onClick={() => accept(b)}>Aceptar oferta</Button>
                  ) : (
                    <StatusPill status={b.status!} kind="bid" />
                  )}
                </div>
              </li>
            );
          })}
        </ul>
        </>
      )}
    </Card>
  );
}
