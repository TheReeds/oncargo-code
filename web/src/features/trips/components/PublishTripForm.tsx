import { useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { toFormErrors, isVerificationFailure, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { DatePicker } from '@/components/ui/date-picker';
import { TimeSelect } from '@/components/ui/time-select';
import { MoneyInput, NumberInput } from '@/components/ui/number-input';
import { RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import type { CityItem, VehicleView } from '@/lib/api/server';
import { formatMoney, formatNumber, VEHICLE_LABELS } from '@/lib/format';
import { Info } from 'lucide-react';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { DescriptionList } from '@/components/ui/description-list';

/** campo de la API → clave de error local */
const FIELD_MAP: Record<string, string> = { originCity: 'origin', destinationCity: 'dest', departureAt: 'date', vehicleId: 'vehicle', capacityKg: 'capacity', rate: 'rate', pricingMode: 'rate', notes: 'notes' };

function todayLima() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
}

/** Valores iniciales al duplicar un viaje (`/transportista/viajes/nuevo?from=<id>`): fecha +7 días, misma hora. */
export type TripInitial = { originCity?: string; destinationCity?: string; date?: string; time?: string; vehicleId?: string; capacityKg?: number; pricingMode?: 'PER_KG' | 'PER_TRIP'; rate?: number; notes?: string };

export default function PublishTripForm({ vehicles, cities, initial }: { vehicles: VehicleView[]; cities: CityItem[]; initial?: TripInitial }) {
  // RN02 por fecha: un vehículo en ruta hoy puede publicar un viaje futuro; solo el mantenimiento lo impide.
  const usable = vehicles.filter((v) => v.status !== 'MAINTENANCE');
  const startVehicle = initial?.vehicleId && usable.some((v) => v.id === initial.vehicleId) ? initial.vehicleId : '';
  const [origin, setOrigin] = useState(initial?.originCity ?? '');
  const [dest, setDest] = useState(initial?.destinationCity ?? '');
  const [date, setDate] = useState(initial?.date ?? '');
  const [time, setTime] = useState(initial?.time ?? '');
  const [vehicleId, setVehicleId] = useState(startVehicle);
  const [capacity, setCapacity] = useState(initial?.capacityKg ? String(initial.capacityKg) : '');
  const [mode, setMode] = useState<'PER_KG' | 'PER_TRIP'>(initial?.pricingMode ?? 'PER_KG');
  const [rate, setRate] = useState(initial?.rate != null ? String(initial.rate) : '');
  const [notes, setNotes] = useState(initial?.notes ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);

  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const vehicle = usable.find((v) => v.id === vehicleId);
  const maxCap = vehicle?.maxWeightKg ?? 0;
  const cap = Number(capacity);
  const rt = Number(rate);
  const clear = (k: string) => setErrors((e) => { const { [k]: _, ...rest } = e; return rest; });

  function pickVehicle(id: string) {
    setVehicleId(id);
    clear('vehicle');
    const v = usable.find((x) => x.id === id);
    if (v?.maxWeightKg && !capacity) setCapacity(String(v.maxWeightKg));
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBlocked(null);
    const errs: Record<string, string> = {};
    if (!origin) errs.origin = 'Selecciona la ciudad de origen.';
    if (!dest) errs.dest = 'Selecciona la ciudad de destino.';
    else if (dest === origin) errs.dest = 'El destino debe ser distinto del origen.';
    if (!date) errs.date = 'Selecciona la fecha de salida.';
    if (!time) errs.time = 'Selecciona la hora de salida.';
    if (date && time && new Date(`${date}T${time}:00-05:00`).getTime() <= Date.now()) errs.date = 'La salida debe ser en el futuro. Elige otra fecha u hora.';
    if (!vehicleId) errs.vehicle = 'Selecciona el vehículo que hará el viaje.';
    if (!(cap > 0)) errs.capacity = 'Ingresa la capacidad que ofreces en kg.';
    else if (maxCap && cap > maxCap) errs.capacity = `Tu vehículo lleva hasta ${formatNumber(maxCap)} kg. Reduce la capacidad que ofreces.`;
    if (!(rt > 0)) errs.rate = mode === 'PER_KG' ? 'Ingresa la tarifa por kg.' : 'Ingresa la tarifa del tramo.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { data, error } = await actions.trips.create({ originCity: origin, destinationCity: dest, departureAt: `${date}T${time}:00-05:00`, vehicleId, capacityKg: cap, pricingMode: mode, rate: rt, notes: notes.trim() || undefined });
    if (error) {
      setBusy(false);
      const r = toFormErrors(error, FIELD_MAP);
      setErrors(r.errors);
      if (isVerificationFailure(r.failure)) setBlocked(r.failure);
      else setError(r.formError);
      return;
    }
    flash('Viaje publicado', 'success');
    window.location.href = data?.id ? `/transportista/viajes/${data.id}` : '/transportista/viajes';
  }

  if (usable.length === 0) {
    return (
      <Alert tone="warning" title="Necesitas un vehículo disponible" action={<Button asChild size="sm" variant="outline"><a href="/transportista/flota">Ir a Flota</a></Button>}>
        Para publicar un viaje, agrega un vehículo a tu flota o sácalo de mantenimiento.
      </Alert>
    );
  }

  const full = mode === 'PER_KG' ? rt * cap : rt;
  const perKg = cap > 0 ? (mode === 'PER_KG' ? rt : rt / cap) : 0;

  return (
    <form method="post" onSubmit={submit} noValidate className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <Card>
        <CardHeader><CardTitle>Datos del viaje</CardTitle></CardHeader>
        <CardBody className="space-y-5">
          {initial && <Alert tone="info" title="Copia de un viaje anterior">Revisa la fecha (una semana después) y la tarifa antes de publicar.</Alert>}
          {error && <Alert tone="error" title="No pudimos publicar el viaje">{error}</Alert>}
          {blocked && <VerificationBlock failure={blocked} />}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Ciudad de origen" htmlFor="t-origin" error={errors.origin} required>
              <Combobox id="t-origin" value={origin} onValueChange={(v) => { setOrigin(v); clear('origin'); }} options={cityOptions} invalid={!!errors.origin} placeholder="Selecciona una ciudad" searchPlaceholder="Buscar ciudad…" />
            </Field>
            <Field label="Ciudad de destino" htmlFor="t-dest" error={errors.dest} required>
              <Combobox id="t-dest" value={dest} onValueChange={(v) => { setDest(v); clear('dest'); }} options={cityOptions.filter((c) => c.value !== origin)} invalid={!!errors.dest} placeholder="Selecciona una ciudad" searchPlaceholder="Buscar ciudad…" />
            </Field>
            <Field label="Fecha de salida" htmlFor="t-date" error={errors.date} required>
              <DatePicker id="t-date" value={date} onValueChange={(v) => { setDate(v); clear('date'); }} min={todayLima()} invalid={!!errors.date} placeholder="Selecciona una fecha" />
            </Field>
            <Field label="Hora de salida" htmlFor="t-time" error={errors.time} required>
              <TimeSelect id="t-time" value={time} onValueChange={(v) => { setTime(v); clear('time'); }} invalid={!!errors.time} placeholder="Selecciona la hora" />
            </Field>
          </div>
          <Field label="Vehículo" htmlFor="t-vehicle" error={errors.vehicle} hint="Un vehículo en ruta hoy puede publicar un viaje para otra fecha; solo el mantenimiento lo impide." required>
            <Combobox id="t-vehicle" value={vehicleId} onValueChange={pickVehicle} invalid={!!errors.vehicle} placeholder="Selecciona un vehículo" searchPlaceholder="Buscar por placa…"
              options={usable.map((v) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!] ?? v.type}`, hint: `${formatNumber(v.maxWeightKg)} kg` }))} />
          </Field>
          <Field label="Capacidad que ofreces" htmlFor="t-capacity" error={errors.capacity} hint={maxCap ? `Máximo ${formatNumber(maxCap)} kg (capacidad del vehículo).` : 'Selecciona un vehículo para ver su capacidad.'} required>
            <NumberInput id="t-capacity" value={capacity} onValueChange={(v) => { setCapacity(v); clear('capacity'); }} suffix="kg" invalid={!!errors.capacity} placeholder="12,000" />
          </Field>

          <fieldset>
            <legend className="mb-1.5 text-[13px] font-medium text-ink-900">Tarifa</legend>
            <RadioGroup value={mode} onValueChange={(v) => { setMode(v as 'PER_KG' | 'PER_TRIP'); clear('rate'); }} className="grid gap-2 sm:grid-cols-2" aria-label="Tipo de tarifa">
              {([['PER_KG', 'Por kg', 'Cada PYME paga según el peso que reserve.'], ['PER_TRIP', 'Por tramo completo', 'Precio del viaje lleno; se prorratea por kg.']] as const).map(([v, t, d]) => (
                <label key={v} htmlFor={`t-mode-${v}`} className={`flex cursor-pointer items-start gap-3 rounded-input border p-3 ${mode === v ? 'border-petrol-700 bg-petrol-50' : 'border-steel-200 hover:border-ink-400'}`}>
                  <RadioGroupItem id={`t-mode-${v}`} value={v} className="mt-0.5" />
                  <span><span className="block text-sm font-medium text-ink-900">{t}</span><span className="block text-xs text-ink-500">{d}</span></span>
                </label>
              ))}
            </RadioGroup>
          </fieldset>
          <Field label={mode === 'PER_KG' ? 'Tarifa por kg' : 'Tarifa del tramo'} htmlFor="t-rate" error={errors.rate} hint={mode === 'PER_KG' ? 'Soles por cada kg reservado. Ej. 0.48' : 'Soles por el viaje con la capacidad completa.'} required>
            <MoneyInput id="t-rate" value={rate} onValueChange={(v) => { setRate(v); clear('rate'); }} invalid={!!errors.rate} placeholder={mode === 'PER_KG' ? '0.48' : '3,200.00'} />
          </Field>
          <Field label="Notas para las PYMEs" htmlFor="t-notes" error={errors.notes} hint={`Opcional. Máximo ${LIMITS.tripNotes} caracteres. Sin teléfonos ni correos.`}><Textarea id="t-notes" value={notes} onChange={(e) => { setNotes(e.target.value); clear('notes'); }} maxLength={LIMITS.tripNotes} aria-invalid={!!errors.notes || undefined} placeholder="Ej. Salimos a las 6 a. m. desde el almacén de Callao." /></Field>
          <div className="flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end">
            <Button asChild variant="outline"><a href="/transportista/viajes">Volver</a></Button>
            <Button type="submit" variant="money" loading={busy}>{busy ? 'Publicando…' : 'Publicar viaje'}</Button>
          </div>
        </CardBody>
      </Card>

      <aside aria-label="Vista previa del ingreso">
        <Card className="sticky top-20">
          <CardHeader><CardTitle>Vista previa</CardTitle></CardHeader>
          <CardBody className="space-y-3 text-sm">
            {rt > 0 && cap > 0 ? (
              <>
                <div><div className="text-xs text-ink-500">Ingreso con el viaje lleno</div><div className="money text-2xl" data-testid="trip-preview-full">{formatMoney(full)}</div></div>
                <div className="grid grid-cols-2 gap-3 border-t border-steel-200 pt-3">
                  <div><div className="text-xs text-ink-500">Equivale a</div><div className="font-mono font-semibold tabular-nums">{formatMoney(perKg)} / kg</div></div>
                  <div><div className="text-xs text-ink-500">Por 1,000 kg</div><div className="font-mono font-semibold tabular-nums">{formatMoney(perKg * 1000)}</div></div>
                </div>
              </>
            ) : (
              <p className="flex gap-2 text-ink-500"><Info className="mt-0.5 size-4 shrink-0" aria-hidden="true" />Ingresa la capacidad y la tarifa para ver cuánto ganarías con el viaje lleno.</p>
            )}
            {(origin || dest || vehicle || date) && (
              <div className="border-t border-steel-200 pt-3">
                <DescriptionList cols={1} className="gap-y-3" items={[
                  { label: 'Ruta', value: origin && dest ? `${cityOptions.find((c) => c.value === origin)?.label} → ${cityOptions.find((c) => c.value === dest)?.label}` : null },
                  { label: 'Salida', value: date ? `${date}${time ? ` · ${time}` : ''}` : null, mono: true },
                  { label: 'Vehículo', value: vehicle ? <><span className="font-mono">{vehicle.plate}</span> · {VEHICLE_LABELS[vehicle.type!] ?? vehicle.type}</> : null },
                ]} />
              </div>
            )}
            {maxCap > 0 && cap > 0 && <div className="border-t border-steel-200 pt-3"><div className="mb-1 text-xs text-ink-500">Parte del vehículo que ofreces</div><CapacityBar used={Math.min(cap, maxCap)} total={maxCap} label="Capacidad ofrecida frente a la capacidad del vehículo" /></div>}
            <p className="border-t border-steel-200 pt-3 text-xs text-ink-500">Las reservas llegan como pendientes. Tú decides cuáles confirmar; al confirmar se genera el envío.</p>
          </CardBody>
        </Card>
      </aside>
    </form>
  );
}
