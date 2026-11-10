import { useMemo, useState } from 'react';
import { Search, FilterX } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker, type DateRangeValue } from '@/components/ui/date-picker';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Card } from '@/components/ui/card';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { Button } from '@/components/ui/button';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { RequestView } from '@/lib/api/server';

const PAGE_SIZE = 10;
const STATUS_OPTIONS = [
  { value: 'PUBLISHED', label: 'Publicadas' },
  { value: 'MATCHED', label: 'Adjudicadas' },
  { value: 'CANCELLED', label: 'Canceladas' },
];

export default function RequestsTable({ requests }: { requests: RequestView[] }) {
  const [status, setStatus] = useState('');
  const [city, setCity] = useState('');
  const [q, setQ] = useState('');
  const [range, setRange] = useState<DateRangeValue>({ from: '', to: '' });
  const [page, setPage] = useState(1);

  const cityOptions = useMemo(() => {
    const codes = new Set<string>();
    requests.forEach((r) => { if (r.originCity) codes.add(r.originCity); if (r.destinationCity) codes.add(r.destinationCity); });
    return [...codes].map((c) => ({ value: c, label: CITY_LABELS[c] ?? c })).sort((a, b) => a.label.localeCompare(b.label, 'es'));
  }, [requests]);

  const rows = useMemo(() => {
    const term = q.trim().toLowerCase();
    return requests
      .filter((r) => !status || r.status === status)
      .filter((r) => !city || r.originCity === city || r.destinationCity === city)
      .filter((r) => !range.from || ((r.pickupDate ?? '') >= range.from && (r.pickupDate ?? '') <= (range.to || range.from)))
      .filter((r) => !term || [CITY_LABELS[r.originCity!], CITY_LABELS[r.destinationCity!], r.cargoDescription].join(' ').toLowerCase().includes(term))
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  }, [requests, status, city, q, range]);

  const filtered = !!(status || city || q || range.from);
  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const current = Math.min(page, pageCount);
  const visible = rows.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE);
  const reset = () => { setStatus(''); setCity(''); setQ(''); setRange({ from: '', to: '' }); setPage(1); };
  const change = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(1); };

  return (
    <Card className="min-w-0 overflow-hidden">
      <div className="grid gap-3 border-b border-steel-200 p-4 sm:grid-cols-2 xl:grid-cols-[minmax(0,1.4fr)_repeat(3,minmax(0,1fr))_auto]" role="search" aria-label="Filtrar solicitudes">
        <div className="relative">
          <label htmlFor="f-q" className="sr-only">Buscar por ruta o carga</label>
          <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" />
          <Input id="f-q" className="pl-9" placeholder="Buscar ruta o carga…" value={q} onChange={(e) => change(setQ)(e.target.value)} />
        </div>
        <div>
          <label htmlFor="f-status" className="sr-only">Estado</label>
          <Select id="f-status" value={status} onValueChange={change(setStatus)} options={STATUS_OPTIONS} placeholder="Todos los estados" />
        </div>
        <div>
          <label htmlFor="f-city" className="sr-only">Ciudad de la ruta</label>
          <Combobox id="f-city" value={city} onValueChange={change(setCity)} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear />
        </div>
        <div>
          <label htmlFor="f-range" className="sr-only">Fecha de recojo</label>
          <DateRangePicker id="f-range" value={range} onValueChange={change(setRange)} placeholder="Fecha de recojo" />
        </div>
        <Button type="button" variant="ghost" onClick={reset} disabled={!filtered} className="justify-self-start"><FilterX className="size-4" aria-hidden="true" /> Limpiar</Button>
      </div>
      <div className="flex items-center justify-between px-4 pt-3 text-xs text-ink-500" aria-live="polite">
        <span><span className="font-mono tabular-nums text-ink-700">{rows.length}</span> solicitud{rows.length === 1 ? '' : 'es'}{filtered ? ' con estos filtros' : ''}</span>
      </div>
      {rows.length === 0 ? (
        <EmptyState
          title={requests.length === 0 ? 'Aún no tienes solicitudes' : 'No hay solicitudes con estos filtros'}
          description={requests.length === 0 ? 'Publica tu primera carga y recibe ofertas de transportistas en minutos.' : 'Prueba con otra ciudad o amplía el rango de fechas.'}
          action={requests.length === 0 ? <Button asChild><a href="/pyme/solicitudes/nueva">Publicar solicitud</a></Button> : <Button type="button" variant="outline" onClick={reset}>Limpiar filtros</Button>}
        />
      ) : (
        <>
          <div className="mt-2 overflow-x-auto">
            <Table>
              <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Recojo</Th><Th>Estado</Th><Th>Subasta</Th><Th className="text-right">Sugerido</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
              <tbody>
                {visible.map((r) => (
                  <Tr key={r.id}>
                    <Td>
                      <a href={`/pyme/solicitudes/${r.id}`} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</a>
                      <div className="font-mono text-xs text-ink-500">{formatKm(r.distanceKm)}</div>
                    </Td>
                    <Td><div>{CARGO_LABELS[r.cargoType!]}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
                    <Td className="whitespace-nowrap">{formatDate(r.pickupDate)}</Td>
                    <Td><StatusPill status={r.status!} /></Td>
                    <Td className="whitespace-nowrap">
                      {r.status === 'PUBLISHED' && r.auction ? (
                        <div className="space-y-0.5"><div className="text-xs text-ink-700"><span className="font-mono">{r.auction.bidCount ?? 0}/{r.auction.maxBids ?? 5}</span> ofertas</div><Countdown closesAt={r.auction.closesAt} /></div>
                      ) : <span className="text-ink-500">—</span>}
                    </Td>
                    <Td className="text-right"><span className="money">{formatMoney(r.suggestedPrice)}</span></Td>
                    <Td className="text-right"><Button asChild variant="outline" size="sm"><a href={`/pyme/solicitudes/${r.id}`}>Ver detalle</a></Button></Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
          </div>
          <Pagination page={current} size={PAGE_SIZE} total={rows.length} onPageChange={setPage} className="border-t border-steel-200 px-4 py-3" />
        </>
      )}
    </Card>
  );
}
