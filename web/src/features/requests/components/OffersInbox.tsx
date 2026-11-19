import { useState } from 'react';
import { actions } from 'astro:actions';
import { Gavel, Inbox, Star } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash } from '@/components/ui/toaster';
import { Countdown } from '@/components/domain/Countdown';
import { EmptyState } from '@/components/domain/EmptyState';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { TrustScore } from '@/components/domain/TrustScore';
import { reportFailure } from '@/components/domain/VerificationBlock';
import { Can } from '@/lib/permissions/react';
import { useLiveRefresh } from '@/lib/realtime';
import { CARGO_LABELS, CITY_LABELS, VEHICLE_LABELS, formatDate, formatMoney, formatTons } from '@/lib/format';
import { splitActionError } from '@/lib/action-error';
import type { Schemas } from '@/lib/api/server';

type Inbox = Schemas['OffersInboxView'];
type Row = Schemas['OfferInboxRow'];
type Bid = Schemas['OfferBidView'];

const EVENTS = ['bid.placed', 'bid.withdrawn', 'auction.updated', 'request.matched', 'counter_offer.updated'];

function BidCell({ b, showAmount = true }: { b: Bid; showAmount?: boolean }) {
  return (
    <div className="min-w-0">
      {showAmount && <div className="money text-base">{formatMoney(b.amount)}</div>}
      <div className="flex flex-wrap items-center gap-1 text-sm">
        <span className="max-w-44 truncate">{b.carrierName}</span>
        {b.preferred && <Badge tone="emerald"><Star className="size-3" aria-hidden="true" />Preferido</Badge>}
      </div>
      <div className="flex flex-wrap items-center gap-1.5 text-xs text-ink-500">
        <TrustScore score={b.carrierTrustScore} />
        {b.vehicleType && <span>{VEHICLE_LABELS[b.vehicleType] ?? b.vehicleType}{b.vehiclePlate && <> · <span className="font-mono">{b.vehiclePlate}</span></>}</span>}
      </div>
    </div>
  );
}

/**
 * Bandeja de ofertas (roadmap 2.10): una fila por solicitud publicada con ofertas por elegir. «Aceptar la mejor» confirma
 * mostrando al transportista, el vehículo y el monto. Se actualiza con los eventos de ofertas y subastas.
 */
export default function OffersInbox({ initial }: { initial: Inbox }) {
  const [inbox, setInbox] = useState(initial);
  const [busy, setBusy] = useState<string | null>(null);
  const rows = inbox.rows ?? [];

  async function reload() {
    const { data } = await actions.requests.inbox();
    if (data) setInbox(data);
  }
  useLiveRefresh({ types: EVENTS, refresh: reload, fallbackMs: 60_000, debounceMs: 400 });

  async function acceptBest(r: Row) {
    const b = r.best!;
    const trust = r.bestTrust!;
    const others = (r.bidCount ?? 1) - 1;
    const ok = await confirm({
      title: `¿Aceptar la oferta de ${formatMoney(b.amount)}?`,
      description: `${b.carrierName} lleva ${CITY_LABELS[r.originCity!]} → ${CITY_LABELS[r.destinationCity!]} (${r.code})${b.vehiclePlate ? ` en ${VEHICLE_LABELS[b.vehicleType ?? ''] ?? 'su vehículo'} ${b.vehiclePlate}` : ''}. `
        + (trust.bidId !== b.bidId ? `Hay una oferta con mejor Trust Score (${trust.carrierName}, ${formatMoney(trust.amount)}). ` : '')
        + `Se cerrará la subasta${others > 0 ? ` y se descartarán las otras ${others} ${others === 1 ? 'oferta' : 'ofertas'}` : ''}. No se puede deshacer.`,
      confirmLabel: 'Aceptar oferta',
    });
    if (!ok) return;
    setBusy(r.requestId!);
    const { data, error } = await actions.requests.accept({ bidId: b.bidId! });
    if (error) { reportFailure(splitActionError(error)); setBusy(null); await reload(); return; }
    flash('Oferta aceptada', 'success');
    window.location.href = `/pyme/envios?id=${data.shipmentId}`;
  }

  if (rows.length === 0)
    return (
      <Card>
        <EmptyState icon={Inbox} title="No tienes ofertas por revisar" description="Cuando un transportista oferte por una de tus solicitudes publicadas, la verás aquí con la mejor oferta destacada."
          action={<Button asChild variant="outline"><a href="/pyme/solicitudes">Ver mis solicitudes</a></Button>} />
      </Card>
    );

  return (
    <div className="min-w-0 space-y-3">
      <ReadOnlyNotice>No puedes aceptar ofertas.</ReadOnlyNotice>
      <p className="text-sm text-ink-700" aria-live="polite"><span className="font-mono font-semibold">{inbox.bids}</span> {inbox.bids === 1 ? 'oferta' : 'ofertas'} en <span className="font-mono font-semibold">{inbox.requests}</span> {inbox.requests === 1 ? 'solicitud' : 'solicitudes'}</p>
      <Card className="min-w-0 overflow-hidden">
        <Table label="Ofertas por solicitud" stack>
          <thead>
            <tr><Th>Solicitud</Th><Th>Ofertas</Th><Th>Mejor monto</Th><Th>Mejor Trust Score</Th><Th>Cierra en</Th><Th><span className="sr-only">Acciones</span></Th></tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const route = `${CITY_LABELS[r.originCity!]} a ${CITY_LABELS[r.destinationCity!]}`;
              const sameBest = r.best?.bidId === r.bestTrust?.bidId;
              return (
                <Tr key={r.requestId}>
                  <Td primary>
                    <a href={`/pyme/solicitudes/${r.requestId}`} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</a>
                    <div className="font-mono text-xs text-ink-500"><span className="font-semibold text-ink-700">{r.code}</span> · {CARGO_LABELS[r.cargoType!]} · {formatTons(r.weightKg)}</div>
                    <div className="text-xs text-ink-500">Recojo {formatDate(r.pickupDate)}</div>
                  </Td>
                  <Td label="Ofertas"><span className="font-mono font-semibold tabular-nums">{r.bidCount}</span></Td>
                  <Td label="Mejor monto"><BidCell b={r.best!} /></Td>
                  <Td label="Mejor Trust Score">{sameBest ? <span className="text-sm text-ink-700">Es la misma oferta</span> : <BidCell b={r.bestTrust!} />}</Td>
                  <Td label="Cierra en" className="whitespace-nowrap"><Countdown closesAt={r.closesAt} /></Td>
                  <Td actions className="text-right">
                    <Button asChild variant="outline" size="sm"><a href={`/pyme/solicitudes/${r.requestId}`}><Gavel />Comparar<span className="sr-only"> ofertas de {route}</span></a></Button>
                    <Can fallback={null}><Button size="sm" variant="money" loading={busy === r.requestId} disabled={busy !== null && busy !== r.requestId} onClick={() => void acceptBest(r)}>Aceptar la mejor<span className="sr-only"> de {route}</span></Button></Can>
                  </Td>
                </Tr>
              );
            })}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
