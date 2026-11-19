import { useMemo, useState } from 'react';
import { FileText, SearchX, Truck, TriangleAlert } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { QuickFilters } from '@/components/ui/quick-filters';
import { cn } from '@/lib/utils';
import { Combobox } from '@/components/ui/combobox';
import { SearchInput } from '@/components/ui/search-input';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { CityItem } from '@/lib/api/server';
import type { OperatorRequestRow, OperatorShipmentRow } from '../server';

export type ListingFilters = { status: string; origin: string; dest: string; from: string; to: string; q: string };
type Kind = 'requests' | 'shipments';

const STATUS: Record<Kind, { value: string; label: string }[]> = {
  requests: [
    { value: '', label: 'Todas' },
    { value: 'PUBLISHED', label: 'Publicadas' },
    { value: 'MATCHED', label: 'Adjudicadas' },
    { value: 'CANCELLED', label: 'Canceladas' },
  ],
  shipments: [
    { value: '', label: 'Todos' },
    { value: 'PENDING', label: 'Por recoger' },
    { value: 'IN_TRANSIT', label: 'En tránsito' },
    { value: 'DELIVERED', label: 'Entregados' },
    { value: 'CANCELLED', label: 'Cancelados' },
  ],
};
const TEXT: Record<Kind, { noun: [string, string]; label: string; empty: string; emptyHint: string; base: string }> = {
  requests: { noun: ['solicitud', 'solicitudes'], label: 'Solicitudes', empty: 'Aún no hay solicitudes', emptyHint: 'Cuando una PYME publique una carga, la verás aquí.', base: '/operador/solicitudes' },
  shipments: { noun: ['envío', 'envíos'], label: 'Envíos', empty: 'Aún no hay envíos', emptyHint: 'Cuando una PYME acepte una oferta o reserve un viaje, el envío aparecerá aquí.', base: '/operador/envios' },
};

/** Listado global del operador (solicitudes o envíos): filtros en la URL, paginación en el servidor. */
export default function OperatorListing({ kind, rows, cities, filters, page, size, total, counts }: {
  kind: Kind; rows: (OperatorRequestRow | OperatorShipmentRow)[]; cities: CityItem[]; filters: ListingFilters; page: number; size: number; total: number;
  /** Totales globales por estado (de las métricas); solo se muestran sin filtros de ruta o fecha. */
  counts?: Record<string, number>;
}) {
  const [f, setF] = useState(filters);
  const [navigating, setNavigating] = useState(false);
  const t = TEXT[kind];
  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const hasFilters = !!(f.status || f.origin || f.dest || f.from || f.q);
  const showCounts = !!counts && !(f.origin || f.dest || f.from || f.q);
  const statusOptions = STATUS[kind].map((o) => ({ ...o, count: showCounts ? counts![o.value] : undefined }));

  function go(next: ListingFilters, p = 1) {
    setF(next);
    setNavigating(true);
    const q = new URLSearchParams();
    if (next.status) q.set('estado', next.status);
    if (next.origin) q.set('origen', next.origin);
    if (next.dest) q.set('destino', next.dest);
    if (next.from) { q.set('desde', next.from); if (next.to) q.set('hasta', next.to); }
    if (next.q) q.set('q', next.q);
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<ListingFilters>) => go({ ...f, ...patch });
  const clear = () => go({ status: '', origin: '', dest: '', from: '', to: '', q: '' });

  return (
    <div className="min-w-0 space-y-3" aria-busy={navigating || undefined}>
      <QuickFilters label="Estado" value={f.status} onValueChange={(v) => set({ status: v })} options={statusOptions} />
      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar count={total} noun={t.noun} active={hasFilters} onClear={clear}>
          <Field label="Buscar" htmlFor="l-q" hint={kind === 'requests' ? 'Código, referencia, razón social, RUC o placa' : 'Razón social, RUC, placa, conductor o código'} className="sm:col-span-2"><SearchInput id="l-q" value={f.q} onSearch={(text) => text.length === 1 ? undefined : set({ q: text })} placeholder="Escribe al menos 2 caracteres" maxLength={60} /></Field>
          <Field label="Origen" htmlFor="l-o"><Combobox id="l-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Destino" htmlFor="l-d"><Combobox id="l-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Fecha de creación" htmlFor="l-f" className="sm:col-span-2 xl:col-span-2"><DateRangePicker id="l-f" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
        </FilterBar>
        {rows.length === 0 ? (
          hasFilters
            ? <EmptyState icon={SearchX} title={`No hay ${t.noun[1]} con estos filtros`} description={f.q ? `Nada coincide con «${f.q}». Prueba con otra razón social, RUC, placa o código.` : 'Prueba con otro estado o amplía el rango de fechas.'} action={<Button variant="outline" onClick={clear}><SearchX /> Limpiar filtros</Button>} />
            : <EmptyState icon={kind === 'requests' ? FileText : Truck} title={t.empty} description={t.emptyHint} />
        ) : (
          <Table label={t.label} stack dense>
            {kind === 'requests' ? <RequestsHead /> : <ShipmentsHead />}
            <tbody>
              {rows.map((r) => kind === 'requests' ? <RequestRow key={r.id} r={r as OperatorRequestRow} /> : <ShipmentRow key={r.id} r={r as OperatorShipmentRow} />)}
            </tbody>
          </Table>
        )}
        {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>
    </div>
  );
}

const route = (o?: string, d?: string) => `${CITY_LABELS[o ?? ''] ?? o} → ${CITY_LABELS[d ?? ''] ?? d}`;

const RequestsHead = () => <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Solicitante</Th><Th>Creada</Th><Th>Estado</Th><Th className="text-right">Sugerido</Th><Th className="relative"><span className="sr-only">Acciones</span></Th></tr></thead>;
const ShipmentsHead = () => <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Solicitante</Th><Th>Transportista</Th><Th>Creado</Th><Th>Estado</Th><Th className="text-right">Precio</Th><Th className="relative"><span className="sr-only">Acciones</span></Th></tr></thead>;

function RequestRow({ r }: { r: OperatorRequestRow }) {
  const href = `/operador/solicitudes/${r.id}`;
  return (
    <Tr data-testid="operator-row">
      <Td primary><a href={href} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{route(r.originCity, r.destinationCity)}</a><div className="text-xs text-ink-500">Recojo {formatDate(r.pickupDate)}</div></Td>
      <Td label="Carga"><div>{CARGO_LABELS[r.cargoType!] ?? r.cargoType}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
      <Td label="Solicitante"><span className="block max-w-48 truncate">{r.requesterName}</span></Td>
      <Td label="Creada" className="whitespace-nowrap">{formatDateTime(r.createdAt)}</Td>
      <Td label="Estado"><StatusPill status={r.status!} /></Td>
      <Td label="Sugerido" className="text-right"><span className="money">{formatMoney(r.suggestedPrice)}</span></Td>
      <Td actions className="relative text-right"><Button asChild variant="outline" size="sm"><a href={href}>Ver detalle<span className="sr-only"> de la solicitud {route(r.originCity, r.destinationCity)}</span></a></Button></Td>
    </Tr>
  );
}

function ShipmentRow({ r }: { r: OperatorShipmentRow }) {
  const href = `/operador/envios/${r.id}`;
  return (
    <Tr data-testid="operator-row">
      <Td primary><a href={href} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{route(r.originCity, r.destinationCity)}</a><div className="font-mono text-xs text-ink-500">{r.vehiclePlate ?? 'Sin vehículo'}</div></Td>
      <Td label="Carga"><div className="max-w-56 truncate">{r.cargoDescription}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
      <Td label="Solicitante"><span className="block max-w-44 truncate">{r.requesterName}</span></Td>
      <Td label="Transportista"><span className="block max-w-44 truncate">{r.carrierName}</span><div className="max-w-44 truncate text-xs text-ink-500">{r.driverName ?? 'Sin conductor'}</div></Td>
      <Td label="Creado" className="whitespace-nowrap">{formatDateTime(r.createdAt)}</Td>
      <Td label="Estado"><div className="flex flex-col items-start gap-1"><StatusPill status={r.status!} />{r.hasActiveIncident && <span className="inline-flex items-center gap-1 text-xs font-medium text-red-700"><TriangleAlert className="size-3" aria-hidden="true" />Con incidencia</span>}</div></Td>
      <Td label="Precio" className="text-right"><span className="money">{formatMoney(r.agreedPrice)}</span></Td>
      <Td actions className="relative text-right"><Button asChild variant="outline" size="sm"><a href={href}>Ver detalle<span className="sr-only"> del envío {route(r.originCity, r.destinationCity)}</span></a></Button></Td>
    </Tr>
  );
}
