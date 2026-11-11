import { useState } from 'react';
import { actions } from 'astro:actions';
import { useLiveRefresh } from '@/lib/realtime';
import { splitActionError } from '@/lib/action-error';
import { Ban, Check, Lock, X } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Dialog } from '@/components/ui/dialog';
import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { EmptyState } from '@/components/domain/EmptyState';
import { CARGO_LABELS, CITY_LABELS, formatDateTime, formatMoney, formatNumber, formatTripRate, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { TripBookingView, TripView } from '../server';

function RejectDialog({ booking, onClose }: { booking: TripBookingView | null; onClose: () => void }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!booking) return;
    setBusy(true);
    setError(null);
    const { error } = await actions.trips.rejectBooking({ id: booking.id!, reason: reason.trim() || undefined });
    if (error) { setBusy(false); setError(splitActionError(error).message); return; }
    flash('Reserva rechazada');
    window.location.reload();
  }
  return (
    <Dialog open={!!booking} onOpenChange={(o) => !o && !busy && onClose()} title="¿Rechazar esta reserva?" description={booking ? `${booking.requesterName} pidió ${formatNumber(booking.weightKg)} kg. La empresa recibirá un aviso y no se puede deshacer.` : undefined}
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="reject-form" variant="destructive-solid" loading={busy}>{busy ? 'Rechazando…' : 'Rechazar reserva'}</Button></>}>
      <form id="reject-form" onSubmit={submit} className="space-y-3">
        {error && <Alert tone="error" title="No pudimos rechazar la reserva">{error}</Alert>}
        <Field label="Motivo" htmlFor="reject-reason" hint="Opcional. La empresa lo verá. Máximo 300 caracteres."><Textarea id="reject-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} placeholder="Ej. Ya completamos la capacidad con otra carga." /></Field>
      </form>
    </Dialog>
  );
}

export default function TripDetail({ trip: initialTrip, bookings: initialBookings }: { trip: TripView; bookings: TripBookingView[] }) {
  const [trip, setTrip] = useState(initialTrip);
  const [bookings, setBookings] = useState(initialBookings);
  useLiveRefresh({
    types: ['trip_booking.updated', 'trip.cancelled'],
    match: (e) => e.data.tripId === initialTrip.id,
    refresh: async () => { const { data } = await actions.trips.detail({ id: initialTrip.id! }); if (data) { setTrip(data.trip); setBookings(data.bookings); } },
    fallbackMs: 60_000,
  });
  const [busy, setBusy] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<TripBookingView | null>(null);
  const active = trip.status === 'OPEN' || trip.status === 'FILLING';
  const canCancel = active || trip.status === 'CLOSED';
  const pending = bookings.filter((b) => b.status === 'PENDING');
  const rest = bookings.filter((b) => b.status !== 'PENDING');

  async function run(key: string, fn: () => Promise<{ error?: { message: string } }>, ok: string) {
    setBusy(key);
    const { error } = await fn();
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash(ok, 'success');
    window.location.reload();
  }

  async function onConfirm(b: TripBookingView) {
    const ok = await confirm({ title: `¿Confirmar la reserva de ${formatNumber(b.weightKg)} kg?`, description: `${b.requesterName} pagará ${formatMoney(b.price)}. Al confirmar se genera el envío y se descuenta el espacio de tu viaje.`, confirmLabel: 'Confirmar reserva' });
    if (ok) await run(`c-${b.id}`, () => actions.trips.confirmBooking({ id: b.id! }) as never, 'Reserva confirmada');
  }
  async function onClose() {
    const ok = await confirm({ title: '¿Cerrar las reservas de este viaje?', description: pending.length ? `Se rechazarán ${pending.length} reserva${pending.length === 1 ? '' : 's'} pendiente${pending.length === 1 ? '' : 's'} y no se aceptarán nuevas. Las confirmadas se mantienen.` : 'No se aceptarán nuevas reservas. Las confirmadas se mantienen.', confirmLabel: 'Cerrar reservas', tone: 'destructive' });
    if (ok) await run('close', () => actions.trips.close({ id: trip.id! }) as never, 'Reservas cerradas');
  }
  async function onCancel() {
    const ok = await confirm({ title: '¿Cancelar este viaje?', description: 'Se cancelarán las reservas y los envíos que aún no salieron, y avisaremos a las empresas. No se puede deshacer.', confirmLabel: 'Cancelar viaje', tone: 'destructive' });
    if (ok) await run('cancel', () => actions.trips.cancel({ id: trip.id! }) as never, 'Viaje cancelado');
  }

  const used = trip.confirmedWeightKg ?? 0;
  const BookingCard = ({ b }: { b: TripBookingView }) => (
    <li className="space-y-3 px-5 py-4" data-testid="booking-row">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><span className="font-medium text-ink-900">{b.requesterName}</span><StatusPill kind="booking" status={b.status!} /></div>
          <div className="mt-0.5 text-sm text-ink-700">{CARGO_LABELS[b.cargoType!] ?? b.cargoType} · <span className="font-mono">{formatNumber(b.weightKg)} kg</span> · {b.cargoDescription}</div>
        </div>
        <div className="text-right"><div className="money text-lg">{formatMoney(b.price)}</div><div className="text-xs text-ink-500">Pedida {formatDateTime(b.createdAt)}</div></div>
      </div>
      <dl className="grid gap-x-6 gap-y-1 text-xs text-ink-500 sm:grid-cols-2">
        <div><dt className="inline font-medium text-ink-700">Recojo: </dt><dd className="inline">{b.pickupAddress}</dd></div>
        <div><dt className="inline font-medium text-ink-700">Entrega: </dt><dd className="inline">{b.deliveryAddress}</dd></div>
      </dl>
      {b.decisionNote && <p className="text-xs text-ink-500">Nota: {b.decisionNote}</p>}
      {b.status === 'PENDING' && active && (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="money" loading={busy === `c-${b.id}`} disabled={!!busy} onClick={() => onConfirm(b)}><Check />Confirmar reserva<span className="sr-only"> de {b.requesterName}</span></Button>
          <Button size="sm" variant="destructive" disabled={!!busy} onClick={() => setRejecting(b)}><X />Rechazar reserva<span className="sr-only"> de {b.requesterName}</span></Button>
        </div>
      )}
      {b.status === 'CONFIRMED' && b.shipmentId && <Button asChild size="sm" variant="outline"><a href={`/transportista/envios?id=${b.shipmentId}`}>Ver envío</a></Button>}
    </li>
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardBody className="space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2"><StatusPill kind="trip" status={trip.status!} /><span className="text-sm text-ink-700">Sale el <strong className="font-medium">{formatDateTime(trip.departureAt)}</strong></span></div>
            {canCancel && (
              <div className="flex flex-wrap gap-2">
                {active && <Button variant="outline" size="sm" loading={busy === 'close'} disabled={!!busy} onClick={onClose}><Lock />Cerrar reservas</Button>}
                <Button variant="destructive" size="sm" loading={busy === 'cancel'} disabled={!!busy} onClick={onCancel}><Ban />Cancelar viaje</Button>
              </div>
            )}
          </div>
          <RouteLine origin={trip.originCity!} destination={trip.destinationCity!} />
          <div className="grid gap-5 border-t border-steel-200 pt-4 sm:grid-cols-3">
            <div><div className="text-xs text-ink-500">Vehículo</div><div className="font-medium"><span className="font-mono">{trip.vehiclePlate}</span> · {VEHICLE_LABELS[trip.vehicleType!] ?? trip.vehicleType}</div></div>
            <div><div className="text-xs text-ink-500">Tarifa</div><div className="money">{formatTripRate(trip.pricingMode, trip.rate)}</div></div>
            <div>
              <div className="mb-1 text-xs text-ink-500">Ocupación confirmada</div>
              <CapacityBar used={used} total={trip.capacityKg ?? 0} label="Ocupación confirmada del viaje" />
              <div className="mt-0.5 text-xs text-ink-500">Quedan <span className="font-mono">{formatTons(trip.remainingKg)}</span> libres</div>
            </div>
          </div>
          {trip.notes && <p className="border-t border-steel-200 pt-3 text-sm text-ink-700">{trip.notes}</p>}
        </CardBody>
      </Card>

      <Card>
        <CardHeader><CardTitle>Reservas</CardTitle><span className="text-sm text-ink-500">{pending.length} por confirmar · {trip.confirmedBookings ?? 0} confirmada{trip.confirmedBookings === 1 ? '' : 's'}</span></CardHeader>
        {bookings.length === 0 ? (
          <EmptyState title="Aún no hay reservas" description="Las empresas que busquen espacio en esta ruta podrán reservar. Te avisaremos apenas llegue una." />
        ) : (
          <ul className="divide-y divide-steel-200">
            {[...pending, ...rest].map((b) => <BookingCard key={b.id} b={b} />)}
          </ul>
        )}
      </Card>
      <RejectDialog booking={rejecting} onClose={() => setRejecting(null)} />
    </div>
  );
}
