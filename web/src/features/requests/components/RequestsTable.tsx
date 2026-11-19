import { useMemo, useState } from 'react';
import { Search, SearchX, Gavel, Eye, Plus, MoreHorizontal, Ban, RotateCcw, Repeat2, Copy } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Card } from '@/components/ui/card';
import { FilterBar } from '@/components/ui/filter-bar';
import { QuickFilters } from '@/components/ui/quick-filters';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { Button } from '@/components/ui/button';
import { ExportButton } from '@/components/ui/export-button';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { RequestView } from '@/lib/api/server';
import { CancelRequestDialog } from './CancelRequestButton';
import { RescheduleDialog } from './RecoveryDialogs';
import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';

export const PAGE_SIZE = 20;
export type RequestFilters = { status: string; origin: string; dest: string; from: string; to: string; q: string; sort: string };
export type RequestCounts = { all?: number; published?: number; matched?: number; cancelled?: number; withBids?: number };
const SORT_OPTIONS = [
  { value: 'recent', label: 'Más recientes' },
  { value: 'pickup', label: 'Recojo más próximo' },
  { value: 'bids', label: 'Más ofertas' },
  { value: 'price_desc', label: 'Mayor precio sugerido' },
  { value: 'price_asc', label: 'Menor precio sugerido' },
];
const bids = (r: RequestView) => (r.status === 'PUBLISHED' ? (r.auction?.bidCount ?? 0) : 0);

/** Parámetros de URL de la lista (también los usa la exportación CSV, con los nombres de la API). */
function toQuery(f: RequestFilters, page = 1) {
  const q = new URLSearchParams();
  if (f.status) q.set('estado', f.status);
  if (f.origin) q.set('origen', f.origin);
  if (f.dest) q.set('destino', f.dest);
  if (f.from) { q.set('desde', f.from); if (f.to) q.set('hasta', f.to); }
  if (f.q) q.set('q', f.q);
  if (f.sort && f.sort !== 'recent') q.set('orden', f.sort);
  if (page > 1) q.set('pagina', String(page));
  return q;
}

/**
 * «Mis solicitudes»: filtros, orden y paginación en el SERVIDOR (`GET /shipment-requests/mine`), con los filtros en la
 * URL (se pueden compartir y sobreviven al recargar). «Exportar CSV» baja exactamente lo que se está viendo.
 */
export default function RequestsTable({ rows, cities, filters, page, size, total, counts, anyRequests }: {
  rows: RequestView[]; cities: { code?: string; name?: string }[]; filters: RequestFilters; page: number; size: number; total: number; counts?: RequestCounts;
  /** false = la organización aún no tiene ninguna solicitud (estado vacío de bienvenida). */
  anyRequests: boolean;
}) {
  const canOperate = useCan('OPERATE');
  const [f, setF] = useState(filters);
  const [navigating, setNavigating] = useState(false);
  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const filtered = !!(f.status || f.origin || f.dest || f.from || f.q);

  function go(next: RequestFilters, p = 1) {
    setF(next);
    setNavigating(true);
    const qs = toQuery(next, p).toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<RequestFilters>) => go({ ...f, ...patch });
  const reset = () => go({ status: '', origin: '', dest: '', from: '', to: '', q: '', sort: f.sort });
  const exportQs = toQuery(f).toString();
  const exportHref = `/exportar/solicitudes${exportQs ? `?${exportQs}` : ''}`;
  const [qText, setQText] = useState(filters.q);

  const [cancelId, setCancelId] = useState<string | null>(null);
  const [recover, setRecover] = useState<{ mode: 'reopen' | 'republish'; r: RequestView } | null>(null);

  if (!anyRequests)
    return (
      <Card>
        <EmptyState title="Aún no tienes solicitudes" description="Publica tu primera carga y recibe ofertas de transportistas en minutos." action={canOperate ? <Button asChild><a href="/pyme/solicitudes/nueva"><Plus />Publicar solicitud</a></Button> : undefined} />
      </Card>
    );

  return (
    <div className="min-w-0 space-y-3" aria-busy={navigating || undefined}>
      <ReadOnlyNotice>No puedes publicar ni cancelar solicitudes.</ReadOnlyNotice>
      <QuickFilters
        label="Estado de la solicitud"
        value={f.status}
        onValueChange={(v) => set({ status: v })}
        options={[
          { value: '', label: 'Todas', count: counts?.all },
          { value: 'REVIEW', label: 'Con ofertas por elegir', count: counts?.withBids },
          { value: 'PUBLISHED', label: 'Publicadas', count: counts?.published },
          { value: 'MATCHED', label: 'Adjudicadas', count: counts?.matched },
          { value: 'CANCELLED', label: 'Canceladas', count: counts?.cancelled },
        ]}
      />
      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar
          count={total}
          noun={['solicitud', 'solicitudes']}
          active={filtered}
          onClear={reset}
          extra={<ExportButton href={exportHref} />}
          sort={<Select aria-label="Ordenar por" className="h-9 w-52" value={f.sort} onValueChange={(v) => set({ sort: v })} options={SORT_OPTIONS} />}
        >
          <Field label="Buscar" htmlFor="f-q">
            <form role="search" onSubmit={(e) => { e.preventDefault(); set({ q: qText.trim() }); }} className="relative">
              <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" />
              <Input id="f-q" className="pl-9" placeholder="Código, referencia, ruta o carga" value={qText} onChange={(e) => setQText(e.target.value)} onBlur={() => qText.trim() !== f.q && set({ q: qText.trim() })} maxLength={80} />
            </form>
          </Field>
          <Field label="Origen" htmlFor="f-o"><Combobox id="f-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Destino" htmlFor="f-d"><Combobox id="f-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Fecha de recojo" htmlFor="f-range">
            <DateRangePicker id="f-range" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" />
          </Field>
        </FilterBar>
        {rows.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No hay solicitudes con estos filtros"
            description="Prueba con otra ciudad, otro código o amplía el rango de fechas."
            action={<Button type="button" variant="outline" onClick={reset}>Limpiar filtros</Button>}
          />
        ) : (
          <>
            <Table label="Tus solicitudes" stack>
              <thead>
                <tr>
                  <Th>Ruta</Th><Th>Carga</Th><Th>Recojo</Th><Th>Estado</Th><Th>Subasta</Th>
                  <Th className="text-right">Sugerido</Th>
                  <Th><span className="sr-only">Acciones</span></Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const n = bids(r);
                  const open = r.status === 'PUBLISHED';
                  const route = `${CITY_LABELS[r.originCity!]} a ${CITY_LABELS[r.destinationCity!]}`;
                  return (
                    <Tr key={r.id}>
                      <Td primary>
                        <a href={`/pyme/solicitudes/${r.id}`} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</a>
                        <div className="font-mono text-xs text-ink-500"><span className="font-semibold text-ink-700">{r.code}</span> · {formatKm(r.distanceKm)}</div>
                        {r.customerReference && <div className="max-w-52 truncate font-mono text-xs text-ink-500" title={r.customerReference}>Ref. {r.customerReference}</div>}
                      </Td>
                      <Td label="Carga"><div><div>{CARGO_LABELS[r.cargoType!]}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></div></Td>
                      <Td label="Recojo" className="whitespace-nowrap">{formatDate(r.pickupDate)}</Td>
                      <Td label="Estado">
                        <div className="flex flex-col items-start gap-1">
                          <StatusPill status={r.status!} />
                          {open && r.auction?.status === 'CLOSED' && r.auction.closeReason === 'TIMEOUT' && <span className="text-xs font-medium text-amber-700">Subasta cerrada por tiempo</span>}
                          {r.status === 'CANCELLED' && r.cancelReason && <span className="max-w-44 truncate text-xs text-ink-500" title={r.cancelReason}>{r.cancelReason}</span>}
                          {r.republishedFrom && <span className="text-xs text-ink-500">Republicada</span>}
                        </div>
                      </Td>
                      <Td label="Subasta" className="whitespace-nowrap">
                        {open && r.auction ? (
                          <div className="space-y-0.5 text-right">
                            <div className={`text-xs ${n > 0 ? 'font-medium text-amber-700' : 'text-ink-700'}`}><span className="font-mono">{r.auction.bidCount ?? 0}/{r.auction.maxBids ?? 5}</span> ofertas</div>
                            <Countdown closesAt={r.auction.closesAt} />
                          </div>
                        ) : <span className="text-ink-500">—</span>}
                      </Td>
                      <Td label="Sugerido" className="text-right"><span className="money">{formatMoney(r.suggestedPrice)}</span></Td>
                      <Td actions className="text-right">
                        <Button asChild variant={n > 0 ? 'money' : 'outline'} size="sm"><a href={`/pyme/solicitudes/${r.id}`}>{n > 0 ? <><Gavel />Comparar ofertas</> : <><Eye />Ver detalle</>}<span className="sr-only"> de {route}</span></a></Button>
                        {canOperate && (
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="flex-none!" aria-label={`Más acciones de ${route}`}><MoreHorizontal /></Button></DropdownMenuTrigger>
                            <DropdownMenuContent>
                              <DropdownMenuItem onSelect={() => { window.location.href = `/pyme/solicitudes/${r.id}`; }}><Eye />Ver detalle</DropdownMenuItem>
                              <DropdownMenuItem onSelect={() => { window.location.href = `/pyme/solicitudes/nueva?from=${r.id}`; }}><Copy />Duplicar</DropdownMenuItem>
                              {open && r.auction?.status === 'CLOSED' && r.auction.closeReason === 'TIMEOUT' && <DropdownMenuItem onSelect={() => setRecover({ mode: 'reopen', r })}><RotateCcw />Reabrir subasta</DropdownMenuItem>}
                              {r.status === 'CANCELLED' && <DropdownMenuItem onSelect={() => setRecover({ mode: 'republish', r })}><Repeat2 />Republicar solicitud</DropdownMenuItem>}
                              {open && <DropdownMenuItem destructive onSelect={() => setCancelId(r.id!)}><Ban />Cancelar solicitud</DropdownMenuItem>}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        )}
                      </Td>
                    </Tr>
                  );
                })}
              </tbody>
            </Table>
            {total > size && <Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} className="border-t border-steel-200 px-4 py-3" />}
          </>
        )}
      </Card>
      {cancelId && <CancelRequestDialog id={cancelId} open onOpenChange={(o) => !o && setCancelId(null)} />}
      {recover && <RescheduleDialog mode={recover.mode} id={recover.r.id!} open onOpenChange={(o) => !o && setRecover(null)} currentPickup={recover.r.pickupDate} />}
    </div>
  );
}
