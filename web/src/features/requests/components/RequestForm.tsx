import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Calculator, ChevronUp, MapPin, PackageOpen } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea, Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DatePicker } from '@/components/ui/date-picker';
import { NumberInput } from '@/components/ui/number-input';
import { flash, toast } from '@/components/ui/toaster';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { RouteLine } from '@/components/domain/RouteLine';
import { formatDate, formatKm, formatMoney } from '@/lib/format';
import { splitActionError } from '@/lib/action-error';
import { cn } from '@/lib/utils';
import type { Quote } from '@/lib/api/server';

type Item = { code?: string; name?: string };
type Values = { originCity: string; originAddress: string; destinationCity: string; destinationAddress: string; cargoType: string; cargoDescription: string; weightKg: string; volumeM3: string; pickupDate: string };
/** `error.field` de la API → id del control del formulario. */
const FIELD_KEYS: Record<string, keyof Values> = { originCity: 'originCity', originAddress: 'originAddress', destinationCity: 'destinationCity', destinationAddress: 'destinationAddress', cargoType: 'cargoType', cargoDescription: 'cargoDescription', weightKg: 'weightKg', volumeM3: 'volumeM3', pickupDate: 'pickupDate' };
/** Orden visual (para enfocar el primer campo inválido). */
const ORDER: (keyof Values)[] = ['originCity', 'originAddress', 'destinationCity', 'destinationAddress', 'cargoType', 'pickupDate', 'weightKg', 'volumeM3', 'cargoDescription'];

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between py-1.5 text-sm">
      <dt className="text-ink-700">{label}</dt>
      <dd className="font-mono tabular-nums text-ink-900">{value}</dd>
    </div>
  );
}

function validate(v: Values): Partial<Record<keyof Values, string>> {
  const e: Partial<Record<keyof Values, string>> = {};
  if (!v.originCity) e.originCity = 'Selecciona la ciudad de origen.';
  if (!v.destinationCity) e.destinationCity = 'Selecciona la ciudad de destino.';
  if (v.originCity && v.originCity === v.destinationCity) e.destinationCity = 'Elige un destino distinto al origen.';
  if (v.originAddress.trim().length < 3) e.originAddress = 'Ingresa la dirección de recojo.';
  if (v.destinationAddress.trim().length < 3) e.destinationAddress = 'Ingresa la dirección de entrega.';
  if (v.cargoDescription.trim().length < 3) e.cargoDescription = 'Describe la carga.';
  if (!(Number(v.weightKg) > 0)) e.weightKg = 'Ingresa el peso en kg.';
  if (!v.pickupDate) e.pickupDate = 'Selecciona la fecha de recojo.';
  return e;
}

export default function RequestForm({ cities, cargoTypes, today }: { cities: Item[]; cargoTypes: Item[]; today: string }) {
  const [v, setV] = useState<Values>({ originCity: '', originAddress: '', destinationCity: '', destinationAddress: '', cargoType: 'GENERAL', cargoDescription: '', weightKg: '', volumeM3: '', pickupDate: '' });
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Partial<Record<keyof Values, string>>>({});
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false); // desglose plegable en móvil
  const seq = useRef(0);

  const set = (k: keyof Values) => (val: string) => { setV((s) => ({ ...s, [k]: val })); setErrors((e) => (e[k] ? { ...e, [k]: '' } : e)); };
  /** Valida un campo al salir de él (no mientras escribe). */
  const touch = (k: keyof Values) => () => setErrors((e) => ({ ...e, [k]: validate(v)[k] ?? '' }));
  const cityOptions = cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const cargoOptions = cargoTypes.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const weight = Number(v.weightKg);
  const sameCity = v.originCity !== '' && v.originCity === v.destinationCity;
  const ready = !!(v.originCity && v.destinationCity && !sameCity && weight > 0);

  // Cotización en vivo con debounce
  useEffect(() => {
    if (!ready) {
      setQuote(null);
      setQuoteError(null);
      setQuoting(false);
      return;
    }
    const my = ++seq.current;
    setQuoting(true);
    const t = setTimeout(async () => {
      const { data, error } = await actions.requests.quote({ originCity: v.originCity, destinationCity: v.destinationCity, weightKg: weight, cargoType: v.cargoType as any });
      if (my !== seq.current) return;
      setQuoting(false);
      if (error) {
        setQuote(null);
        setQuoteError(splitActionError(error).message);
      } else {
        setQuote(data);
        setQuoteError(null);
      }
    }, 400);
    return () => clearTimeout(t);
  }, [v.originCity, v.destinationCity, v.cargoType, weight, ready]);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    const errs = validate(v);
    setErrors(errs);
    const first = ORDER.find((k) => errs[k]);
    if (first) {
      requestAnimationFrame(() => document.getElementById(first)?.focus());
      return;
    }

    setBusy(true);
    const vol = Number(v.volumeM3);
    const { data, error } = await actions.requests.create({
      originCity: v.originCity, originAddress: v.originAddress.trim(), destinationCity: v.destinationCity, destinationAddress: v.destinationAddress.trim(),
      cargoType: v.cargoType as any, cargoDescription: v.cargoDescription.trim(), weightKg: weight, volumeM3: vol > 0 ? vol : undefined, pickupDate: v.pickupDate,
    });
    if (error) {
      // `field` de la API → error bajo el campo; sin campo → Alert + toast.
      const { message, field } = splitActionError(error);
      const key = field ? FIELD_KEYS[field] : undefined;
      if (key) {
        setErrors({ [key]: message });
        requestAnimationFrame(() => document.getElementById(key)?.focus());
      } else {
        setError(message);
        toast.error(message);
      }
      setBusy(false);
      return;
    }
    flash('Solicitud publicada');
    window.location.href = `/pyme/solicitudes/${data.id}`;
  }

  const err = (k: keyof Values) => errors[k] || undefined;
  const cityName = (c: string) => cities.find((x) => x.code === c)?.name ?? c;
  return (
    <form onSubmit={onSubmit} noValidate className="grid items-start gap-5 pb-20 lg:grid-cols-[minmax(0,1fr)_360px] lg:pb-0">
      <div className="min-w-0 space-y-5">
        {error && <Alert tone="error" title="No pudimos publicar la solicitud">{error}</Alert>}
        <Card>
          <CardHeader><CardTitle>Ruta</CardTitle><span className="text-xs text-ink-500">¿De dónde a dónde va tu carga?</span></CardHeader>
          <CardBody>
            <div className="grid gap-x-6 gap-y-5 sm:grid-cols-2">
              <fieldset className="space-y-4">
                <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><span className="size-2.5 rounded-full border-2 border-petrol-700" aria-hidden="true" />Recojo</legend>
                <Field label="Ciudad de origen" htmlFor="originCity" error={err('originCity')}>
                  <Combobox id="originCity" value={v.originCity} onValueChange={set('originCity')} options={cityOptions} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!err('originCity')} />
                </Field>
                <Field label="Dirección de recojo" htmlFor="originAddress" error={err('originAddress')}><Input id="originAddress" name="originAddress" autoComplete="off" value={v.originAddress} onChange={(e) => set('originAddress')(e.target.value)} onBlur={touch('originAddress')} placeholder="Av. Argentina 1234, Callao" aria-invalid={!!err('originAddress')} /></Field>
              </fieldset>
              <fieldset className="space-y-4">
                <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><span className="size-2.5 bg-petrol-700" aria-hidden="true" />Entrega</legend>
                <Field label="Ciudad de destino" htmlFor="destinationCity" error={err('destinationCity')}>
                  <Combobox id="destinationCity" value={v.destinationCity} onValueChange={set('destinationCity')} options={cityOptions.filter((c) => c.value !== v.originCity)} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!err('destinationCity')} />
                </Field>
                <Field label="Dirección de entrega" htmlFor="destinationAddress" error={err('destinationAddress')}><Input id="destinationAddress" name="destinationAddress" autoComplete="off" value={v.destinationAddress} onChange={(e) => set('destinationAddress')(e.target.value)} onBlur={touch('destinationAddress')} placeholder="Jr. Lima 456, Juliaca" aria-invalid={!!err('destinationAddress')} /></Field>
              </fieldset>
            </div>
          </CardBody>
        </Card>

        <Card>
          <CardHeader><CardTitle>Carga</CardTitle><span className="text-xs text-ink-500">Define el vehículo y el precio sugerido</span></CardHeader>
          <CardBody className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tipo de carga" htmlFor="cargoType">
                <Select id="cargoType" value={v.cargoType} onValueChange={set('cargoType')} options={cargoOptions} />
              </Field>
              <Field label="Fecha de recojo" htmlFor="pickupDate" error={err('pickupDate')}><DatePicker id="pickupDate" value={v.pickupDate} onValueChange={set('pickupDate')} min={today} invalid={!!err('pickupDate')} placeholder="Elige el día" /></Field>
              <Field label="Peso" htmlFor="weightKg" error={err('weightKg')}><div onBlur={touch('weightKg')}><NumberInput id="weightKg" value={v.weightKg} onValueChange={set('weightKg')} decimals={1} suffix="kg" placeholder="3,200" invalid={!!err('weightKg')} /></div></Field>
              <Field label="Volumen" htmlFor="volumeM3" hint="Opcional. Ayuda a elegir el vehículo."><NumberInput id="volumeM3" value={v.volumeM3} onValueChange={set('volumeM3')} decimals={1} suffix="m³" placeholder="12" /></Field>
            </div>
            <Field label="Descripción de la carga" htmlFor="cargoDescription" error={err('cargoDescription')}><Textarea id="cargoDescription" name="cargoDescription" value={v.cargoDescription} onChange={(e) => set('cargoDescription')(e.target.value)} onBlur={touch('cargoDescription')} placeholder="Ej.: 120 rollos de tela, paletizados" aria-invalid={!!err('cargoDescription')} /></Field>
          </CardBody>
          <div className="flex flex-wrap items-center justify-end gap-2 border-t border-steel-200 bg-steel-100/50 px-5 py-3.5">
            <Button asChild variant="outline"><a href="/pyme/solicitudes">Volver</a></Button>
            <Button type="submit" loading={busy}>{busy ? 'Publicando…' : 'Publicar solicitud'}</Button>
          </div>
        </Card>
      </div>

      {/* Cotización: panel lateral sticky en escritorio; en móvil, barra inferior con el precio y desglose plegable. */}
      <Card
        className={cn('max-lg:fixed max-lg:inset-x-0 max-lg:bottom-0 max-lg:z-30 max-lg:rounded-b-none max-lg:border-x-0 max-lg:border-b-0 max-lg:shadow-[0_-10px_28px_-14px_rgb(14_23_38/0.4)] lg:sticky lg:top-20')}
        style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
        aria-live="polite"
      >
        <CardHeader className="max-lg:py-3">
          <CardTitle className="flex items-center gap-2"><Calculator className="size-4 text-petrol-700" aria-hidden="true" /> Cotización estimada</CardTitle>
          <div className="flex items-center gap-2 lg:hidden">
            {quoting ? <Skeleton className="h-5 w-20" /> : quote && ready ? <span className="money after:content-[attr(data-price)]" data-price={formatMoney(quote.suggestedPrice)} /> : <span className="text-xs text-ink-500">Completa ruta y peso</span>}
            <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-controls="quote-body" className="grid size-8 place-items-center rounded-input text-ink-700 hover:bg-steel-100">
              <ChevronUp className={cn('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
              <span className="sr-only">{open ? 'Ocultar desglose' : 'Ver desglose'}</span>
            </button>
          </div>
        </CardHeader>
        <CardBody id="quote-body" className={cn('space-y-4', !open && 'max-lg:hidden')}>
          {v.originCity && v.destinationCity && !sameCity && (
            <div className="rounded-input bg-steel-100 p-3">
              <RouteLine size="sm" origin={v.originCity} destination={v.destinationCity} originSub={v.originAddress || undefined} destinationSub={v.destinationAddress || undefined} />
              {v.pickupDate && <p className="mt-2 border-t border-steel-200 pt-2 text-xs text-ink-500">Recojo el <span className="font-medium text-ink-700">{formatDate(v.pickupDate)}</span>{weight > 0 && <> · <span className="font-mono">{new Intl.NumberFormat('es-PE').format(weight)} kg</span></>}</p>}
            </div>
          )}
          {sameCity && <Alert tone="error">El origen y el destino deben ser distintos.</Alert>}
          {!ready && !sameCity && <p className="text-sm text-ink-500">Elige origen, destino y peso para ver el precio sugerido al instante.</p>}
          {quoteError && <Alert tone="error">{quoteError}</Alert>}
          {quoting && !quote && ready && <div className="space-y-2" role="status" aria-label="Calculando cotización">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-5 w-full" />)}</div>}
          {quote && ready && (
            <dl className={cn('transition-opacity', quoting && 'opacity-60')}>
              <Row label="Distancia" value={formatKm(quote.distanceKm)} />
              <Row label="Combustible" value={formatMoney(quote.fuelCost)} />
              <Row label="Transporte" value={formatMoney(quote.transportCost)} />
              <Row label="Peajes" value={formatMoney(quote.tollCost)} />
              <Row label="Manipuleo" value={formatMoney(quote.handlingFee)} />
              <div className="mt-3 rounded-card border border-emerald-500/30 bg-emerald-50 p-3.5">
                <div className="flex items-baseline justify-between gap-3">
                  <dt className="text-sm font-semibold text-ink-900">Precio sugerido</dt>
                  <dd className="font-mono text-2xl font-semibold tabular-nums text-emerald-700">{formatMoney(quote.suggestedPrice)}</dd>
                </div>
                <div className="mt-1.5 flex items-baseline justify-between gap-3 border-t border-emerald-500/20 pt-1.5 text-sm">
                  <dt className="text-ink-700">Piso de subasta</dt>
                  <dd className="font-mono tabular-nums text-ink-900">{formatMoney(quote.priceFloor)}</dd>
                </div>
              </div>
            </dl>
          )}
          {quote && ready && <p className="text-xs text-ink-500">Los transportistas ofertarán por debajo del precio sugerido, nunca por debajo del piso.</p>}
        </CardBody>
      </Card>
    </form>
  );
}
