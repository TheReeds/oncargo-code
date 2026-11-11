import { useState } from 'react';
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
import { CARGO_LABELS, CITY_LABELS, formatDateTime, formatMoney, formatNumber } from '@/lib/format';
import type { TripBookingView } from '../server';

type Tab = 'ACTIVE' | 'HISTORY';

export default function MyBookings({ bookings }: { bookings: TripBookingView[] }) {
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
              <Card className="space-y-3 p-5" data-testid="booking-card">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1"><RouteLine size="sm" origin={b.originCity!} destination={b.destinationCity!} originSub={formatDateTime(b.departureAt)} /></div>
                  <StatusPill kind="booking" status={b.status!} />
                </div>
                <div className="grid gap-3 text-sm sm:grid-cols-[1fr_auto]">
                  <div className="min-w-0 text-ink-700">
                    <div>{CARGO_LABELS[b.cargoType!] ?? b.cargoType} · <span className="font-mono">{formatNumber(b.weightKg)} kg</span> · {b.cargoDescription}</div>
                    <div className="mt-0.5 text-xs text-ink-500">{b.carrierName}</div>
                    {b.decisionNote && <div className="mt-1 text-xs text-ink-500">Nota del transportista: {b.decisionNote}</div>}
                  </div>
                  <div className="sm:text-right"><div className="text-xs text-ink-500">Precio</div><div className="money text-lg">{formatMoney(b.price)}</div></div>
                </div>
                {isActive(b) && (
                  <div className="flex flex-wrap items-center justify-between gap-2 border-t border-steel-200 pt-3">
                    <span className="text-xs text-ink-500">{b.status === 'PENDING' ? 'Esperando la confirmación del transportista.' : 'Reserva confirmada: ya tienes un envío.'}</span>
                    <div className="flex gap-2">
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
