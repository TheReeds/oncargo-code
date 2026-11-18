import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { toFormErrors, isVerificationFailure, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Input, Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { NumberInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import { RouteLine } from '@/components/domain/RouteLine';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { TrustScore } from '@/components/domain/TrustScore';
import { CARGO_LABELS, estimateBookingPrice, formatDateTime, formatMoney, formatNumber, formatTripRate, VEHICLE_LABELS } from '@/lib/format';
import type { TripView } from '../server';

const CARGO_OPTIONS = Object.entries(CARGO_LABELS).map(([value, label]) => ({ value, label }));
type CargoType = 'GENERAL' | 'FRAGILE' | 'PERISHABLE' | 'HAZARDOUS' | 'BULK' | 'OVERSIZED';
const FIELD_MAP: Record<string, string> = { weightKg: 'weight', cargoType: 'cargo', cargoDescription: 'desc', pickupAddress: 'pickup', deliveryAddress: 'delivery' };

export default function BookTripSheet({ trip, onClose, defaultWeight }: { trip: TripView | null; onClose: () => void; defaultWeight?: string }) {
  const [weight, setWeight] = useState('');
  const [cargo, setCargo] = useState('GENERAL');
  const [desc, setDesc] = useState('');
  const [pickup, setPickup] = useState('');
  const [delivery, setDelivery] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setWeight(defaultWeight ?? ''); setCargo('GENERAL'); setDesc(''); setPickup(''); setDelivery(''); setErrors({}); setError(null); setBlocked(null); setBusy(false);
  }, [trip?.id]);

  const kg = Number(weight);
  const remaining = trip?.remainingKg ?? 0;
  const price = trip && kg > 0 ? estimateBookingPrice(trip.pricingMode, trip.rate ?? 0, kg, trip.capacityKg ?? 0) : null;
  const clear = (k: string) => setErrors((e) => { const { [k]: _, ...rest } = e; return rest; });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!trip) return;
    setError(null);
    setBlocked(null);
    const errs: Record<string, string> = {};
    if (!(kg > 0)) errs.weight = 'Ingresa el peso que quieres enviar en kg.';
    else if (kg > remaining) errs.weight = `Solo quedan ${formatNumber(remaining)} kg libres en este viaje. Reduce el peso.`;
    if (!cargo) errs.cargo = 'Selecciona el tipo de carga.';
    if (!desc.trim()) errs.desc = 'Describe brevemente tu carga.';
    if (!pickup.trim()) errs.pickup = 'Ingresa la dirección de recojo.';
    if (!delivery.trim()) errs.delivery = 'Ingresa la dirección de entrega.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { error } = await actions.trips.book({ tripId: trip.id!, weightKg: kg, cargoType: cargo as CargoType, cargoDescription: desc.trim(), pickupAddress: pickup.trim(), deliveryAddress: delivery.trim() });
    if (error) {
      setBusy(false);
      const r = toFormErrors(error, FIELD_MAP);
      setErrors(r.errors);
      if (isVerificationFailure(r.failure)) setBlocked(r.failure);
      else setError(r.formError);
      const first = Object.keys(r.errors)[0];
      if (first) requestAnimationFrame(() => document.getElementById(`bk-${first}`)?.focus());
      return;
    }
    flash('Reserva enviada', 'success');
    window.location.href = '/pyme/reservas';
  }

  return (
    <Sheet open={!!trip} onOpenChange={(o) => !o && !busy && onClose()} title="Reservar espacio" description="Reservas una parte del vehículo. El transportista confirma y recién ahí se genera tu envío.">
      {trip && (
        <form method="post" onSubmit={submit} noValidate className="space-y-5 p-5">
          <div className="space-y-3 rounded-card bg-steel-100 p-4">
            <RouteLine origin={trip.originCity!} destination={trip.destinationCity!} />
            <div className="text-sm text-ink-700">Sale el <strong className="font-medium">{formatDateTime(trip.departureAt)}</strong> · {VEHICLE_LABELS[trip.vehicleType!] ?? trip.vehicleType}</div>
            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-steel-200 pt-3 text-sm"><span className="text-ink-700">{trip.carrierName}</span><TrustScore score={trip.carrierTrustScore} variant="meter" className="w-36" /></div>
            <div className="grid grid-cols-2 gap-3 border-t border-steel-200 pt-3 text-sm">
              <div><div className="text-xs text-ink-500">Tarifa</div><div className="money">{formatTripRate(trip.pricingMode, trip.rate)}</div></div>
              <div><CapacityBar used={trip.confirmedWeightKg ?? 0} total={trip.capacityKg ?? 0} label="Ocupación del viaje" /></div>
            </div>
          </div>

          {error && <Alert tone="error" title="No pudimos enviar tu reserva">{error}</Alert>}
          {blocked && <VerificationBlock failure={blocked} />}

          <Field label="Peso a enviar" htmlFor="bk-weight" error={errors.weight} hint={`Hasta ${formatNumber(remaining)} kg disponibles.`} required>
            <NumberInput id="bk-weight" value={weight} onValueChange={(v) => { setWeight(v); clear('weight'); }} suffix="kg" invalid={!!errors.weight} placeholder="2,500" />
          </Field>
          <div className="rounded-card border border-emerald-500/30 bg-emerald-50 p-4" aria-live="polite">
            <div className="text-xs text-ink-700">Precio estimado</div>
            <div className="money text-2xl" data-testid="booking-price">{price != null ? formatMoney(price) : '—'}</div>
            <div className="text-xs text-ink-500">{price != null ? 'Es el precio de tu reserva; lo confirma el transportista.' : 'Ingresa el peso para ver cuánto pagarías.'}</div>
          </div>
          <Field label="Tipo de carga" htmlFor="bk-cargo" error={errors.cargo} required>
            <Select id="bk-cargo" value={cargo} onValueChange={(v) => { setCargo(v); clear('cargo'); }} options={CARGO_OPTIONS} invalid={!!errors.cargo} />
          </Field>
          <Field label="Descripción de la carga" htmlFor="bk-desc" error={errors.desc} required hint={`Hasta ${LIMITS.bookingCargoDescription} caracteres. Sin teléfonos ni correos.`}>
            <Textarea id="bk-desc" value={desc} onChange={(e) => { setDesc(e.target.value); clear('desc'); }} maxLength={LIMITS.bookingCargoDescription} aria-invalid={!!errors.desc || undefined} placeholder="Ej. Mercadería de bazar en cajas" />
          </Field>
          <Field label="Dirección de recojo" htmlFor="bk-pickup" error={errors.pickup} required>
            <Input id="bk-pickup" value={pickup} onChange={(e) => { setPickup(e.target.value); clear('pickup'); }} maxLength={LIMITS.bookingAddress} aria-invalid={!!errors.pickup || undefined} placeholder="Av. Argentina 1234, Callao" />
          </Field>
          <Field label="Dirección de entrega" htmlFor="bk-delivery" error={errors.delivery} required>
            <Input id="bk-delivery" value={delivery} onChange={(e) => { setDelivery(e.target.value); clear('delivery'); }} maxLength={LIMITS.bookingAddress} aria-invalid={!!errors.delivery || undefined} placeholder="Jr. San Román 456, Juliaca" />
          </Field>
          <Button type="submit" variant="money" size="lg" className="w-full" loading={busy}>{busy ? 'Enviando reserva…' : 'Reservar espacio'}</Button>
        </form>
      )}
    </Sheet>
  );
}
