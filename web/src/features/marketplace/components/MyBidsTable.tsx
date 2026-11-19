import { useEffect, useMemo, useState } from 'react';
import { Gavel, History, SearchX, Truck } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { Sheet } from '@/components/ui/sheet';
import { ExportButton } from '@/components/ui/export-button';
import ActivityTrail from '@/features/activity/components/ActivityTrail';
import { formatRate, priceBreakdown } from '@/lib/pricing';
import { loadCommissionRate } from '../commission';
import NegotiateCell from '@/features/negotiation/components/NegotiateCell';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatDateTime, formatMoney, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { RequestView } from '@/lib/api/server';
import { cn } from '@/lib/utils';
import { BID_STATE_LABEL, CLOSE_REASON_LABEL, bidState, standingText, type BidState } from '../bid-state';

const PAGE_SIZE = 15;
type Filter = '' | BidState;

/** Diferencia porcentual de mi oferta respecto del precio sugerido (negativa = por debajo). */
function diffPct(amount: number, suggested?: number | null) {
  return suggested ? ((amount - suggested) / suggested) * 100 : null;
}

/**
 * Mis ofertas (transportista): filtros rápidos por estado, tabla que pasa a tarjetas en móvil y acceso a la negociación.
 * `rows` son solicitudes con `myBid` (de `GET /shipment-requests/my-bids`).
 */
export default function MyBidsTable({ rows, openRequestId }: { rows: RequestView[]; openRequestId?: string | null }) {
  const [filter, setFilter] = useState<Filter>('');
  const [page, setPage] = useState(() => {
    const i = openRequestId ? rows.findIndex((r) => r.id === openRequestId) : -1;
    return i < 0 ? 1 : Math.floor(i / PAGE_SIZE) + 1;
  });
  const count = (s: Filter) => rows.filter((r) => bidState(r) === s).length;
  const shown = useMemo(() => (filter ? rows.filter((r) => bidState(r) === filter) : rows), [rows, filter]);
  const slice = shown.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
  const [rate, setRate] = useState<number | null>(null);
  useEffect(() => { loadCommissionRate().then(setRate); }, []);
  // Autoría y rastro de la oferta (quién del equipo la envió y qué se hizo): hoja lateral con `ActivityTrail`.
  const [trail, setTrail] = useState<{ bidId: string; route: string } | null>(null);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-steel-200 px-4 py-3 sm:px-5">
        <QuickFilters
          label="Estado de la oferta"
          value={filter}
          onValueChange={(v) => { setFilter(v as Filter); setPage(1); }}
          options={[
            { value: '', label: 'Todas', count: rows.length },
            { value: 'PENDING', label: 'Pendientes', count: count('PENDING') },
            { value: 'ACCEPTED', label: 'Aceptadas', count: count('ACCEPTED') },
            { value: 'LOST', label: 'Perdidas', count: count('LOST') },
            ...(count('CANCELLED') > 0 ? [{ value: 'CANCELLED', label: 'Canceladas', count: count('CANCELLED') }] : []),
            ...(count('WITHDRAWN') > 0 ? [{ value: 'WITHDRAWN', label: 'Retiradas', count: count('WITHDRAWN') }] : []),
          ]}
        />
        {rows.length > 0 && <ExportButton href={`/exportar/mis-ofertas${filter ? `?estado=${filter}` : ''}`} />}
      </div>
      {rows.length === 0 ? (
        <EmptyState icon={Gavel} title="Todavía no has enviado ofertas" description="Revisa las cargas disponibles y oferta por las compatibles con tu flota." action={<Button asChild><a href="/transportista/marketplace">Ver cargas disponibles</a></Button>} />
      ) : shown.length === 0 ? (
        <EmptyState icon={SearchX} title="No hay ofertas con este estado" description="Prueba con otro filtro para ver el resto de tus ofertas." action={<Button variant="outline" onClick={() => setFilter('')}>Ver todas las ofertas</Button>} />
      ) : (
        <>
          <Table label="Mis ofertas" stack className="[&_td]:px-3 [&_th]:px-3 sm:[&_td:first-child]:pl-5 sm:[&_th:first-child]:pl-5 sm:[&_td:last-child]:pr-5">
            <thead><tr><Th>Ruta y carga</Th><Th className="text-right">Mi oferta</Th><Th>Vehículo</Th><Th>Estado</Th><Th><span className="sr-only">Negociar</span></Th></tr></thead>
            <tbody>
              {slice.map((r) => {
                const b = r.myBid!;
                const d = diffPct(b.amount ?? 0, r.suggestedPrice);
                const state = bidState(r);
                const pending = state === 'PENDING';
                const open = r.auction?.status === 'OPEN';
                const standing = state === 'WITHDRAWN' ? null : standingText(r.myBidStanding);
                const closedAt = r.auction?.closedAt;
                const reason = r.auction?.closeReason ? CLOSE_REASON_LABEL[r.auction.closeReason] ?? null : null;
                return (
                  <Tr key={r.id} className={cn(pending && 'bg-amber-50/40')}>
                    <Td primary className="min-w-56">
                      <div className="font-medium">{CITY_LABELS[r.originCity!]} <span className="text-ink-500" aria-hidden="true">→</span><span className="sr-only">a</span> {CITY_LABELS[r.destinationCity!]}</div>
                      <div className="text-xs text-ink-700">{CARGO_LABELS[r.cargoType!]} · <span className="font-mono">{formatTons(r.weightKg)}</span> · recojo {formatDate(r.pickupDate)}</div>
                      <div className="text-xs text-ink-500">Enviada {formatDateTime(b.createdAt)}</div>
                    </Td>
                    <Td label="Mi oferta" className="whitespace-nowrap text-right">
                      <div className="@max-[720px]:text-right">
                        <div className="money text-base">{formatMoney(b.amount)}</div>
                        {pending && rate != null && b.amount ? <div className="text-xs text-ink-500" data-testid="bid-net">Cobrarías <span className="font-mono tabular-nums text-ink-700">{formatMoney(priceBreakdown(b.amount, rate).carrierNet)}</span> neto <span className="sr-only">(después de la comisión del {formatRate(rate)} y su IGV)</span></div> : null}
                        {d != null && <div className={cn('font-mono text-xs tabular-nums', d <= 0 ? 'text-emerald-700' : 'text-amber-700')}>{`${d > 0 ? '+' : ''}${d.toFixed(1)}%`}<span className="font-sans text-ink-500"> vs. sugerido {formatMoney(r.suggestedPrice)}</span></div>}
                        {standing && (
                          <div className="mt-1 max-w-52 whitespace-normal text-xs" data-testid="bid-standing">
                            <span className={cn('font-semibold', standing.tone === 'good' ? 'text-emerald-700' : standing.tone === 'warn' ? 'text-amber-700' : 'text-ink-900')}>{standing.headline}</span>
                            {standing.band && <span className="block text-ink-700">{standing.band}</span>}
                          </div>
                        )}
                      </div>
                    </Td>
                    <Td label="Vehículo" className="whitespace-nowrap">
                      {b.vehiclePlate ? <div className="@max-[720px]:text-right"><div className="font-mono">{b.vehiclePlate}</div><div className="text-xs text-ink-500">{b.vehicleType ? VEHICLE_LABELS[b.vehicleType] : ''}</div></div> : <span className="text-ink-500">—</span>}
                    </Td>
                    <Td label="Estado" className="min-w-40">
                      <div className="flex flex-col items-start gap-1 @max-[720px]:items-end">
                        {state === 'LOST' ? <StatusPill status="LOST" /> : state === 'CANCELLED' ? <StatusPill status="BID_CANCELLED" /> : <StatusPill status={b.status!} kind="bid" />}
                        {state === 'CANCELLED' && <span className="max-w-48 text-xs text-ink-700"><span className="font-medium">La PYME canceló la solicitud</span>{r.cancelReason ? <span className="text-ink-500">: {r.cancelReason}</span> : null}</span>}
                        {state === 'LOST' && <span className="max-w-48 text-xs text-ink-700">{r.status === 'MATCHED' ? 'La PYME eligió otra oferta.' : 'La PYME no eligió tu oferta.'}</span>}
                        {state === 'PENDING' && (open ? <span className="text-xs text-ink-500"><span className="font-mono">{r.auction!.bidCount}/{r.auction!.maxBids}</span> ofertas · <Countdown closesAt={r.auction!.closesAt} className="text-xs" /></span> : <span className="text-xs text-ink-500">Subasta cerrada: la PYME aún puede elegirte</span>)}
                        {!open && r.auction && (reason || closedAt) && <span className="max-w-48 text-xs text-ink-500" data-testid="bid-closure">Subasta cerrada{closedAt ? ` el ${formatDateTime(closedAt)}` : ''}{reason ? ` · ${reason}` : ''}</span>}
                      </div>
                    </Td>
                    <Td actions className="whitespace-nowrap text-right">
                      <div className="flex flex-col items-end gap-1 @max-[720px]:w-full @max-[720px]:items-stretch @max-[720px]:[&>div]:items-stretch">
                        <NegotiateCell requestId={r.id!} route={`${CITY_LABELS[r.originCity!]} a ${CITY_LABELS[r.destinationCity!]}`} bid={{ id: b.id!, amount: b.amount, status: b.status }} closed={r.status === 'CANCELLED' || (r.status === 'MATCHED' && b.status !== 'ACCEPTED')} floor={r.priceFloor} canWithdraw={open && r.status === 'PUBLISHED'} autoOpen={openRequestId === r.id} />
                        <Button size="sm" variant="ghost" onClick={() => setTrail({ bidId: b.id!, route: `${CITY_LABELS[r.originCity!]} a ${CITY_LABELS[r.destinationCity!]}` })}><History />Historial<span className="sr-only"> de la oferta de {CITY_LABELS[r.originCity!]} a {CITY_LABELS[r.destinationCity!]}</span></Button>
                        {b.status === 'ACCEPTED' && <Button asChild size="sm" variant="ghost"><a href="/transportista/envios"><Truck />Ver envío<span className="sr-only"> de {CITY_LABELS[r.originCity!]} a {CITY_LABELS[r.destinationCity!]}</span></a></Button>}
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
          {shown.length > PAGE_SIZE && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={PAGE_SIZE} total={shown.length} onPageChange={setPage} /></div>}
        </>
      )}
      <Sheet open={!!trail} onOpenChange={(o) => !o && setTrail(null)} title="Historial de la oferta" description={trail?.route}>
        {trail && <div className="p-5"><ActivityTrail key={trail.bidId} subject="BID" id={trail.bidId} title="Actividad de la oferta" liveTypes={['bid.accepted', 'bid.rejected', 'bid.placed', 'counter_offer.updated']} /></div>}
      </Sheet>
    </Card>
  );
}
