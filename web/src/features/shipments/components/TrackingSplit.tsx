import { useCallback, useMemo, useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { Calendar, Truck, User, Weight } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Combobox } from '@/components/ui/combobox';
import { Field } from '@/components/ui/field';
import { Avatar } from '@/components/ui/avatar';
import { Alert } from '@/components/ui/alert';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney, formatTons } from '@/lib/format';
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

export default function TrackingSplit({ shipments: initialShipments, cities, initialId, perspective, drivers = [] }: Props) {
  // Lista viva: shipment.* / incident.* vuelven a pedir los envíos sin recargar la página (el mapa y la selección se conservan).
  const [shipments, setShipments] = useState<ShipmentView[]>(initialShipments);
  const refresh = useCallback(async () => {
    const { data } = await actions.shipments.list();
    if (data) setShipments(data);
  }, []);
  useLiveRefresh({ types: ['shipment.status_changed', 'shipment.stop_updated', 'shipment.driver_assigned', 'incident.reported', 'incident.updated'], refresh, fallbackMs: 60_000 });
  const [selectedId, setSelectedId] = useState<string | null>(initialId && shipments.some((s) => s.id === initialId) ? initialId : (shipments[0]?.id ?? null));
  const [filter, setFilter] = useState<'active' | 'all'>('active');
  const list = useMemo(() => shipments.filter((s) => filter === 'all' || s.status !== 'DELIVERED' && s.status !== 'CANCELLED'), [shipments, filter]);
  const selected = shipments.find((s) => s.id === selectedId) ?? null;

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

  return (
    <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
      <Card className="flex flex-col overflow-hidden lg:max-h-[calc(100vh-9rem)]">
        <Tabs value={filter} onValueChange={(v) => setFilter(v as 'active' | 'all')}>
          <TabsList className="px-3" aria-label="Filtrar envíos">
            <TabsTrigger value="active" aria-controls={undefined}>En curso <span className="ml-1 font-mono text-xs">{activeCount}</span></TabsTrigger>
            <TabsTrigger value="all" aria-controls={undefined}>Todos <span className="ml-1 font-mono text-xs">{shipments.length}</span></TabsTrigger>
          </TabsList>
        </Tabs>
        <ul className="max-h-80 flex-1 divide-y divide-steel-200 overflow-y-auto lg:max-h-none">
          {list.length === 0 && <li className="p-6 text-center text-sm text-ink-500">No hay envíos en curso. Revisa la pestaña Todos para ver los anteriores.</li>}
          {list.map((s) => {
            const who = (perspective === 'PYME' ? s.carrierName : s.requesterName) ?? '';
            return (
              <li key={s.id}>
                <button type="button" onClick={() => setSelectedId(s.id!)} aria-current={s.id === selectedId} className={cn('w-full space-y-2.5 border-l-4 p-4 text-left transition-colors hover:bg-steel-100/60', s.id === selectedId ? 'border-petrol-700 bg-petrol-50/50' : 'border-transparent')}>
                  <div className="flex items-center justify-between gap-2"><span className="font-mono text-xs text-ink-500">#{s.id?.slice(0, 8)}</span><StatusPill status={s.status!} /></div>
                  <RouteLine size="sm" origin={s.originCity!} destination={s.destinationCity!} />
                  <div className="flex items-center justify-between gap-2 text-xs text-ink-500">
                    <span className="flex min-w-0 items-center gap-2">{who && <Avatar name={who} size="sm" />}<span className="truncate">{who || 'Sin transportista'}</span></span>
                    <span className="money">{formatMoney(s.agreedPrice)}</span>
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      </Card>

      <div className="min-w-0 space-y-4">
        <TrackingMap shipments={list.length ? list : shipments} cities={cities} selectedId={selectedId} height={380} />
        {selected && (
          <Card>
            <CardHeader>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">{selected.cargoDescription}</CardTitle><StatusPill status={selected.status!} /></div>
                <p className="mt-0.5 font-mono text-xs text-ink-500">#{selected.id?.slice(0, 8)}</p>
              </div>
              <div className="text-right"><div className="text-xs text-ink-500">Precio acordado</div><div className="money text-xl">{formatMoney(selected.agreedPrice)}</div></div>
            </CardHeader>
            <CardBody className="space-y-6">
              <ul className="grid gap-4 text-sm sm:grid-cols-2 xl:grid-cols-3">
                <li className="flex items-center gap-3"><Avatar name={(perspective === 'PYME' ? selected.carrierName : selected.requesterName) ?? '?'} /><div className="min-w-0"><span className="block text-xs text-ink-500">{party}</span><span className="block truncate font-medium">{(perspective === 'PYME' ? selected.carrierName : selected.requesterName) ?? '—'}</span></div></li>
                <li className="flex items-center gap-3"><span className="grid size-9 place-items-center rounded-full bg-steel-100 text-ink-700"><User className="size-4" aria-hidden="true" /></span><div><span className="block text-xs text-ink-500">Conductor</span><span className={cn('block font-medium', !selected.driverName && 'text-amber-700')}>{selected.driverName ?? 'Sin asignar'}</span></div></li>
                <li className="flex items-center gap-3"><span className="grid size-9 place-items-center rounded-full bg-steel-100 text-ink-700"><Truck className="size-4" aria-hidden="true" /></span><div><span className="block text-xs text-ink-500">Vehículo</span><span className="block font-mono font-medium">{selected.vehiclePlate ?? '—'}</span></div></li>
                <li className="flex items-center gap-3"><span className="grid size-9 place-items-center rounded-full bg-steel-100 text-ink-700"><Weight className="size-4" aria-hidden="true" /></span><div><span className="block text-xs text-ink-500">Carga</span><span className="block font-mono font-medium">{formatTons(selected.weightKg)}</span></div></li>
                <li className="flex items-center gap-3"><span className="grid size-9 place-items-center rounded-full bg-steel-100 text-ink-700"><Calendar className="size-4" aria-hidden="true" /></span><div><span className="block text-xs text-ink-500">{selected.deliveredAt ? 'Entregado' : 'Creado'}</span><span className="block font-medium">{formatDateTime(selected.deliveredAt ?? selected.createdAt)}</span></div></li>
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
    </div>
  );
}
