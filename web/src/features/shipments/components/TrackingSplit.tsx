import { PriceBreakdown } from '@/components/domain/PriceBreakdown';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { Calendar, Copy, MapPinned, Search, SearchX, Truck, User, Weight } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Input } from '@/components/ui/input';
import { QuickFilters } from '@/components/ui/quick-filters';
import { ExportButton } from '@/components/ui/export-button';
import { Pagination } from '@/components/ui/pagination';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { Can } from '@/lib/permissions/react';
import { cn } from '@/lib/utils';
import { CITY_LABELS, formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { CityItem, DriverView, ShipmentView } from '@/lib/api/server';
import ActivityTrail from '@/features/activity/components/ActivityTrail';
import TrackingMap from './TrackingMap';
import { StopTimeline } from './StopTimeline';
import { StopContacts } from './StopContacts';
import ShipmentExtras from './ShipmentExtras';
import { ShipmentListNote } from './ShipmentListNote';
import { ShareTrackingButton } from './ShareTrackingDialog';
import { SHIPMENTS_PAGE_SIZE, apiQuery, hasFineFilters, toUrlQuery, type ShipmentFilters } from '../filters';

interface Props {
  /** La página de envíos que cumple los filtros (ya filtrada y paginada en el servidor, roadmap 1.5). */
  shipments: ShipmentView[];
  total: number;
  /** Página 1-based. */
  page: number;
  filters: ShipmentFilters;
  /** false = la organización aún no tiene ningún envío (estado vacío de bienvenida). */
  anyShipments: boolean;
  cities: CityItem[];
  initialId?: string | null;
  /** 'PYME' muestra al transportista; 'CARRIER' muestra al solicitante y permite asignar conductor. */
  perspective: 'PYME' | 'CARRIER';
  drivers?: DriverView[];
}

const STATUS_FILTERS = [
  { value: '', label: 'En curso' },
  { value: 'PENDING', label: 'Por recoger' },
  { value: 'IN_TRANSIT', label: 'En tránsito' },
  { value: 'DELIVERED', label: 'Entregados' },
  { value: 'CANCELLED', label: 'Cancelados' },
  { value: 'todos', label: 'Todos' },
];

/** Avance de un envío: paradas completadas y parada actual (la ETA aproximada solo existe en el seguimiento público). */
function progressOf(s: ShipmentView) {
  const stops = [...(s.stops ?? [])].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0));
  const done = stops.filter((x) => x.status === 'COMPLETED').length;
  const current = stops.find((x) => x.status !== 'COMPLETED') ?? null;
  return { total: stops.length, done, current };
}
const stopLabel = (st: NonNullable<ReturnType<typeof progressOf>['current']>) => `${st.type === 'PICKUP' ? 'Recojo' : 'Entrega'} en ${CITY_LABELS[st.city!] ?? st.city}`;
/** Código legible del envío (heredado de la solicitud); los envíos sin código muestran el id corto. */
export const shipmentCode = (s: ShipmentView) => s.code ?? `#${s.id?.slice(0, 8)}`;

function Fact({ icon: Icon, label, children }: { icon: React.ComponentType<{ className?: string }>; label: string; children: React.ReactNode }) {
  return (
    <li className="flex min-w-0 items-center gap-3">
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-steel-100 text-ink-700"><Icon className="size-4" aria-hidden="true" /></span>
      <div className="min-w-0"><span className="block text-xs text-ink-500">{label}</span>{children}</div>
    </li>
  );
}

/**
 * «Envíos» de la PYME y del transportista: lista + mapa + detalle. Filtros, búsqueda y paginación en el SERVIDOR
 * (`GET /shipments`), con la URL como estado (`estado`, `origen`, `destino`, `desde`, `hasta`, `q`, `pagina`, `id`);
 * «Exportar CSV» baja exactamente lo filtrado (`/exportar/envios`). La página se refresca en vivo con los eventos del envío.
 */
export default function TrackingSplit({ shipments: initialShipments, total: initialTotal, page, filters, anyShipments, cities, initialId, perspective, drivers = [] }: Props) {
  void drivers; // la asignación vive en ShipmentActions (dentro de ShipmentExtras)
  const [shipments, setShipments] = useState<ShipmentView[]>(initialShipments);
  const [total, setTotal] = useState(initialTotal);
  const [refreshing, setRefreshing] = useState(false);
  const query = useMemo(() => ({ ...apiQuery(filters), page: page - 1, size: SHIPMENTS_PAGE_SIZE }), [filters, page]);
  const selectedRef = useRef<string | null>(null);
  const refresh = useCallback(async () => {
    setRefreshing(true);
    const { data } = await actions.shipments.page(query);
    if (data) {
      // Un envío abierto por enlace directo (`?id=`) que no cumple el filtro se conserva en la lista.
      setShipments((prev) => {
        const sel = prev.find((x) => x.id === selectedRef.current);
        return sel && !data.items.some((x) => x.id === sel.id) ? [sel, ...data.items] : data.items;
      });
      setTotal(data.meta.total);
    }
    setRefreshing(false);
  }, [query]);
  useLiveRefresh({ types: ['shipment.status_changed', 'shipment.stop_updated', 'shipment.driver_assigned', 'incident.reported', 'incident.updated'], refresh, fallbackMs: 60_000 });

  const [f, setF] = useState(filters);
  const [qText, setQText] = useState(filters.q);
  const [navigating, setNavigating] = useState(false);
  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  function go(next: ShipmentFilters, p = 1) {
    setF(next);
    setNavigating(true);
    const qs = toUrlQuery(next, p).toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<ShipmentFilters>) => go({ ...f, ...patch });
  const reset = () => go({ status: f.status, origin: '', dest: '', from: '', to: '', q: '' });
  const exportQs = toUrlQuery(f).toString();

  const [selectedId, setSelectedId] = useState<string | null>(initialId && shipments.some((s) => s.id === initialId) ? initialId : (shipments[0]?.id ?? null));
  const selected = shipments.find((s) => s.id === selectedId) ?? null;
  selectedRef.current = selectedId;
  const listRef = useRef<HTMLDivElement>(null);
  // Al abrir con `?id=`, deja visible el envío elegido dentro de la lista (sin mover la página).
  useEffect(() => {
    const box = listRef.current;
    const el = box?.querySelector<HTMLElement>('[aria-current="true"]');
    if (box && el && el.offsetTop + el.offsetHeight > box.clientHeight) box.scrollTop = Math.max(0, el.offsetTop - 8);
  }, []);

  if (!anyShipments)
    return (
      <Card>
        <EmptyState
          title="Todavía no tienes envíos"
          description={perspective === 'PYME' ? 'Cuando aceptes una oferta, tu envío aparecerá aquí con su seguimiento.' : 'Cuando una PYME acepte tu oferta, el envío aparecerá aquí para asignar conductor.'}
          action={<Button asChild><a href={perspective === 'PYME' ? '/pyme/solicitudes/nueva' : '/transportista/marketplace'}>{perspective === 'PYME' ? 'Publicar solicitud' : 'Ver cargas disponibles'}</a></Button>}
        />
      </Card>
    );

  const party = perspective === 'PYME' ? 'Transportista' : 'Solicitante';
  const partyName = selected ? ((perspective === 'PYME' ? selected.carrierName : selected.requesterName) ?? '') : '';
  const prog = selected ? progressOf(selected) : null;
  const fine = hasFineFilters(f);

  return (
    <div className="min-w-0 space-y-4" aria-busy={navigating || undefined}>
      <QuickFilters label="Estado del envío" value={f.status} onValueChange={(v) => set({ status: v })} options={STATUS_FILTERS} />
      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar count={total} noun={['envío', 'envíos']} active={fine} onClear={reset} extra={<ExportButton href={`/exportar/envios${exportQs ? `?${exportQs}` : ''}`} />}>
          <Field label="Buscar" htmlFor="sf-q">
            <form role="search" onSubmit={(e) => { e.preventDefault(); set({ q: qText.trim() }); }} className="relative">
              <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" />
              <Input id="sf-q" className="pl-9" placeholder="Código, referencia, placa o conductor" value={qText} onChange={(e) => setQText(e.target.value)} onBlur={() => qText.trim() !== f.q && set({ q: qText.trim() })} maxLength={80} />
            </form>
          </Field>
          <Field label="Origen" htmlFor="sf-o"><Combobox id="sf-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Destino" htmlFor="sf-d"><Combobox id="sf-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Fecha de recojo" htmlFor="sf-range">
            <DateRangePicker id="sf-range" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" />
          </Field>
        </FilterBar>
      </Card>

      {shipments.length === 0 ? (
        <Card>
          <EmptyState
            icon={SearchX}
            title={fine ? 'No hay envíos con estos filtros' : f.status === '' ? 'No tienes envíos en curso' : 'No hay envíos en este estado'}
            description={fine ? 'Prueba con otra ciudad, otro código o amplía el rango de fechas.' : 'Revisa «Todos» para ver los anteriores.'}
            action={fine ? <Button type="button" variant="outline" onClick={reset}>Limpiar filtros</Button> : <Button type="button" variant="outline" onClick={() => set({ status: 'todos' })}>Ver todos</Button>}
          />
        </Card>
      ) : (
        // Móvil: mapa → lista (región con scroll propio) → detalle. Escritorio: lista a la izquierda; mapa y detalle a la derecha.
        <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)] lg:items-start">
          <div className="order-1 min-w-0 lg:order-none lg:col-start-2 lg:row-start-1">
            <TrackingMap shipments={shipments} cities={cities} selectedId={selectedId} height="clamp(210px, 34vh, 400px)" />
          </div>

          <Card className="order-2 flex flex-col overflow-hidden lg:order-none lg:col-start-1 lg:row-span-2 lg:row-start-1 lg:sticky lg:top-20 lg:max-h-[calc(100vh-7rem)]">
            <div ref={listRef} role="region" aria-label="Lista de envíos" tabIndex={0} className={cn('max-h-72 flex-1 overflow-y-auto overscroll-contain lg:max-h-none', refreshing && 'opacity-80')}>
              <ul className="divide-y divide-steel-200">
                {shipments.map((s) => {
                  const who = (perspective === 'PYME' ? s.carrierName : s.requesterName) ?? '';
                  const p = progressOf(s);
                  return (
                    <li key={s.id}>
                      <button type="button" onClick={() => setSelectedId(s.id!)} aria-current={s.id === selectedId} className={cn('w-full space-y-2.5 border-l-4 p-4 text-left transition-colors hover:bg-steel-100/60', s.id === selectedId ? 'border-petrol-700 bg-petrol-50/50' : 'border-transparent')}>
                        <div className="flex items-center justify-between gap-2"><span className="font-mono text-xs text-ink-500">{shipmentCode(s)}</span><span className="flex items-center gap-1.5">{s.hasActiveIncident && <Badge tone="red">Incidencia</Badge>}<StatusPill status={s.status!} /></span></div>
                        <RouteLine size="sm" origin={s.originCity!} destination={s.destinationCity!} />
                        <ShipmentListNote shipment={s} perspective={perspective} />
                        {p.total > 0 && (
                          <div className="flex items-center gap-2 text-xs text-ink-500">
                            <div className="h-1 flex-1 overflow-hidden rounded-full bg-steel-200" aria-hidden="true"><div className="h-full rounded-full bg-emerald-500" style={{ width: `${(p.done / p.total) * 100}%` }} /></div>
                            <span className="font-mono tabular-nums">{p.done}/{p.total}</span>
                          </div>
                        )}
                        <div className="flex items-center justify-between gap-2 text-xs text-ink-500">
                          <span className="flex min-w-0 items-center gap-2">{who && <Avatar name={who} size="sm" />}<span className="truncate">{who || 'Sin transportista'}</span></span>
                          <span className="money shrink-0 whitespace-nowrap">{formatMoney(s.agreedPrice)}</span>
                        </div>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
            {total > SHIPMENTS_PAGE_SIZE && <Pagination className="border-t border-steel-200 px-3 py-2" page={page} size={SHIPMENTS_PAGE_SIZE} total={total} onPageChange={(p) => go(f, p)} />}
          </Card>

          {selected && prog && (
            <div className="order-3 min-w-0 space-y-4 lg:order-none lg:col-start-2 lg:row-start-2">
              <Card className="min-w-0">
                <CardHeader className="items-start">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">{selected.cargoDescription}</CardTitle><StatusPill status={selected.status!} /></div>
                    <p className="mt-0.5 text-xs text-ink-500"><span className="font-mono">{shipmentCode(selected)}</span>{selected.customerReference && <> · Tu referencia <span className="font-mono text-ink-700">{selected.customerReference}</span></>}</p>
                  </div>
                  <div className="shrink-0 text-right"><div className="text-xs text-ink-500">Precio acordado</div><div className="money whitespace-nowrap text-xl">{formatMoney(selected.agreedPrice)}</div><PriceBreakdown breakdown={selected.priceBreakdown} className="mt-1 flex flex-col items-end" /></div>
                </CardHeader>
                <CardBody className="space-y-6">
                  {perspective === 'PYME' && (
                    <div className="flex flex-wrap items-center gap-2">
                      <ShareTrackingButton key={selected.id} shipment={selected} />
                      <Can><Button asChild variant="outline" size="sm"><a href={`/pyme/solicitudes/nueva?from=${selected.requestId}`}><Copy aria-hidden="true" />Duplicar</a></Button></Can>
                    </div>
                  )}
                  {selected.status !== 'DELIVERED' && selected.status !== 'CANCELLED' && prog.total > 0 && (
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-input bg-petrol-50 px-4 py-3 text-sm">
                      <MapPinned className="size-5 shrink-0 text-petrol-700" aria-hidden="true" />
                      <div className="min-w-0 flex-1">
                        <div className="text-xs text-ink-500">Parada actual</div>
                        <div className="font-medium text-ink-900">{prog.current ? stopLabel(prog.current) : 'Todas las paradas completadas'}{prog.current?.status === 'ARRIVED' && <span className="ml-2 text-xs font-medium text-petrol-700">El conductor llegó</span>}</div>
                      </div>
                      <div className="flex w-full items-center gap-2 sm:w-44"><div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface" aria-hidden="true"><div className="h-full rounded-full bg-emerald-500" style={{ width: `${(prog.done / prog.total) * 100}%` }} /></div><span className="font-mono text-xs tabular-nums text-ink-700">{prog.done}/{prog.total} paradas</span></div>
                    </div>
                  )}
                  <ul className="grid gap-4 text-sm sm:grid-cols-2 xl:grid-cols-3">
                    <li className="flex min-w-0 items-center gap-3"><Avatar name={partyName || '?'} /><div className="min-w-0"><span className="block text-xs text-ink-500">{party}</span><span className="block truncate font-medium">{partyName || '—'}</span></div></li>
                    <Fact icon={User} label="Conductor"><span className={cn('block truncate font-medium', !selected.driverName && 'text-amber-700')}>{selected.driverName ?? 'Sin asignar'}</span></Fact>
                    <Fact icon={Truck} label="Vehículo"><span className="block font-mono font-medium">{selected.vehiclePlate ?? '—'}</span></Fact>
                    <Fact icon={Weight} label="Carga"><span className="block font-mono font-medium">{formatTons(selected.weightKg)}</span></Fact>
                    <Fact icon={Calendar} label={selected.deliveredAt ? 'Entregado' : 'Creado'}><span className="block font-medium">{formatDateTime(selected.deliveredAt ?? selected.createdAt)}</span></Fact>
                  </ul>
                  <div className="border-t border-steel-200 pt-5">
                    <h3 className="mb-3 text-sm font-semibold text-ink-900">Paradas</h3>
                    <StopTimeline stops={selected.stops ?? []} />
                  </div>
                  <StopContacts stops={selected.stops ?? []} />
                  <div className="border-t border-steel-200 pt-5"><ShipmentExtras key={selected.id} shipment={selected} perspective={perspective} /></div>
                </CardBody>
              </Card>
              <ActivityTrail key={`act-${selected.id}`} subject="SHIPMENT" id={selected.id!} liveTypes={['shipment.status_changed', 'shipment.stop_updated', 'shipment.driver_assigned']} />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
