import { useMemo, useState } from 'react';
import { SearchX } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { CityItem } from '@/lib/api/server';
import type { OperatorRequestRow, OperatorShipmentRow } from '../server';

export type ListingFilters = { status: string; origin: string; dest: string; from: string; to: string };
type Kind = 'requests' | 'shipments';

const STATUS: Record<Kind, { value: string; label: string }[]> = {
  requests: [
    { value: 'PUBLISHED', label: 'Publicadas' },
    { value: 'MATCHED', label: 'Adjudicadas' },
    { value: 'CANCELLED', label: 'Canceladas' },
  ],
  shipments: [
    { value: 'PENDING', label: 'Pendientes' },
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
export default function OperatorListing({ kind, rows, cities, filters, page, size, total }: {
  kind: Kind; rows: (OperatorRequestRow | OperatorShipmentRow)[]; cities: CityItem[]; filters: ListingFilters; page: number; size: number; total: number;
}) {
  const [f, setF] = useState(filters);
  const t = TEXT[kind];
  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const hasFilters = !!(f.status || f.origin || f.dest || f.from);

  function go(next: ListingFilters, p = 1) {
    setF(next);
    const q = new URLSearchParams();
    if (next.status) q.set('estado', next.status);
    if (next.origin) q.set('origen', next.origin);
    if (next.dest) q.set('destino', next.dest);
    if (next.from) { q.set('desde', next.from); if (next.to) q.set('hasta', next.to); }
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<ListingFilters>) => go({ ...f, ...patch });
  const clear = () => go({ status: '', origin: '', dest: '', from: '', to: '' });

  return (
    <Card className="min-w-0 overflow-hidden">
      <FilterBar count={total} noun={t.noun} active={hasFilters} onClear={clear}>
        <Field label="Estado" htmlFor="l-s"><Select id="l-s" value={f.status} onValueChange={(v) => set({ status: v })} options={STATUS[kind]} placeholder="Todos los estados" /></Field>
        <Field label="Origen" htmlFor="l-o"><Combobox id="l-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
        <Field label="Destino" htmlFor="l-d"><Combobox id="l-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
        <Field label="Fecha de creación" htmlFor="l-f"><DateRangePicker id="l-f" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
      </FilterBar>
      {rows.length === 0 ? (
        hasFilters
          ? <EmptyState title={`No hay ${t.noun[1]} con estos filtros`} description="Prueba con otro estado o amplía el rango de fechas." action={<Button variant="outline" onClick={clear}><SearchX /> Limpiar filtros</Button>} />
          : <EmptyState title={t.empty} description={t.emptyHint} />
      ) : (
        <Table aria-label={t.label}>
          {kind === 'requests' ? <RequestsHead /> : <ShipmentsHead />}
          <tbody>
            {rows.map((r) => kind === 'requests' ? <RequestRow key={r.id} r={r as OperatorRequestRow} /> : <ShipmentRow key={r.id} r={r as OperatorShipmentRow} />)}
          </tbody>
        </Table>
      )}
      {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
    </Card>
  );
}

const route = (o?: string, d?: string) => `${CITY_LABELS[o ?? ''] ?? o} → ${CITY_LABELS[d ?? ''] ?? d}`;

const RequestsHead = () => <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Solicitante</Th><Th>Creada</Th><Th>Estado</Th><Th className="text-right">Sugerido</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>;
const ShipmentsHead = () => <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Solicitante</Th><Th>Transportista</Th><Th>Creado</Th><Th>Estado</Th><Th className="text-right">Precio</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>;

function RequestRow({ r }: { r: OperatorRequestRow }) {
  const href = `/operador/solicitudes/${r.id}`;
  return (
    <Tr data-testid="operator-row">
      <Td><a href={href} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{route(r.originCity, r.destinationCity)}</a><div className="text-xs text-ink-500">Recojo {formatDate(r.pickupDate)}</div></Td>
      <Td><div>{CARGO_LABELS[r.cargoType!] ?? r.cargoType}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
      <Td><span className="block max-w-48 truncate">{r.requesterName}</span></Td>
      <Td className="whitespace-nowrap">{formatDateTime(r.createdAt)}</Td>
      <Td><StatusPill status={r.status!} /></Td>
      <Td className="text-right"><span className="money">{formatMoney(r.suggestedPrice)}</span></Td>
      <Td className="text-right"><Button asChild variant="outline" size="sm"><a href={href}>Ver detalle<span className="sr-only"> de la solicitud {route(r.originCity, r.destinationCity)}</span></a></Button></Td>
    </Tr>
  );
}

function ShipmentRow({ r }: { r: OperatorShipmentRow }) {
  const href = `/operador/envios/${r.id}`;
  return (
    <Tr data-testid="operator-row">
      <Td><a href={href} className="whitespace-nowrap font-medium text-petrol-700 hover:underline">{route(r.originCity, r.destinationCity)}</a><div className="font-mono text-xs text-ink-500">{r.vehiclePlate ?? 'Sin vehículo'}</div></Td>
      <Td><div className="max-w-56 truncate">{r.cargoDescription}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
      <Td><span className="block max-w-44 truncate">{r.requesterName}</span></Td>
      <Td><span className="block max-w-44 truncate">{r.carrierName}</span><div className="max-w-44 truncate text-xs text-ink-500">{r.driverName ?? 'Sin conductor'}</div></Td>
      <Td className="whitespace-nowrap">{formatDateTime(r.createdAt)}</Td>
      <Td><div className="flex flex-col items-start gap-1"><StatusPill status={r.status!} />{r.hasActiveIncident && <span className="text-xs font-medium text-red-600">Con incidencia</span>}</div></Td>
      <Td className="text-right"><span className="money">{formatMoney(r.agreedPrice)}</span></Td>
      <Td className="text-right"><Button asChild variant="outline" size="sm"><a href={href}>Ver detalle<span className="sr-only"> del envío {route(r.originCity, r.destinationCity)}</span></a></Button></Td>
    </Tr>
  );
}
