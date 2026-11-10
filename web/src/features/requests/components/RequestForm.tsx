import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Calculator, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { formatKm, formatMoney } from '@/lib/format';
import type { Quote } from '@/lib/api/server';

type Item = { code?: string; name?: string };

function Row({ label, value, strong, tone }: { label: string; value: string; strong?: boolean; tone?: 'money' }) {
  return (
    <div className={`flex items-center justify-between py-1.5 text-sm ${strong ? 'border-t border-steel-200 pt-2.5 font-semibold' : ''}`}>
      <dt className={strong ? '' : 'text-ink-700'}>{label}</dt>
      <dd className={`font-mono tabular-nums ${tone === 'money' ? 'text-lg font-semibold text-emerald-700' : ''}`}>{value}</dd>
    </div>
  );
}

export default function RequestForm({ cities, cargoTypes, today }: { cities: Item[]; cargoTypes: Item[]; today: string }) {
  const [v, setV] = useState({ originCity: '', destinationCity: '', cargoType: 'GENERAL', weightKg: '' });
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const seq = useRef(0);

  const set = (k: keyof typeof v) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setV((s) => ({ ...s, [k]: e.target.value }));
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
        setQuoteError(error.message);
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
    if (!v.originCity) errs.originCity = 'Selecciona el origen';
    if (!v.destinationCity) errs.destinationCity = 'Selecciona el destino';
    if (sameCity) errs.destinationCity = 'El destino debe ser distinto al origen';
    if (g('originAddress').length < 3) errs.originAddress = 'Ingresa la dirección de recojo';
    if (g('destinationAddress').length < 3) errs.destinationAddress = 'Ingresa la dirección de entrega';
    if (g('cargoDescription').length < 3) errs.cargoDescription = 'Describe la carga';
    if (!(weight > 0)) errs.weightKg = 'Ingresa el peso en kg';
    if (!g('pickupDate')) errs.pickupDate = 'Selecciona la fecha de recojo';
    setErrors(errs);
    if (Object.keys(errs).length) return;

    setBusy(true);
    const vol = Number(g('volumeM3'));
    const { data, error } = await actions.requests.create({
      originCity: v.originCity, originAddress: g('originAddress'), destinationCity: v.destinationCity, destinationAddress: g('destinationAddress'),
      cargoType: v.cargoType as any, cargoDescription: g('cargoDescription'), weightKg: weight, volumeM3: vol > 0 ? vol : undefined, pickupDate: g('pickupDate'),
    });
    if (error) {
      setError(error.message);
      setBusy(false);
      return;
    }
    window.location.href = `/pyme/solicitudes/${data.id}?creada=1`;
  }

  const err = (k: string) => errors[k];
  return (
    <form onSubmit={onSubmit} noValidate className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
      <Card>
        <CardHeader><CardTitle>Datos del envío</CardTitle></CardHeader>
        <CardBody className="space-y-5">
          {error && <Alert tone="error">{error}</Alert>}
          <div className="grid gap-4 sm:grid-cols-2">
            <fieldset className="space-y-4">
              <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Recojo</legend>
              <Field label="Ciudad de origen" htmlFor="originCity" error={err('originCity')}>
                <Select id="originCity" value={v.originCity} onChange={set('originCity')} aria-invalid={!!err('originCity')}>
                  <option value="">Selecciona…</option>
                  {cities.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </Select>
              </Field>
              <Field label="Dirección de recojo" htmlFor="originAddress" error={err('originAddress')}><Input id="originAddress" name="originAddress" placeholder="Av. Argentina 1234, Callao" aria-invalid={!!err('originAddress')} /></Field>
            </fieldset>
            <fieldset className="space-y-4">
              <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Entrega</legend>
              <Field label="Ciudad de destino" htmlFor="destinationCity" error={err('destinationCity')}>
                <Select id="destinationCity" value={v.destinationCity} onChange={set('destinationCity')} aria-invalid={!!err('destinationCity')}>
                  <option value="">Selecciona…</option>
                  {cities.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </Select>
              </Field>
              <Field label="Dirección de entrega" htmlFor="destinationAddress" error={err('destinationAddress')}><Input id="destinationAddress" name="destinationAddress" placeholder="Jr. Lima 456, Juliaca" aria-invalid={!!err('destinationAddress')} /></Field>
            </fieldset>
          </div>
          <fieldset className="space-y-4 border-t border-steel-200 pt-5">
            <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">Carga</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Tipo de carga" htmlFor="cargoType">
                <Select id="cargoType" value={v.cargoType} onChange={set('cargoType')}>
                  {cargoTypes.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
                </Select>
              </Field>
              <Field label="Fecha de recojo" htmlFor="pickupDate" error={err('pickupDate')}><Input id="pickupDate" name="pickupDate" type="date" min={today} aria-invalid={!!err('pickupDate')} /></Field>
              <Field label="Peso (kg)" htmlFor="weightKg" error={err('weightKg')}><Input id="weightKg" type="number" inputMode="decimal" min="1" step="any" value={v.weightKg} onChange={set('weightKg')} className="font-mono" placeholder="3200" aria-invalid={!!err('weightKg')} /></Field>
              <Field label="Volumen (m³)" htmlFor="volumeM3" hint="Opcional"><Input id="volumeM3" name="volumeM3" type="number" inputMode="decimal" min="0" step="any" className="font-mono" /></Field>
            </div>
            <Field label="Descripción de la carga" htmlFor="cargoDescription" error={err('cargoDescription')}><Textarea id="cargoDescription" name="cargoDescription" placeholder="Ej.: 120 rollos de tela, paletizados" aria-invalid={!!err('cargoDescription')} /></Field>
          </fieldset>
          <div className="flex justify-end gap-2 border-t border-steel-200 pt-5">
            <Button asChild variant="outline"><a href="/pyme/solicitudes">Cancelar</a></Button>
            <Button type="submit" disabled={busy}>{busy ? 'Publicando…' : 'Publicar solicitud'}</Button>
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
          {!ready && !sameCity && <p className="text-sm text-ink-500">Elige origen, destino y peso para ver el precio sugerido en vivo.</p>}
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
