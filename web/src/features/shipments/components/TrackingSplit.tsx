import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { Calendar, Truck, User, Weight, MapPinned } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Combobox } from '@/components/ui/combobox';
import { Field } from '@/components/ui/field';
import { Avatar } from '@/components/ui/avatar';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { cn } from '@/lib/utils';
import { CITY_LABELS, formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { CityItem, DriverView, ShipmentView } from '@/lib/api/server';
import TrackingMap from './TrackingMap';
import { StopTimeline } from './StopTimeline';
import ShipmentExtras from './ShipmentExtras';

interface Props {
  shipments: ShipmentView[];
  cities: CityItem[];
  initialId?: string | null;
  /** 'PYME' muestra al transportista; 'CARRIER' muestra al solicitante y permite asignar conductor. */
  perspective: 'PYME' | 'CARRIER';
  drivers?: DriverView[];
}

function AssignDriver({ shipment, drivers }: { shipment: ShipmentView; drivers: DriverView[] }) {
  const [driverId, setDriverId] = useState(shipment.driverId ?? '');
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    const { error } = await actions.shipments.assignDriver({ id: shipment.id!, driverId });
    if (error) {
      toast.error(error.message);
      setBusy(false);
      return;
    }
    flash('Conductor asignado');
    window.location.reload();
  }

  if (shipment.status === 'DELIVERED' || shipment.status === 'CANCELLED') return null;
  const options = drivers.map((d) => ({ value: d.id!, label: d.fullName ?? '', hint: d.licenseNumber ?? undefined }));
  const reassign = !!shipment.driverId;
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-ink-900">Conductor del envío</h3>
      {drivers.length === 0 ? (
        <Alert tone="info" title="Aún no tienes conductores">Registra uno en <a className="font-medium text-petrol-700 underline" href="/transportista/flota">Flota</a> para asignarlo a este envío.</Alert>
      ) : (
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <div className="flex-1">
            <Field label={reassign ? 'Reasignar a' : 'Conductor'} htmlFor={`drv-${shipment.id}`} hint={reassign ? undefined : 'Recibirá el envío en su app y podrá iniciar el viaje.'}>
              <Combobox id={`drv-${shipment.id}`} value={driverId} onValueChange={setDriverId} options={options} placeholder="Elige un conductor" searchPlaceholder="Buscar por nombre o licencia" />
            </Field>
          </div>
          <Button className="sm:mb-[1.375rem]" loading={busy} disabled={!driverId || driverId === shipment.driverId} onClick={save}>{reassign ? 'Reasignar conductor' : 'Asignar conductor'}</Button>
        </div>
      )}
    </div>
  );
}

/** Avance de un envío: paradas completadas y parada actual (sin inventar ETA: la API no lo expone). */
function progressOf(s: ShipmentView) {
  const stops = [...(s.stops ?? [])].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0));
  const done = stops.filter((x) => x.status === 'COMPLETED').length;
  const current = stops.find((x) => x.status !== 'COMPLETED') ?? null;
  return { total: stops.length, done, current };
}
const stopLabel = (st: NonNullable<ReturnType<typeof progressOf>['current']>) => `${st.type === 'PICKUP' ? 'Recojo' : 'Entrega'} en ${CITY_LABELS[st.city!] ?? st.city}`;

function Fact({ icon: Icon, label, children }: { icon: React.ComponentType<{ className?: string }>; label: string; children: React.ReactNode }) {
  return (
    <li className="flex min-w-0 items-center gap-3">
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-steel-100 text-ink-700"><Icon className="size-4" aria-hidden="true" /></span>
      <div className="min-w-0"><span className="block text-xs text-ink-500">{label}</span>{children}</div>
    </li>
  );
}

export default function TrackingSplit({ shipments: initialShipments, cities, initialId, perspective, drivers = [] }: Props) {
  // Lista viva: shipment.* / incident.* vuelven a pedir los envíos sin recargar la página (el mapa y la selección se conservan).
  const [shipments, setShipments] = useState<ShipmentView[]>(initialShipments);
  const [refreshing, setRefreshing] = useState(false);
  const refresh = useCallback(async () => {
    setRefreshing(true);
    const { data } = await actions.shipments.list();
    if (data) setShipments(data);
    setRefreshing(false);
  }, []);
  useLiveRefresh({ types: ['shipment.status_changed', 'shipment.stop_updated', 'shipment.driver_assigned', 'incident.reported', 'incident.updated'], refresh, fallbackMs: 60_000 });
  const [selectedId, setSelectedId] = useState<string | null>(initialId && shipments.some((s) => s.id === initialId) ? initialId : (shipments[0]?.id ?? null));
  const [filter, setFilter] = useState<'active' | 'all'>('active');
  const list = useMemo(() => shipments.filter((s) => filter === 'all' || s.status !== 'DELIVERED' && s.status !== 'CANCELLED'), [shipments, filter]);
  const selected = shipments.find((s) => s.id === selectedId) ?? null;
  const listRef = useRef<HTMLDivElement>(null);
  // Al abrir con `?id=`, deja visible el envío elegido dentro de la lista (sin mover la página).
  useEffect(() => {
    const box = listRef.current;
    const el = box?.querySelector<HTMLElement>('[aria-current="true"]');
    if (box && el && el.offsetTop + el.offsetHeight > box.clientHeight) box.scrollTop = Math.max(0, el.offsetTop - 8);
  }, []);

  if (shipments.length === 0)
    return (
      <Card>
        <EmptyState
          title="Todavía no tienes envíos"
          description={perspective === 'PYME' ? 'Cuando aceptes una oferta, tu envío aparecerá aquí con su seguimiento.' : 'Cuando una PYME acepte tu oferta, el envío aparecerá aquí para asignar conductor.'}
          action={<Button asChild><a href={perspective === 'PYME' ? '/pyme/solicitudes/nueva' : '/transportista/marketplace'}>{perspective === 'PYME' ? 'Publicar solicitud' : 'Ver cargas disponibles'}</a></Button>}
        />
      </Card>
    );

  const activeCount = shipments.filter((s) => s.status !== 'DELIVERED' && s.status !== 'CANCELLED').length;
  const party = perspective === 'PYME' ? 'Transportista' : 'Solicitante';
  const partyName = selected ? ((perspective === 'PYME' ? selected.carrierName : selected.requesterName) ?? '') : '';
  const prog = selected ? progressOf(selected) : null;

  // Móvil: mapa → lista (región con scroll propio) → detalle. Escritorio: lista a la izquierda; mapa y detalle a la derecha.
  return (
    <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)] lg:items-start">
      <div className="order-1 min-w-0 lg:order-none lg:col-start-2 lg:row-start-1">
        <TrackingMap shipments={list.length ? list : shipments} cities={cities} selectedId={selectedId} height="clamp(210px, 34vh, 400px)" />
      </div>

      <Card className="order-2 flex flex-col overflow-hidden lg:order-none lg:col-start-1 lg:row-span-2 lg:row-start-1 lg:sticky lg:top-20 lg:max-h-[calc(100vh-7rem)]">
        <Tabs value={filter} onValueChange={(v) => setFilter(v as 'active' | 'all')}>
          <TabsList className="px-3" aria-label="Filtrar envíos">
            <TabsTrigger value="active" aria-controls={undefined}>En curso <span className="ml-1 font-mono text-xs">{activeCount}</span></TabsTrigger>
            <TabsTrigger value="all" aria-controls={undefined}>Todos <span className="ml-1 font-mono text-xs">{shipments.length}</span></TabsTrigger>
          </TabsList>
        </Tabs>
        <div ref={listRef} role="region" aria-label="Lista de envíos" tabIndex={0} className={cn('max-h-72 flex-1 overflow-y-auto overscroll-contain lg:max-h-none', refreshing && 'opacity-80')}>
          <ul className="divide-y divide-steel-200">
            {list.length === 0 && <li className="p-6 text-center text-sm text-ink-500">No hay envíos en curso. Revisa la pestaña Todos para ver los anteriores.</li>}
            {list.map((s) => {
              const who = (perspective === 'PYME' ? s.carrierName : s.requesterName) ?? '';
              const p = progressOf(s);
              return (
                <li key={s.id}>
                  <button type="button" onClick={() => setSelectedId(s.id!)} aria-current={s.id === selectedId} className={cn('w-full space-y-2.5 border-l-4 p-4 text-left transition-colors hover:bg-steel-100/60', s.id === selectedId ? 'border-petrol-700 bg-petrol-50/50' : 'border-transparent')}>
                    <div className="flex items-center justify-between gap-2"><span className="font-mono text-xs text-ink-500">#{s.id?.slice(0, 8)}</span><span className="flex items-center gap-1.5">{s.hasActiveIncident && <Badge tone="red">Incidencia</Badge>}<StatusPill status={s.status!} /></span></div>
                    <RouteLine size="sm" origin={s.originCity!} destination={s.destinationCity!} />
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
      </Card>

      {selected && prog && (
        <Card className="order-3 min-w-0 lg:order-none lg:col-start-2 lg:row-start-2">
          <CardHeader className="items-start">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">{selected.cargoDescription}</CardTitle><StatusPill status={selected.status!} /></div>
              <p className="mt-0.5 font-mono text-xs text-ink-500">#{selected.id?.slice(0, 8)}</p>
            </div>
            <div className="shrink-0 text-right"><div className="text-xs text-ink-500">Precio acordado</div><div className="money whitespace-nowrap text-xl">{formatMoney(selected.agreedPrice)}</div></div>
          </CardHeader>
          <CardBody className="space-y-6">
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
            <div className="border-t border-steel-200 pt-5"><ShipmentExtras key={selected.id} shipment={selected} perspective={perspective} /></div>
            {perspective === 'CARRIER' && <div className="border-t border-steel-200 pt-5"><AssignDriver key={selected.id} shipment={selected} drivers={drivers} /></div>}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
