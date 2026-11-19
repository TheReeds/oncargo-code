import { useState } from 'react';
import { Banknote, SearchX } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Pagination } from '@/components/ui/pagination';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { cn } from '@/lib/utils';
import { CITY_LABELS, formatDate, formatMoney } from '@/lib/format';
import { daysBetween, limaDateOf, limaToday } from '@/lib/lima-date';
import type { SettlementRow } from '../server';
import type { ListFilters } from '../filters';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';

const route = (o?: string, d?: string) => `${CITY_LABELS[o ?? ''] ?? o} → ${CITY_LABELS[d ?? ''] ?? d}`;
const detailHref = (p: Perspective, id: string) => p === 'OPERATOR' ? `/operador/envios/${id}` : p === 'CARRIER' ? `/transportista/envios?id=${id}` : `/pyme/envios?id=${id}`;
const COPY: Record<Perspective, { noun: string; empty: string; emptyHint: string }> = {
  PYME: { noun: 'por pagar', empty: 'No tienes pagos pendientes', emptyHint: 'Cuando se entregue un envío, su cobro aparece aquí con la fecha de vencimiento.' },
  CARRIER: { noun: 'por cobrar', empty: 'Aún no tienes cobros', emptyHint: 'Cuando entregues un envío, aquí registras tu comprobante y confirmas el pago.' },
  OPERATOR: { noun: 'cobros', empty: 'Aún no hay cobros', emptyHint: 'Cuando se entregue un envío, su cobro aparecerá aquí.' },
};

/** Listado de cobros con filtros en la URL (`estado`, `desde`, `hasta`, `pagina`) y paginación en el servidor. */
export default function SettlementsList({ perspective, rows, filters, page, size, total, base, counts }: {
  perspective: Perspective; rows: SettlementRow[]; filters: ListFilters; page: number; size: number; total: number; base: string;
  counts?: { overdue?: number; disputed?: number; commissionPending?: number };
}) {
  const [f, setF] = useState(filters);
  const [navigating, setNavigating] = useState(false);
  const t = COPY[perspective];
  const withCommission = perspective !== 'PYME';
  const today = limaToday();

  function go(next: ListFilters, p = 1) {
    setF(next); setNavigating(true);
    const q = new URLSearchParams();
    if (next.estado) q.set('estado', next.estado);
    if (next.desde) { q.set('desde', next.desde); if (next.hasta) q.set('hasta', next.hasta); }
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = base + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<ListFilters>) => go({ ...f, ...patch });
  const hasFilters = !!(f.estado || f.desde);
  const options = [
    { value: '', label: 'Todos' },
    { value: 'VENCIDOS', label: 'Vencidos', count: counts?.overdue },
    { value: 'PENDING_INVOICE', label: 'Falta comprobante' },
    { value: 'INVOICED', label: 'Facturado' },
    { value: 'PAID', label: 'Pagado' },
    { value: 'CONFIRMED', label: 'Cobrado' },
    { value: 'DISPUTED', label: 'En disputa', count: counts?.disputed },
    ...(withCommission ? [{ value: 'COMISION', label: 'Comisión por cobrar', count: counts?.commissionPending }] : []),
  ];

  return (
    <div className="min-w-0 space-y-3" aria-busy={navigating || undefined}>
      <QuickFilters label="Estado del cobro" value={f.estado} onValueChange={(v) => set({ estado: v })} options={options} />
      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar count={total} noun={['cobro', 'cobros']} active={hasFilters} onClear={() => go({ estado: '', desde: '', hasta: '', pagina: 1 })}>
          <Field label="Fecha de entrega" htmlFor="st-range" className="sm:col-span-2"><DateRangePicker id="st-range" value={{ from: f.desde, to: f.hasta }} onValueChange={(r) => set({ desde: r.from, hasta: r.to })} placeholder="Cualquier fecha" /></Field>
        </FilterBar>
        {rows.length === 0 ? (
          hasFilters
            ? <EmptyState icon={SearchX} title="No hay cobros con estos filtros" description="Prueba con otro estado o amplía las fechas." action={<Button variant="outline" onClick={() => go({ estado: '', desde: '', hasta: '', pagina: 1 })}><SearchX /> Limpiar filtros</Button>} />
            : <EmptyState icon={Banknote} title={t.empty} description={t.emptyHint} />
        ) : (
          <Table label="Cobros" stack dense>
            <thead>
              <tr>
                <Th>Envío</Th>
                <Th>{perspective === 'PYME' ? 'Transportista' : perspective === 'CARRIER' ? 'PYME' : 'PYME y transportista'}</Th>
                <Th>Entrega</Th><Th>Vence</Th><Th>Estado</Th><Th className="text-right">Precio final</Th>
                {withCommission && <Th className="text-right">Comisión</Th>}
                <Th className="relative"><span className="sr-only">Acciones</span></Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const delivered = r.deliveredAt ? limaDateOf(r.deliveredAt) : null;
                const age = delivered ? Math.max(0, daysBetween(delivered, today)) : null;
                const href = detailHref(perspective, r.shipmentId!);
                return (
                  <Tr key={r.shipmentId} data-testid="settlement-row">
                    <Td primary><a href={href} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{route(r.originCity, r.destinationCity)}</a><div className="font-mono text-xs text-ink-500">{r.code}</div></Td>
                    <Td label={perspective === 'PYME' ? 'Transportista' : perspective === 'CARRIER' ? 'PYME' : 'Partes'}>
                      {perspective === 'PYME' ? r.carrierName : perspective === 'CARRIER' ? r.requesterName : <><span className="block max-w-36 truncate">{r.requesterName}</span><span className="block max-w-36 truncate text-xs text-ink-500">{r.carrierName}</span></>}
                    </Td>
                    <Td label="Entrega" className="whitespace-nowrap">{delivered ? formatDate(delivered) : '—'}{age != null && <div className="text-xs text-ink-500">{age === 0 ? 'hoy' : `hace ${age} ${age === 1 ? 'día' : 'días'}`}</div>}</Td>
                    <Td label="Vence" className="whitespace-nowrap">{r.dueOn ? formatDate(r.dueOn) : '—'}{(r.daysOverdue ?? 0) > 0 && <div className="text-xs font-medium text-red-700">{r.daysOverdue} {r.daysOverdue === 1 ? 'día' : 'días'} de atraso</div>}</Td>
                    <Td label="Estado"><div className="flex flex-col items-start gap-1"><StatusPill status={r.status!} kind="settlement" />{!!r.pendingAdjustments && <span className="text-xs text-amber-700">{r.pendingAdjustments} {r.pendingAdjustments === 1 ? 'ajuste' : 'ajustes'} por responder</span>}</div></Td>
                    <Td label="Precio final" className="whitespace-nowrap text-right"><span className="money">{formatMoney(r.finalPrice)}</span></Td>
                    {withCommission && <Td label="Comisión" className="text-right"><span className="font-mono tabular-nums">{formatMoney((r.commission ?? 0) + (r.commissionVat ?? 0))}</span>{r.commissionStatus && <div className="mt-0.5 flex justify-end"><StatusPill status={r.commissionStatus} kind="commission" /></div>}</Td>}
                    <Td actions className="relative text-right"><Button asChild variant="outline" size="sm"><a href={href}>Ver envío<span className="sr-only"> {route(r.originCity, r.destinationCity)}</span></a></Button></Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
        {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>
    </div>
  );
}
