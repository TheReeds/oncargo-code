import { useState } from 'react';
import { actions } from 'astro:actions';
import { CalendarClock, Plus, Undo2 } from 'lucide-react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { MoneyInput, NumberInput } from '@/components/ui/number-input';
import { DatePicker } from '@/components/ui/date-picker';
import { InlineLabel, RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { CARGO_LABELS, formatDateTime } from '@/lib/format';
import { percentToRate, rateToPercent } from '@/features/operator/parameters';
import type { TariffValues, TariffVersion } from '../server';

const STATE: Record<string, { label: string; tone: 'emerald' | 'petrol' | 'neutral' | 'amber' }> = {
  CURRENT: { label: 'Vigente', tone: 'emerald' },
  SCHEDULED: { label: 'Programada', tone: 'petrol' },
  PAST: { label: 'Reemplazada', tone: 'neutral' },
  CANCELLED: { label: 'Anulada', tone: 'amber' },
};

type Form = {
  baseFee: string; fuelPerKm: string; ratePerKmTon: string; tollEveryKm: string; tollPrice: string; handlingPercent: string;
  multipliers: Record<string, string>; when: 'now' | 'later'; day: string;
};

const num = (v: number | string | undefined) => (v == null ? '' : String(v));

function toForm(v: TariffValues): Form {
  return {
    baseFee: num(v.baseFee), fuelPerKm: num(v.fuelPerKm), ratePerKmTon: num(v.ratePerKmTon), tollEveryKm: num(v.tollEveryKm),
    tollPrice: num(v.tollPrice), handlingPercent: rateToPercent(num(v.handlingRate)),
    multipliers: Object.fromEntries(Object.entries(v.cargoMultipliers ?? {}).map(([k, m]) => [k, num(m)])),
    when: 'now', day: '',
  };
}

const sol = (v: number | string | undefined, digits = 2) =>
  v == null ? '—' : `S/ ${Number(v).toLocaleString('es-PE', { minimumFractionDigits: digits, maximumFractionDigits: 4 })}`;

/** Valores de una versión en lista de definiciones (tarjeta de la vigente y detalle del historial). */
function Values({ v }: { v: TariffValues }) {
  const rows: [string, string][] = [
    ['Tarifa base', sol(v.baseFee)],
    ['Combustible por km', sol(v.fuelPerKm, 2)],
    ['Transporte por t·km', sol(v.ratePerKmTon, 2)],
    ['Peaje', `${sol(v.tollPrice)} cada ${num(v.tollEveryKm)} km`],
    ['Manipuleo', `${rateToPercent(num(v.handlingRate))} % del transporte`],
  ];
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm">
        {rows.map(([k, val]) => (
          <div key={k} className="contents"><dt className="text-ink-500">{k}</dt><dd className="font-mono tabular-nums text-ink-900">{val}</dd></div>
        ))}
      </dl>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-sm" aria-label="Multiplicador por tipo de carga">
        {Object.entries(v.cargoMultipliers ?? {}).map(([k, m]) => (
          <div key={k} className="contents"><dt className="text-ink-500">{CARGO_LABELS[k] ?? k}</dt><dd className="font-mono tabular-nums text-ink-900">× {Number(m).toFixed(2)}</dd></div>
        ))}
      </dl>
    </div>
  );
}

/**
 * Tarifario versionado (2.14). La versión vigente alimenta el motor de costos (sugerido y piso de cada solicitud nueva);
 * un cambio crea otra versión, vigente ya o programada, con motivo. Una programada se puede anular antes de que rija.
 */
export default function TariffEditor({ versions, today }: { versions: TariffVersion[]; today: string }) {
  const current = versions.find((v) => v.state === 'CURRENT') ?? versions[0];
  const latest = versions.reduce((m, v) => Math.max(m, v.version ?? 0), 0);
  const [form, setForm] = useState<Form | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [cancelling, setCancelling] = useState<TariffVersion | null>(null);

  const set = (k: keyof Form, v: string) => { setForm((f) => (f ? { ...f, [k]: v } : f)); setErrors((e) => ({ ...e, [k]: '' })); };
  const setMult = (k: string, v: string) => { setForm((f) => (f ? { ...f, multipliers: { ...f.multipliers, [k]: v } } : f)); setErrors((e) => ({ ...e, [`cargoMultipliers.${k}`]: '' })); };

  function valid(): boolean {
    if (!form) return false;
    const e: Record<string, string> = {};
    const req = (k: keyof Form, label: string) => { if (!/^\d+(\.\d{1,4})?$/.test(String(form[k]))) e[k] = `Indica ${label}.`; };
    req('baseFee', 'la tarifa base (0 si no aplica)');
    req('fuelPerKm', 'el combustible por km');
    req('ratePerKmTon', 'la tarifa por t·km');
    req('tollEveryKm', 'cada cuántos km hay un peaje');
    req('tollPrice', 'el precio del peaje');
    if (percentToRate(form.handlingPercent) === null) e.handlingPercent = 'Usa un porcentaje con hasta 2 decimales.';
    for (const [k, v] of Object.entries(form.multipliers)) if (!/^\d+(\.\d{1,2})?$/.test(v)) e[`cargoMultipliers.${k}`] = 'Usa un número como 1.25.';
    if (form.when === 'later' && !form.day) e.day = 'Elige desde qué día rige.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function submit(reason: string) {
    if (!form) return null;
    const { error } = await actions.config.createTariff({
      baseFee: form.baseFee, fuelPerKm: form.fuelPerKm, ratePerKmTon: form.ratePerKmTon, tollEveryKm: form.tollEveryKm,
      tollPrice: form.tollPrice, handlingRate: percentToRate(form.handlingPercent)!, cargoMultipliers: form.multipliers,
      effectiveFrom: form.when === 'later' ? `${form.day}T00:00:00-05:00` : undefined, reason, expectedLatestVersion: latest,
    });
    if (error) return splitActionError(error);
    flash(form.when === 'later' ? 'Versión programada' : 'Tarifa actualizada', 'success');
    window.location.reload();
    return null;
  }

  async function cancel(reason: string) {
    if (!cancelling) return null;
    const { error } = await actions.config.cancelTariff({ version: cancelling.version!, reason });
    if (error) return splitActionError(error);
    flash('Versión anulada', 'success');
    window.location.reload();
    return null;
  }

  const field = (k: keyof Form, label: string, hint: string, input: React.ReactNode) => (
    <Field label={label} htmlFor={`tariff-${k}`} hint={hint} error={errors[k] || null}>{input}</Field>
  );

  return (
    <div className="space-y-4">
      <Card data-testid="tariff-current">
        <CardHeader>
          <div>
            <CardTitle>Tarifa vigente{current ? <span className="ml-2 font-mono text-sm text-ink-500">v{current.version}</span> : null}</CardTitle>
            <CardDescription>Con ella se calculan el sugerido y el piso de cada solicitud nueva. Las ya publicadas conservan su precio.</CardDescription>
          </div>
          {current && (
            <Button size="sm" onClick={() => { setForm(toForm(current.values!)); setErrors({}); }}><Plus aria-hidden="true" /> Nueva versión</Button>
          )}
        </CardHeader>
        <CardBody>{current?.values ? <Values v={current.values} /> : <p className="text-sm text-ink-500">Sin versiones.</p>}</CardBody>
      </Card>

      <Card>
        <CardHeader><div><CardTitle>Versiones</CardTitle><CardDescription>La más nueva primero. Ninguna se edita: un cambio es otra versión.</CardDescription></div></CardHeader>
        <Table label="Versiones del tarifario" stack dense>
          <thead><tr><Th>Versión</Th><Th>Rige desde</Th><Th>Motivo</Th><Th>Quién</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
          <tbody>
            {versions.map((v) => {
              const st = STATE[v.state ?? 'PAST'] ?? STATE.PAST;
              return (
                <Tr key={v.version} data-testid="tariff-row">
                  <Td primary><span className="font-mono font-medium text-ink-900">v{v.version}</span> <Badge tone={st.tone}>{st.label}</Badge></Td>
                  <Td label="Rige desde" className="whitespace-nowrap">{formatDateTime(v.effectiveFrom)}</Td>
                  <Td label="Motivo"><span className="block max-w-md text-ink-700">{v.reason}</span></Td>
                  <Td label="Quién"><span className="block">{v.createdByName ?? 'Sistema'}</span><span className="block text-xs text-ink-500">{formatDateTime(v.createdAt)}</span></Td>
                  <Td label="Acciones">
                    {v.state === 'SCHEDULED' && (
                      <Button variant="outline" size="sm" onClick={() => setCancelling(v)}><Undo2 aria-hidden="true" /> Anular<span className="sr-only"> la versión {v.version}</span></Button>
                    )}
                  </Td>
                </Tr>
              );
            })}
          </tbody>
        </Table>
      </Card>

      <ReasonDialog
        open={!!form}
        onOpenChange={(o) => !o && setForm(null)}
        title="Nueva versión del tarifario"
        description="Parte de la tarifa vigente. Cambia lo que necesites."
        required minLength={3} maxLength={300}
        label="Motivo del cambio" hint="Obligatorio. Queda en el historial y en la auditoría."
        placeholder="Ej. Subió el diésel 6 % en noviembre."
        confirmLabel={form?.when === 'later' ? 'Programar versión' : 'Aplicar desde ahora'}
        canSubmit={valid}
        onFailure={(f) => { if (f.field && f.field !== 'reason') { setErrors((e) => ({ ...e, [f.field === 'handlingRate' ? 'handlingPercent' : f.field === 'effectiveFrom' ? 'day' : f.field!]: f.message })); return true; } return false; }}
        onSubmit={submit}
      >
        {form && (
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              {field('baseFee', 'Tarifa base', 'Monto fijo por servicio.', <MoneyInput id="tariff-baseFee" value={form.baseFee} onValueChange={(v) => set('baseFee', v)} invalid={!!errors.baseFee} />)}
              {field('fuelPerKm', 'Combustible por km', 'Soles por km recorrido.', <NumberInput id="tariff-fuelPerKm" value={form.fuelPerKm} onValueChange={(v) => set('fuelPerKm', v)} decimals={4} prefix="S/" invalid={!!errors.fuelPerKm} />)}
              {field('ratePerKmTon', 'Transporte por t·km', 'Soles por tonelada y km.', <NumberInput id="tariff-ratePerKmTon" value={form.ratePerKmTon} onValueChange={(v) => set('ratePerKmTon', v)} decimals={4} prefix="S/" invalid={!!errors.ratePerKmTon} />)}
              {field('handlingPercent', 'Manipuleo', 'Porcentaje del costo de transporte.', <NumberInput id="tariff-handlingPercent" value={form.handlingPercent} onValueChange={(v) => set('handlingPercent', v)} decimals={2} suffix="%" invalid={!!errors.handlingPercent} />)}
              {field('tollPrice', 'Precio del peaje', 'Soles por peaje.', <MoneyInput id="tariff-tollPrice" value={form.tollPrice} onValueChange={(v) => set('tollPrice', v)} invalid={!!errors.tollPrice} />)}
              {field('tollEveryKm', 'Un peaje cada', 'Kilómetros entre peajes.', <NumberInput id="tariff-tollEveryKm" value={form.tollEveryKm} onValueChange={(v) => set('tollEveryKm', v)} decimals={0} suffix="km" invalid={!!errors.tollEveryKm} />)}
            </div>
            <fieldset>
              <legend className="mb-2 text-sm font-medium text-ink-900">Multiplicador por tipo de carga</legend>
              <div className="grid gap-3 sm:grid-cols-3">
                {Object.entries(form.multipliers).map(([k, v]) => (
                  <Field key={k} label={CARGO_LABELS[k] ?? k} htmlFor={`tariff-mult-${k}`} error={errors[`cargoMultipliers.${k}`] || null}>
                    <NumberInput id={`tariff-mult-${k}`} value={v} onValueChange={(x) => setMult(k, x)} decimals={2} prefix="×" invalid={!!errors[`cargoMultipliers.${k}`]} />
                  </Field>
                ))}
              </div>
            </fieldset>
            <fieldset>
              <legend className="mb-2 text-sm font-medium text-ink-900">Desde cuándo rige</legend>
              <RadioGroup value={form.when} onValueChange={(v) => set('when', v)} aria-label="Desde cuándo rige">
                <div className="flex items-center gap-2"><RadioGroupItem id="tariff-now" value="now" /><InlineLabel htmlFor="tariff-now">Desde ahora</InlineLabel></div>
                <div className="flex items-center gap-2"><RadioGroupItem id="tariff-later" value="later" /><InlineLabel htmlFor="tariff-later">Desde un día (00:00, hora de Lima)</InlineLabel></div>
              </RadioGroup>
              {form.when === 'later' && (
                <div className="mt-3">
                  <Field label="Rige desde" htmlFor="tariff-day" error={errors.day || null} hint="Hasta un año adelante.">
                    <DatePicker id="tariff-day" value={form.day} onValueChange={(v) => set('day', v)} min={today} invalid={!!errors.day} />
                  </Field>
                </div>
              )}
            </fieldset>
          </div>
        )}
      </ReasonDialog>

      <ReasonDialog
        open={!!cancelling}
        onOpenChange={(o) => !o && setCancelling(null)}
        title={cancelling ? `Anular la versión ${cancelling.version}` : 'Anular versión'}
        description={cancelling ? `Iba a regir desde ${formatDateTime(cancelling.effectiveFrom)}. Sigue la versión vigente.` : undefined}
        required minLength={3} maxLength={300} label="Motivo" confirmLabel="Anular versión" tone="destructive"
        onSubmit={cancel}
      />
      <p className="flex items-center gap-2 text-xs text-ink-500"><CalendarClock className="size-4" aria-hidden="true" /> Cada solicitud guarda la versión con la que se cotizó.</p>
    </div>
  );
}
