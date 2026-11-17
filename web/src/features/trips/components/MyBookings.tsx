import { useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { EmptyState } from '@/components/domain/EmptyState';
import { Timeline } from '@/components/ui/timeline';
import { DescriptionList } from '@/components/ui/description-list';
import { CARGO_LABELS, formatDateTime, formatMoney, formatNumber } from '@/lib/format';
import type { TripBookingView } from '../server';

type Tab = 'ACTIVE' | 'HISTORY';

export default function MyBookings({ bookings: initial }: { bookings: TripBookingView[] }) {
  const [bookings, setBookings] = useState(initial);
  useLiveRefresh({
    types: ['trip_booking.updated', 'trip.cancelled'],
    refresh: async () => { const { data } = await actions.trips.myBookings(); if (data) setBookings(data); },
    fallbackMs: 60_000,
  });
  const [tab, setTab] = useState<Tab>('ACTIVE');
  const [busy, setBusy] = useState<string | null>(null);
  const isActive = (b: TripBookingView) => b.status === 'PENDING' || b.status === 'CONFIRMED';
  const shown = bookings.filter((b) => (tab === 'ACTIVE') === isActive(b));

  async function cancel(b: TripBookingView) {
    const ok = await confirm({ title: '¿Cancelar esta reserva?', description: b.status === 'CONFIRMED' ? 'Se cancelará también el envío y el espacio volverá al transportista. No se puede deshacer.' : 'El transportista dejará de verla. No se puede deshacer.', confirmLabel: 'Cancelar reserva', tone: 'destructive' });
    if (!ok) return;
    setBusy(b.id!);
    const { error } = await actions.trips.cancelBooking({ id: b.id! });
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash('Reserva cancelada');
    window.location.reload();
  }

  return (
    <div className="space-y-4">
      <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
        <TabsList aria-label="Estado de las reservas">
          <TabsTrigger value="ACTIVE" aria-controls={undefined}>Activas <span className="ml-1 font-mono text-xs">{bookings.filter(isActive).length}</span></TabsTrigger>
          <TabsTrigger value="HISTORY" aria-controls={undefined}>Historial <span className="ml-1 font-mono text-xs">{bookings.filter((b) => !isActive(b)).length}</span></TabsTrigger>
        </TabsList>
      </Tabs>
      {shown.length === 0 ? (
        <Card>
          {tab === 'ACTIVE'
            ? <EmptyState title="Aún no tienes reservas activas" description="Busca un viaje con espacio libre en tu ruta y reserva solo el peso que necesitas." action={<Button asChild variant="money"><a href="/pyme/viajes">Buscar viajes</a></Button>} />
            : <EmptyState title="Sin historial todavía" description="Aquí verás las reservas canceladas o rechazadas." />}
        </Card>
      ) : (
        <ul className="space-y-3" aria-label="Mis reservas">
          {shown.map((b) => (
            <li key={b.id}>
              <Card className="overflow-hidden" data-testid="booking-card">
                <div className="space-y-4 p-4 sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1"><RouteLine size="sm" origin={b.originCity!} destination={b.destinationCity!} originSub={formatDateTime(b.departureAt)} /></div>
                    <div className="flex items-center gap-3">
                      <div className="text-right"><div className="text-xs text-ink-500">Precio</div><div className="money whitespace-nowrap text-lg">{formatMoney(b.price)}</div></div>
                      <StatusPill kind="booking" status={b.status!} />
                    </div>
                  </div>
                  <DescriptionList cols={3} items={[
                    { label: 'Carga', value: `${CARGO_LABELS[b.cargoType!] ?? b.cargoType} · ${b.cargoDescription}` },
                    { label: 'Peso reservado', value: `${formatNumber(b.weightKg)} kg`, mono: true },
                    { label: 'Transportista', value: b.carrierName },
                    { label: 'Nota del transportista', value: b.decisionNote, span: true },
                  ]} />
                  {isActive(b) && (
                    <Timeline
                      label="Avance de la reserva"
                      items={[
                        { id: 'sent', title: 'Reserva enviada', meta: formatDateTime(b.createdAt), status: 'done' },
                        { id: 'ok', title: 'Aceptada por el transportista', meta: b.decidedAt ? formatDateTime(b.decidedAt) : undefined, status: b.status === 'CONFIRMED' ? 'done' : 'current' },
                        { id: 'ship', title: 'Envío generado', status: b.status === 'CONFIRMED' && b.shipmentId ? 'done' : 'pending' },
                      ]}
                    />
                  )}
                </div>
                {isActive(b) && (
                  <div className="flex flex-wrap items-center justify-between gap-2 border-t border-steel-200 bg-steel-100/50 px-4 py-3 sm:px-5">
                    <span className="text-xs text-ink-500">{b.status === 'PENDING' ? 'Esperando la confirmación del transportista.' : 'Reserva confirmada: ya tienes un envío.'}</span>
                    <div className="flex gap-2 max-sm:w-full [&>*]:max-sm:flex-1">
                      {b.status === 'CONFIRMED' && b.shipmentId && <Button asChild size="sm" variant="outline"><a href={`/pyme/envios?id=${b.shipmentId}`}>Ver envío</a></Button>}
                      <Button size="sm" variant="destructive" loading={busy === b.id} disabled={!!busy} onClick={() => cancel(b)}>Cancelar reserva<span className="sr-only"> de {b.originCity} a {b.destinationCity}</span></Button>
                    </div>
                  </div>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
