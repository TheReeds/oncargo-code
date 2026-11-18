import { useMemo, useState } from 'react';
import { Search, SearchX, Gavel, Eye, Plus, MoreHorizontal, Ban, RotateCcw, Repeat2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker, type DateRangeValue } from '@/components/ui/date-picker';
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
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { RequestView } from '@/lib/api/server';
import { CancelRequestDialog } from './CancelRequestButton';
import { RescheduleDialog } from './RecoveryDialogs';
import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';

const PAGE_SIZE = 10;
type SortKey = 'createdAt' | 'pickupDate' | 'bids' | 'price';
type Sort = { key: SortKey; dir: 'asc' | 'desc' };
const SORT_OPTIONS = [
  { value: 'createdAt:desc', label: 'Más recientes' },
  { value: 'pickupDate:asc', label: 'Recojo más próximo' },
  { value: 'bids:desc', label: 'Más ofertas' },
  { value: 'price:desc', label: 'Mayor precio sugerido' },
  { value: 'price:asc', label: 'Menor precio sugerido' },
];
const bids = (r: RequestView) => (r.status === 'PUBLISHED' ? (r.auction?.bidCount ?? 0) : 0);
const VALUE: Record<SortKey, (r: RequestView) => number | string> = {
  createdAt: (r) => r.createdAt ?? '',
  pickupDate: (r) => r.pickupDate ?? '',
  bids,
  price: (r) => r.suggestedPrice ?? 0,
};

export default function RequestsTable({ requests }: { requests: RequestView[] }) {
  const canOperate = useCan('OPERATE');
  const [status, setStatus] = useState('');
  const [city, setCity] = useState('');
  const [q, setQ] = useState('');
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' });
  const [sort, setSort] = useState<Sort>({ key: 'createdAt', dir: 'desc' });
  const [page, setPage] = useState(1);

  const cityOptions = useMemo(() => {
    const codes = new Set<string>();
    requests.forEach((r) => { if (r.originCity) codes.add(r.originCity); if (r.destinationCity) codes.add(r.destinationCity); });
    return [...codes].map((c) => ({ value: c, label: CITY_LABELS[c] ?? c })).sort((a, b) => a.label.localeCompare(b.label, 'es'));
  }, [requests]);

  const counts = useMemo(() => {
    const c = { '': requests.length, PUBLISHED: 0, MATCHED: 0, CANCELLED: 0 } as Record<string, number>;
    requests.forEach((r) => { if (r.status && r.status in c) c[r.status]++; });
    return c;
  }, [requests]);
  const toReview = useMemo(() => requests.filter((r) => bids(r) > 0).length, [requests]);

  const rows = useMemo(() => {
    const term = q.trim().toLowerCase();
    const get = VALUE[sort.key];
    const sign = sort.dir === 'asc' ? 1 : -1;
    return requests
      .filter((r) => !status || (status === 'REVIEW' ? bids(r) > 0 : r.status === status))
      .filter((r) => !city || r.originCity === city || r.destinationCity === city)
      .filter((r) => !range.from || ((r.pickupDate ?? '') >= range.from && (r.pickupDate ?? '') <= (range.to || range.from)))
      .filter((r) => !term || [CITY_LABELS[r.originCity!], CITY_LABELS[r.destinationCity!], r.cargoDescription].join(' ').toLowerCase().includes(term))
      .sort((a, b) => {
        const x = get(a), y = get(b);
        return (x < y ? -1 : x > y ? 1 : 0) * sign || (b.createdAt ?? '').localeCompare(a.createdAt ?? '');
      });
  }, [requests, status, city, q, range, sort]);

  const filtered = !!(status || city || q || range.from);
  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const current = Math.min(page, pageCount);
  const visible = rows.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE);
  const reset = () => { setStatus(''); setCity(''); setQ(''); setRange({ from: '', to: '' }); setPage(1); };
  const change = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(1); };
  const toggle = (key: SortKey, first: 'asc' | 'desc' = 'asc') => setSort((s) => ({ key, dir: s.key === key ? (s.dir === 'asc' ? 'desc' : 'asc') : first }));
  const dirOf = (key: SortKey) => (sort.key === key ? sort.dir : null);

  const [cancelId, setCancelId] = useState<string | null>(null);
  const [recover, setRecover] = useState<{ mode: 'reopen' | 'republish'; r: RequestView } | null>(null);

  if (requests.length === 0)
    return (
      <Card>
        <EmptyState title="Aún no tienes solicitudes" description="Publica tu primera carga y recibe ofertas de transportistas en minutos." action={canOperate ? <Button asChild><a href="/pyme/solicitudes/nueva"><Plus />Publicar solicitud</a></Button> : undefined} />
      </Card>
    );

  return (
    <div className="min-w-0 space-y-3">
      <ReadOnlyNotice>No puedes publicar ni cancelar solicitudes.</ReadOnlyNotice>
      <QuickFilters
        label="Estado de la solicitud"
        value={status}
        onValueChange={change(setStatus)}
        options={[
          { value: '', label: 'Todas', count: counts[''] },
          { value: 'REVIEW', label: 'Con ofertas por elegir', count: toReview },
          { value: 'PUBLISHED', label: 'Publicadas', count: counts.PUBLISHED },
          { value: 'MATCHED', label: 'Adjudicadas', count: counts.MATCHED },
          { value: 'CANCELLED', label: 'Canceladas', count: counts.CANCELLED },
        ]}
      />
      <Card className="min-w-0 overflow-hidden">
        <FilterBar
          count={rows.length}
          noun={['solicitud', 'solicitudes']}
          active={filtered}
          onClear={reset}
          sort={<Select aria-label="Ordenar por" className="h-9 w-52" value={`${sort.key}:${sort.dir}`} onValueChange={(v) => { const [key, dir] = v.split(':'); setSort({ key: key as SortKey, dir: dir as 'asc' | 'desc' }); setPage(1); }} options={SORT_OPTIONS} />}
        >
          <Field label="Buscar" htmlFor="f-q">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" />
              <Input id="f-q" className="pl-9" placeholder="Ruta o descripción de la carga" value={q} onChange={(e) => change(setQ)(e.target.value)} />
            </div>
          </Field>
          <Field label="Ciudad de la ruta" htmlFor="f-city">
            <Combobox id="f-city" value={city} onValueChange={change(setCity)} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear />
          </Field>
          <Field label="Fecha de recojo" htmlFor="f-range" className="sm:col-span-2 xl:col-span-2">
            <DateRangePicker id="f-range" value={range} onValueChange={change(setRange)} placeholder="Cualquier fecha" />
          </Field>
        </FilterBar>
        {rows.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="No hay solicitudes con estos filtros"
            description="Prueba con otra ciudad o amplía el rango de fechas."
            action={<Button type="button" variant="outline" onClick={reset}>Limpiar filtros</Button>}
          />
        ) : (
          <>
            <Table label="Tus solicitudes" stack>
              <thead>
                <tr>
                  <Th>Ruta</Th><Th>Carga</Th>
                  <Th sort={dirOf('pickupDate')} onSort={() => toggle('pickupDate')}>Recojo</Th>
                  <Th>Estado</Th>
                  <Th sort={dirOf('bids')} onSort={() => toggle('bids', 'desc')}>Subasta</Th>
                  <Th className="text-right" sort={dirOf('price')} onSort={() => toggle('price', 'desc')}>Sugerido</Th>
                  <Th><span className="sr-only">Acciones</span></Th>
                </tr>
              </thead>
              <tbody>
                {visible.map((r) => {
                  const n = bids(r);
                  const open = r.status === 'PUBLISHED';
                  return (
                    <Tr key={r.id}>
                      <Td primary>
                        <a href={`/pyme/solicitudes/${r.id}`} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</a>
                        <div className="font-mono text-xs text-ink-500">{formatKm(r.distanceKm)}</div>
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
                        <Button asChild variant={n > 0 ? 'money' : 'outline'} size="sm"><a href={`/pyme/solicitudes/${r.id}`}>{n > 0 ? <><Gavel />Comparar ofertas</> : <><Eye />Ver detalle</>}<span className="sr-only"> de {CITY_LABELS[r.originCity!]} a {CITY_LABELS[r.destinationCity!]}</span></a></Button>
                        {canOperate && (open || r.status === 'CANCELLED') && (
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="flex-none!" aria-label={`Más acciones de ${CITY_LABELS[r.originCity!]} a ${CITY_LABELS[r.destinationCity!]}`}><MoreHorizontal /></Button></DropdownMenuTrigger>
                            <DropdownMenuContent>
                              <DropdownMenuItem onSelect={() => { window.location.href = `/pyme/solicitudes/${r.id}`; }}><Eye />Ver detalle</DropdownMenuItem>
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
            <Pagination page={current} size={PAGE_SIZE} total={rows.length} onPageChange={setPage} className="border-t border-steel-200 px-4 py-3" />
          </>
        )}
      </Card>
      {cancelId && <CancelRequestDialog id={cancelId} open onOpenChange={(o) => !o && setCancelId(null)} />}
      {recover && <RescheduleDialog mode={recover.mode} id={recover.r.id!} open onOpenChange={(o) => !o && setRecover(null)} currentPickup={recover.r.pickupDate} />}
    </div>
  );
}
