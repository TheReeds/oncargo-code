import { useMemo, useState } from 'react';
import { CalendarClock, Plus, SearchX } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { CITY_LABELS, formatDateTime, formatTripRate, VEHICLE_LABELS } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { TripView } from '../server';

type Filter = '' | 'active' | 'pending' | 'closed';
const isActive = (t: TripView) => t.status === 'OPEN' || t.status === 'FILLING';

/** Mis viajes publicados: filtros rápidos por estado y tabla que pasa a tarjetas en móvil. */
export default function MyTripsTable({ trips }: { trips: TripView[] }) {
  const [filter, setFilter] = useState<Filter>('');
  const counts = useMemo(() => ({ active: trips.filter(isActive).length, pending: trips.filter((t) => (t.pendingBookings ?? 0) > 0).length, closed: trips.filter((t) => !isActive(t)).length }), [trips]);
  const shown = [...trips].sort((a, b) => Number(isActive(b)) - Number(isActive(a))).filter((t) => (filter === 'active' ? isActive(t) : filter === 'pending' ? (t.pendingBookings ?? 0) > 0 : filter === 'closed' ? !isActive(t) : true));

  return (
    <Card>
      {trips.length === 0 ? (
        <EmptyState icon={CalendarClock} title="Aún no publicaste viajes" description="Publica una salida con tu capacidad libre y recibe reservas de empresas que ya viajan por tu ruta." action={<Button asChild variant="money"><a href="/transportista/viajes/nuevo"><Plus />Publicar viaje</a></Button>} />
      ) : (
        <>
          <div className="border-b border-steel-200 px-4 py-3 sm:px-5">
            <QuickFilters label="Estado del viaje" value={filter} onValueChange={(v) => setFilter(v as Filter)} options={[
              { value: '', label: 'Todos', count: trips.length },
              { value: 'active', label: 'Abiertos', count: counts.active },
              { value: 'pending', label: 'Con reservas por confirmar', count: counts.pending },
              { value: 'closed', label: 'Cerrados', count: counts.closed },
            ]} />
          </div>
          {shown.length === 0 ? (
            <EmptyState compact icon={SearchX} title="No hay viajes con este filtro" description="Cambia el filtro para ver el resto de tus viajes." action={<Button size="sm" variant="outline" onClick={() => setFilter('')}>Ver todos los viajes</Button>} />
          ) : (
            <Table label="Viajes publicados" stack className="[&_td]:px-3 [&_th]:px-3 sm:[&_td:first-child]:pl-5 sm:[&_th:first-child]:pl-5 sm:[&_td:last-child]:pr-5">
              <thead><tr><Th>Ruta</Th><Th>Vehículo</Th><Th>Tarifa</Th><Th>Ocupación</Th><Th>Reservas</Th><Th>Estado</Th><Th><span className="sr-only">Acción</span></Th></tr></thead>
              <tbody>
                {shown.map((t) => {
                  const route = `${CITY_LABELS[t.originCity!]} a ${CITY_LABELS[t.destinationCity!]}`;
                  const pend = t.pendingBookings ?? 0;
                  return (
                    <Tr key={t.id} className={cn(pend > 0 && 'bg-amber-50/40')}>
                      <Td primary className="min-w-0"><div className="font-medium">{CITY_LABELS[t.originCity!]} <span className="text-ink-500" aria-hidden="true">→</span><span className="sr-only">a</span> {CITY_LABELS[t.destinationCity!]}</div><div className="text-xs text-ink-700">Sale {formatDateTime(t.departureAt)}</div></Td>
                      <Td label="Vehículo"><span><span className="font-mono">{t.vehiclePlate}</span><span className="ml-1.5 text-xs text-ink-500">{VEHICLE_LABELS[t.vehicleType!] ?? t.vehicleType}</span></span></Td>
                      <Td label="Tarifa" className="whitespace-nowrap"><span className="money">{formatTripRate(t.pricingMode, t.rate)}</span></Td>
                      <Td label="Ocupación" className="min-w-40 @max-[720px]:[&>*]:w-40"><CapacityBar used={t.confirmedWeightKg ?? 0} total={t.capacityKg ?? 0} label={`Ocupación del viaje de ${route}`} /></Td>
                      <Td label="Reservas" className="whitespace-nowrap text-sm">
                        <span className="@max-[720px]:text-right">
                          <span className="font-mono tabular-nums">{t.confirmedBookings ?? 0}</span> confirmada{t.confirmedBookings === 1 ? '' : 's'}
                          {pend > 0 && <span className="block text-xs font-medium text-amber-700"><span className="font-mono">{pend}</span> por confirmar</span>}
                        </span>
                      </Td>
                      <Td label="Estado"><StatusPill kind="trip" status={t.status!} /></Td>
                      <Td actions className="whitespace-nowrap text-right"><Button asChild size="sm" variant={pend > 0 ? 'money' : 'outline'}><a href={`/transportista/viajes/${t.id}`}>{pend > 0 ? 'Responder reservas' : 'Ver detalle'}<span className="sr-only"> del viaje de {route}</span></a></Button></Td>
                    </Tr>
                  );
                })}
              </tbody>
            </Table>
          )}
        </>
      )}
    </Card>
  );
}
