import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { useState } from 'react';
import { actions } from 'astro:actions';
import { useLiveRefresh } from '@/lib/realtime';
import { splitActionError } from '@/lib/action-error';
import { Ban, Check, Lock, MapPin, X } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { LIMITS } from '@/lib/limits';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { EmptyState } from '@/components/domain/EmptyState';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { DescriptionList } from '@/components/ui/description-list';
import { cn } from '@/lib/utils';
import { CARGO_LABELS, formatDateTime, formatMoney, formatNumber, formatTripRate, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { TripBookingView, TripView } from '../server';

function RejectDialog({ booking, onClose }: { booking: TripBookingView | null; onClose: () => void }) {
  return (
    <ReasonDialog
      open={!!booking} onOpenChange={(o) => !o && onClose()} title="¿Rechazar esta reserva?"
      description={booking ? `${booking.requesterName} pidió ${formatNumber(booking.weightKg)} kg. La empresa recibirá un aviso y no se puede deshacer.` : undefined}
      label="Motivo" hint={`Opcional. La empresa lo verá. Máximo ${LIMITS.bookingRejectReason} caracteres. Sin teléfonos ni correos.`}
      placeholder="Ej. Ya completamos la capacidad con otra carga." maxLength={LIMITS.bookingRejectReason} confirmLabel="Rechazar reserva" tone="destructive" rows={3}
      onSubmit={async (reason) => {
        const { error } = await actions.trips.rejectBooking({ id: booking!.id!, reason: reason || undefined });
        if (error) return splitActionError(error);
        flash('Reserva rechazada');
        window.location.reload();
        return null;
      }}
    />
  );
}

export default function TripDetail({ trip: initialTrip, bookings: initialBookings }: { trip: TripView; bookings: TripBookingView[] }) {
  const [trip, setTrip] = useState(initialTrip);
  const canOperate = useCan('OPERATE');
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
  const income = bookings.filter((b) => b.status === 'CONFIRMED').reduce((n, b) => n + (b.price ?? 0), 0);
  const pendingKg = pending.reduce((n, b) => n + (b.weightKg ?? 0), 0);
  const BookingCard = ({ b }: { b: TripBookingView }) => (
    <li className={cn('space-y-3 border-l-2 px-5 py-4', b.status === 'PENDING' ? 'border-amber-500 bg-amber-50/40' : 'border-transparent')} data-testid="booking-row">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2"><span className="font-medium text-ink-900">{b.requesterName}</span><StatusPill kind="booking" status={b.status!} /></div>
          <div className="mt-0.5 text-sm text-ink-700">{CARGO_LABELS[b.cargoType!] ?? b.cargoType} · <span className="font-mono">{formatNumber(b.weightKg)} kg</span> · {b.cargoDescription}</div>
        </div>
        <div className="text-right"><div className="money text-xl">{formatMoney(b.price)}</div><div className="text-xs text-ink-500">Pedida {formatDateTime(b.createdAt)}</div></div>
      </div>
      <ul className="grid gap-x-6 gap-y-1.5 text-xs sm:grid-cols-2">
        <li className="flex gap-1.5"><MapPin className="mt-0.5 size-3.5 shrink-0 text-petrol-700" aria-hidden="true" /><span><span className="font-medium text-ink-700">Recojo: </span><span className="text-ink-500">{b.pickupAddress}</span></span></li>
        <li className="flex gap-1.5"><MapPin className="mt-0.5 size-3.5 shrink-0 text-emerald-700" aria-hidden="true" /><span><span className="font-medium text-ink-700">Entrega: </span><span className="text-ink-500">{b.deliveryAddress}</span></span></li>
      </ul>
      {b.decisionNote && <p className="text-xs text-ink-500">Nota: {b.decisionNote}</p>}
      {b.status === 'PENDING' && active && canOperate && (
        <div className="flex flex-wrap gap-2 max-sm:[&>*]:flex-1">
          <Button size="sm" variant="money" loading={busy === `c-${b.id}`} disabled={!!busy} onClick={() => onConfirm(b)}><Check />Confirmar reserva<span className="sr-only"> de {b.requesterName}</span></Button>
          <Button size="sm" variant="destructive" disabled={!!busy} onClick={() => setRejecting(b)}><X />Rechazar reserva<span className="sr-only"> de {b.requesterName}</span></Button>
        </div>
      )}
      {b.status === 'CONFIRMED' && b.shipmentId && <Button asChild size="sm" variant="outline"><a href={`/transportista/envios?id=${b.shipmentId}`}>Ver envío</a></Button>}
    </li>
  );

  return (
    <div className="space-y-6">
      <ReadOnlyNotice className="mb-3">No puedes confirmar ni rechazar reservas ni cancelar el viaje.</ReadOnlyNotice>
      <KpiGrid cols={3}>
        <div className="col-span-2 lg:col-span-1"><KpiTile label="Ingreso confirmado" value={formatMoney(income)} context={`${trip.confirmedBookings ?? 0} reserva${trip.confirmedBookings === 1 ? '' : 's'} confirmada${trip.confirmedBookings === 1 ? '' : 's'}`} tone="money" /></div>
        <KpiTile label="Por confirmar" value={pending.length} context={pending.length ? `${formatTons(pendingKg)} esperan tu respuesta` : 'todo al día'} tone={pending.length ? 'warn' : 'default'} />
        <KpiTile label="Libre" value={formatTons(trip.remainingKg)} context="por vender" tone={(trip.remainingKg ?? 0) > 0 ? 'money' : 'default'} />
      </KpiGrid>

      <Card>
        <CardBody className="space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2"><StatusPill kind="trip" status={trip.status!} /><span className="text-sm text-ink-700">Sale el <strong className="font-medium">{formatDateTime(trip.departureAt)}</strong></span></div>
            {canCancel && canOperate && (
              <div className="flex flex-wrap gap-2">
                {active && <Button variant="outline" size="sm" loading={busy === 'close'} disabled={!!busy} onClick={onClose}><Lock />Cerrar reservas</Button>}
                <Button variant="destructive" size="sm" loading={busy === 'cancel'} disabled={!!busy} onClick={onCancel}><Ban />Cancelar viaje</Button>
              </div>
            )}
          </div>
          <RouteLine origin={trip.originCity!} destination={trip.destinationCity!} />
          <div className="grid gap-5 border-t border-steel-200 pt-4 lg:grid-cols-[1fr_1fr]">
            <DescriptionList cols={1} items={[
              { label: 'Vehículo', value: <><span className="font-mono">{trip.vehiclePlate}</span> · {VEHICLE_LABELS[trip.vehicleType!] ?? trip.vehicleType}</> },
              { label: 'Tarifa', value: formatTripRate(trip.pricingMode, trip.rate), money: true },
              { label: 'Notas para las PYMEs', value: trip.notes },
            ]} />
            <div>
              <div className="mb-1 text-xs font-medium text-ink-500">Ocupación confirmada</div>
              <CapacityBar used={used} total={trip.capacityKg ?? 0} label="Ocupación confirmada del viaje" />
              <div className="mt-0.5 text-xs text-ink-500">Quedan <span className="font-mono">{formatTons(trip.remainingKg)}</span> libres</div>
            </div>
          </div>
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
