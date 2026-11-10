import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Calculator, Loader2, MapPin, PackageOpen } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Textarea, Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DatePicker } from '@/components/ui/date-picker';
import { NumberInput } from '@/components/ui/number-input';
import { flash, toast } from '@/components/ui/toaster';
import { Alert } from '@/components/ui/alert';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { formatKm, formatMoney } from '@/lib/format';
import { splitActionError } from '@/lib/action-error';
import type { Quote } from '@/lib/api/server';

type Item = { code?: string; name?: string };
/** `error.field` de la API → id del control del formulario. */
const FIELD_KEYS: Record<string, string> = { originCity: 'originCity', originAddress: 'originAddress', destinationCity: 'destinationCity', destinationAddress: 'destinationAddress', cargoType: 'cargoType', cargoDescription: 'cargoDescription', weightKg: 'weightKg', volumeM3: 'volumeM3', pickupDate: 'pickupDate' };

function Row({ label, value, strong, tone }: { label: string; value: string; strong?: boolean; tone?: 'money' }) {
  return (
    <div className={`flex items-center justify-between py-1.5 text-sm ${strong ? 'border-t border-steel-200 pt-2.5 font-semibold' : ''}`}>
      <dt className={strong ? '' : 'text-ink-700'}>{label}</dt>
      <dd className={`font-mono tabular-nums ${tone === 'money' ? 'text-lg font-semibold text-emerald-700' : ''}`}>{value}</dd>
    </div>
  );
}

export default function RequestForm({ cities, cargoTypes, today }: { cities: Item[]; cargoTypes: Item[]; today: string }) {
  const [v, setV] = useState({ originCity: '', destinationCity: '', cargoType: 'GENERAL', weightKg: '', volumeM3: '', pickupDate: '' });
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const seq = useRef(0);

  const set = (k: keyof typeof v) => (val: string) => { setV((s) => ({ ...s, [k]: val })); setErrors((e) => (e[k] ? { ...e, [k]: '' } : e)); };
  const cityOptions = cities.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const cargoOptions = cargoTypes.map((c) => ({ value: c.code ?? '', label: c.name ?? '' }));
  const weight = Number(v.weightKg);
  const sameCity = v.originCity !== '' && v.originCity === v.destinationCity;
  const ready = v.originCity && v.destinationCity && !sameCity && weight > 0;

  // Cotización en vivo con debounce
  useEffect(() => {
    if (!ready) {
      setQuote(null);
      setQuoteError(null);
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
    const f = new FormData(e.currentTarget);
    const g = (k: string) => String(f.get(k) ?? '').trim();
    const errs: Record<string, string> = {};
    if (!v.originCity) errs.originCity = 'Selecciona la ciudad de origen.';
    if (!v.destinationCity) errs.destinationCity = 'Selecciona la ciudad de destino.';
    if (sameCity) errs.destinationCity = 'Elige un destino distinto al origen.';
    if (g('originAddress').length < 3) errs.originAddress = 'Ingresa la dirección de recojo.';
    if (g('destinationAddress').length < 3) errs.destinationAddress = 'Ingresa la dirección de entrega.';
    if (g('cargoDescription').length < 3) errs.cargoDescription = 'Describe la carga.';
    if (!(weight > 0)) errs.weightKg = 'Ingresa el peso en kg.';
    if (!v.pickupDate) errs.pickupDate = 'Selecciona la fecha de recojo.';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setBusy(true);
    const vol = Number(v.volumeM3);
    const { data, error } = await actions.requests.create({
      originCity: v.originCity, originAddress: g('originAddress'), destinationCity: v.destinationCity, destinationAddress: g('destinationAddress'),
      cargoType: v.cargoType as any, cargoDescription: g('cargoDescription'), weightKg: weight, volumeM3: vol > 0 ? vol : undefined, pickupDate: v.pickupDate,
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

  const err = (k: string) => errors[k];
  return (
    <form onSubmit={onSubmit} noValidate className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
      <Card>
        <CardHeader><CardTitle>Datos del envío</CardTitle></CardHeader>
        <CardBody className="space-y-5">
          {error && <Alert tone="error" title="No pudimos publicar la solicitud">{error}</Alert>}
          <div className="grid gap-4 sm:grid-cols-2">
            <fieldset className="space-y-4">
              <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><MapPin className="size-3.5 text-petrol-700" aria-hidden="true" />Recojo</legend>
              <Field label="Ciudad de origen" htmlFor="originCity" error={err('originCity')}>
                <Combobox id="originCity" value={v.originCity} onValueChange={set('originCity')} options={cityOptions} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!err('originCity')} />
              </Field>
              <Field label="Dirección de recojo" htmlFor="originAddress" error={err('originAddress')}><Input id="originAddress" name="originAddress" placeholder="Av. Argentina 1234, Callao" aria-invalid={!!err('originAddress')} /></Field>
            </fieldset>
            <fieldset className="space-y-4">
              <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><MapPin className="size-3.5 text-emerald-700" aria-hidden="true" />Entrega</legend>
              <Field label="Ciudad de destino" htmlFor="destinationCity" error={err('destinationCity')}>
                <Combobox id="destinationCity" value={v.destinationCity} onValueChange={set('destinationCity')} options={cityOptions.filter((c) => c.value !== v.originCity)} placeholder="Selecciona la ciudad" searchPlaceholder="Buscar ciudad…" invalid={!!err('destinationCity')} />
              </Field>
              <Field label="Dirección de entrega" htmlFor="destinationAddress" error={err('destinationAddress')}><Input id="destinationAddress" name="destinationAddress" placeholder="Jr. Lima 456, Juliaca" aria-invalid={!!err('destinationAddress')} /></Field>
            </fieldset>
          </div>
          <fieldset className="space-y-4 border-t border-steel-200 pt-5">
            <legend className="mb-3 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-ink-500"><PackageOpen className="size-3.5 text-petrol-700" aria-hidden="true" />Carga</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tipo de carga" htmlFor="cargoType">
                <Select id="cargoType" value={v.cargoType} onValueChange={set('cargoType')} options={cargoOptions} />
              </Field>
              <Field label="Fecha de recojo" htmlFor="pickupDate" error={err('pickupDate')}><DatePicker id="pickupDate" value={v.pickupDate} onValueChange={set('pickupDate')} min={today} invalid={!!err('pickupDate')} placeholder="Elige el día" /></Field>
              <Field label="Peso" htmlFor="weightKg" error={err('weightKg')}><NumberInput id="weightKg" value={v.weightKg} onValueChange={set('weightKg')} decimals={1} suffix="kg" placeholder="3,200" invalid={!!err('weightKg')} /></Field>
              <Field label="Volumen" htmlFor="volumeM3" hint="Opcional. Ayuda a elegir el vehículo."><NumberInput id="volumeM3" value={v.volumeM3} onValueChange={set('volumeM3')} decimals={1} suffix="m³" placeholder="12" /></Field>
            </div>
            <Field label="Descripción de la carga" htmlFor="cargoDescription" error={err('cargoDescription')}><Textarea id="cargoDescription" name="cargoDescription" placeholder="Ej.: 120 rollos de tela, paletizados" aria-invalid={!!err('cargoDescription')} /></Field>
          </fieldset>
          <div className="flex justify-end gap-2 border-t border-steel-200 pt-5">
            <Button asChild variant="outline"><a href="/pyme/solicitudes">Volver</a></Button>
            <Button type="submit" loading={busy}>{busy ? 'Publicando…' : 'Publicar solicitud'}</Button>
          </div>
        </CardBody>
      </Card>

      <Card className="lg:sticky lg:top-20" aria-live="polite">
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Calculator className="size-4 text-petrol-700" aria-hidden="true" /> Cotización estimada</CardTitle>
          {quoting && <Loader2 className="size-4 animate-spin text-ink-500" aria-label="Calculando" />}
        </CardHeader>
        <CardBody>
          {sameCity && <Alert tone="error">El origen y el destino deben ser distintos.</Alert>}
          {!ready && !sameCity && <p className="text-sm text-ink-500">Elige origen, destino y peso para ver el precio sugerido al instante.</p>}
          {quoteError && <Alert tone="error">{quoteError}</Alert>}
          {quote && ready && (
            <dl className={quoting ? 'opacity-60' : ''}>
              <Row label="Distancia" value={formatKm(quote.distanceKm)} />
              <Row label="Combustible" value={formatMoney(quote.fuelCost)} />
              <Row label="Transporte" value={formatMoney(quote.transportCost)} />
              <Row label="Peajes" value={formatMoney(quote.tollCost)} />
              <Row label="Manipuleo" value={formatMoney(quote.handlingFee)} />
              <Row label="Precio sugerido" value={formatMoney(quote.suggestedPrice)} strong tone="money" />
              <Row label="Piso de subasta" value={formatMoney(quote.priceFloor)} />
              <p className="mt-3 text-xs text-ink-500">Los transportistas ofertarán por debajo del precio sugerido, nunca por debajo del piso.</p>
            </dl>
          )}
        </CardBody>
      </Card>
    </form>
  );
}
