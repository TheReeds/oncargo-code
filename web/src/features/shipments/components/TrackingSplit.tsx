import { useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Truck, User } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { CityItem, DriverView, ShipmentView } from '@/lib/api/server';
import TrackingMap from './TrackingMap';
import { StopTimeline } from './StopTimeline';

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
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  async function save() {
    setError(null);
    setOk(false);
    setBusy(true);
    const { error } = await actions.shipments.assignDriver({ id: shipment.id!, driverId });
    setBusy(false);
    if (error) return setError(error.message);
    setOk(true);
    setTimeout(() => window.location.reload(), 600);
  }

  if (shipment.status === 'DELIVERED' || shipment.status === 'CANCELLED') return null;
  return (
    <div className="space-y-2">
      <label htmlFor={`drv-${shipment.id}`} className="block text-[13px] font-medium text-ink-700">{shipment.driverId ? 'Reasignar conductor' : 'Asignar conductor'}</label>
      <div className="flex gap-2">
        <Select id={`drv-${shipment.id}`} value={driverId} onChange={(e) => setDriverId(e.target.value)}>
          <option value="">Selecciona…</option>
          {drivers.map((d) => <option key={d.id} value={d.id}>{d.fullName} · {d.licenseNumber}</option>)}
        </Select>
        <Button onClick={save} disabled={!driverId || busy || driverId === shipment.driverId}>{busy ? 'Guardando…' : 'Asignar'}</Button>
      </div>
      {drivers.length === 0 && <p className="text-xs text-ink-500">Aún no tienes conductores. <a className="text-petrol-700 underline" href="/transportista/flota">Registra uno en Flota</a>.</p>}
      {error && <Alert tone="error">{error}</Alert>}
      {ok && <Alert tone="success">Conductor asignado.</Alert>}
    </div>
  );
}

export default function TrackingSplit({ shipments, cities, initialId, perspective, drivers = [] }: Props) {
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
          action={<Button asChild><a href={perspective === 'PYME' ? '/pyme/solicitudes/nueva' : '/transportista/marketplace'}>{perspective === 'PYME' ? 'Nueva solicitud' : 'Ir al marketplace'}</a></Button>}
        />
      </Card>
    );

  return (
    <div className="grid gap-4 lg:grid-cols-[360px_1fr]">
      <Card className="flex max-h-[calc(100vh-9rem)] flex-col overflow-hidden">
        <div className="flex gap-1 border-b border-steel-200 p-2" role="tablist" aria-label="Filtrar envíos">
          {(['active', 'all'] as const).map((f) => (
            <button key={f} role="tab" aria-selected={filter === f} onClick={() => setFilter(f)} className={cn('flex-1 rounded-input px-3 py-1.5 text-sm font-medium', filter === f ? 'bg-petrol-50 text-petrol-700' : 'text-ink-700 hover:bg-steel-100')}>
              {f === 'active' ? 'En curso' : 'Todos'}
            </button>
          ))}
        </div>
        <ul className="flex-1 divide-y divide-steel-200 overflow-y-auto">
          {list.length === 0 && <li className="p-6 text-center text-sm text-ink-500">No hay envíos en curso.</li>}
          {list.map((s) => (
            <li key={s.id}>
              <button onClick={() => setSelectedId(s.id!)} aria-current={s.id === selectedId} className={cn('w-full space-y-2 border-l-4 p-4 text-left hover:bg-steel-100/60', s.id === selectedId ? 'border-petrol-700 bg-petrol-50/50' : 'border-transparent')}>
                <div className="flex items-center justify-between gap-2"><span className="font-mono text-xs text-ink-500">#{s.id?.slice(0, 8)}</span><StatusPill status={s.status!} /></div>
                <RouteLine size="sm" origin={s.originCity!} destination={s.destinationCity!} />
                <div className="flex items-center justify-between text-xs text-ink-500"><span className="truncate">{perspective === 'PYME' ? s.carrierName : s.requesterName}</span><span className="money">{formatMoney(s.agreedPrice)}</span></div>
              </button>
            </li>
          ))}
        </ul>
      </Card>

      <div className="min-w-0 space-y-4">
        <TrackingMap shipments={list.length ? list : shipments} cities={cities} selectedId={selectedId} height={380} />
        {selected && (
          <Card>
            <CardBody className="space-y-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2"><h2 className="text-base font-semibold">{selected.cargoDescription}</h2><StatusPill status={selected.status!} /></div>
                  <p className="mt-0.5 text-sm text-ink-500"><span className="font-mono">{formatTons(selected.weightKg)}</span> · creado {formatDateTime(selected.createdAt)}{selected.deliveredAt ? ` · entregado ${formatDateTime(selected.deliveredAt)}` : ''}</p>
                </div>
                <div className="text-right"><div className="text-xs text-ink-500">Precio acordado</div><div className="money text-xl">{formatMoney(selected.agreedPrice)}</div></div>
              </div>
              <dl className="grid gap-3 text-sm sm:grid-cols-3">
                <div className="flex items-center gap-2"><User className="size-4 text-ink-500" aria-hidden="true" /><div><dt className="text-xs text-ink-500">{perspective === 'PYME' ? 'Transportista' : 'Cliente'}</dt><dd className="font-medium">{(perspective === 'PYME' ? selected.carrierName : selected.requesterName) ?? '—'}</dd></div></div>
                <div className="flex items-center gap-2"><Truck className="size-4 text-ink-500" aria-hidden="true" /><div><dt className="text-xs text-ink-500">Vehículo</dt><dd className="font-mono font-medium">{selected.vehiclePlate ?? '—'}</dd></div></div>
                <div className="flex items-center gap-2"><User className="size-4 text-ink-500" aria-hidden="true" /><div><dt className="text-xs text-ink-500">Conductor</dt><dd className="font-medium">{selected.driverName ?? 'Sin asignar'}</dd></div></div>
              </dl>
              <StopTimeline stops={selected.stops ?? []} />
              {perspective === 'CARRIER' && <div className="border-t border-steel-200 pt-4"><AssignDriver key={selected.id} shipment={selected} drivers={drivers} /></div>}
            </CardBody>
          </Card>
        )}
      </div>
    </div>
  );
}
