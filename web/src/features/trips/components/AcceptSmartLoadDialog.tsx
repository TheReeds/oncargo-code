import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { ArrowRight, CheckCircle2, Truck } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { MoneyInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { RN07_FIELD_MESSAGE, isContactFailure, isVerificationFailure, splitActionError, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS, VEHICLE_LABELS, formatDate, formatMoney, formatTons } from '@/lib/format';
import type { SmartLoadView } from '../server';

/** Piso RN03 = 75 % del sugerido (se muestra de referencia; la API es la que valida). */
const FLOOR_RATIO = 0.75;
const floorOf = (suggested: number) => Math.round(suggested * FLOOR_RATIO * 100) / 100;

/**
 * Aceptar un plan Smart Load (RF32/F12) = ofertar en lote: una oferta por carga con el vehículo del plan. El transportista
 * fija el monto de cada una (≥ piso RN03) y una nota; es todo o nada (`TRP-011`). Después se muestran las ofertas creadas.
 * Cada PYME decide si adjudica: aceptar el plan no crea envíos.
 */
export default function AcceptSmartLoadDialog({ plan, onClose }: { plan: SmartLoadView | null; onClose: (accepted: boolean) => void }) {
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState('');
  const [failure, setFailure] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<SmartLoadView | null>(null);

  useEffect(() => {
    if (!plan) return;
    setAmounts(Object.fromEntries((plan.items ?? []).map((i) => [i.requestId!, String(i.income ?? '')])));
    setErrors({}); setNotes(''); setFailure(null); setBusy(false); setDone(null);
  }, [plan?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!plan) return null;
  const items = plan.items ?? [];
  const route = (o?: string, d?: string) => `${CITY_LABELS[o ?? ''] ?? o} → ${CITY_LABELS[d ?? ''] ?? d}`;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!plan) return;
    setFailure(null);
    const errs: Record<string, string> = {};
    for (const i of items) {
      const v = Number(amounts[i.requestId!]);
      const floor = floorOf(i.income ?? 0);
      if (!(v > 0)) errs[i.requestId!] = 'Ingresa el monto de tu oferta.';
      else if (v < floor) errs[i.requestId!] = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube tu oferta para continuar.`;
    }
    setErrors(errs);
    if (Object.keys(errs).length) { document.getElementById(`sl-${Object.keys(errs)[0]}`)?.focus(); return; }
    setBusy(true);
    const { data, error } = await actions.trips.acceptSmartLoad({
      id: plan.id!, items: items.map((i) => ({ requestId: i.requestId!, amount: Number(amounts[i.requestId!]) })), notes: notes.trim() || undefined,
    });
    setBusy(false);
    if (error) {
      const f = splitActionError(error);
      if (f.field === 'notes') setErrors({ notes: isContactFailure(f) ? RN07_FIELD_MESSAGE : f.message });
      else setFailure(f);
      return;
    }
    setDone(data as SmartLoadView);
  }

  const bidsOf = done?.bids ?? [];
  return (
    <Dialog
      open
      onOpenChange={(o) => { if (!o && !busy) onClose(!!done); }}
      className="max-w-xl"
      title={done ? 'Ofertas enviadas' : 'Ofertar este plan'}
      description={done ? 'Cada PYME recibió tu oferta y decide si te adjudica la carga.' : `Enviaremos una oferta por cada carga con el vehículo ${plan.vehiclePlate}. Cada PYME decide si te adjudica: aceptar el plan no crea envíos por sí solo.`}
    >
      {done ? (
        <div className="space-y-4">
          <Alert tone="success" title={`${bidsOf.length || items.length} ${(bidsOf.length || items.length) === 1 ? 'oferta enviada' : 'ofertas enviadas'}`}>
            Te avisaremos cuando una PYME acepte. Puedes seguir negociando cada oferta en Mis ofertas.
          </Alert>
          <ul className="divide-y divide-steel-200 rounded-input border border-steel-200" aria-label="Ofertas creadas">
            {items.map((i) => {
              const bid = bidsOf.find((b) => b.requestId === i.requestId);
              return (
                <li key={i.requestId} className="flex items-center justify-between gap-3 px-3 py-2.5 text-sm">
                  <div className="min-w-0"><span className="flex items-center gap-1.5 font-medium"><CheckCircle2 className="size-4 text-emerald-700" aria-hidden="true" />{route(i.originCity, i.destinationCity)}</span><span className="text-xs text-ink-500">Recojo {formatDate(i.pickupDate)}</span></div>
                  <div className="flex shrink-0 items-center gap-3"><span className="money">{formatMoney(bid?.amount ?? Number(amounts[i.requestId!]))}</span><a className="inline-flex items-center gap-1 text-xs font-medium text-petrol-700 hover:underline" href={`/transportista/ofertas?solicitud=${i.requestId}`}>Ver oferta<ArrowRight className="size-3" aria-hidden="true" /><span className="sr-only"> de {route(i.originCity, i.destinationCity)}</span></a></div>
                </li>
              );
            })}
          </ul>
          <div className="flex justify-end gap-2 border-t border-steel-200 pt-3">
            <Button variant="outline" onClick={() => onClose(true)}>Cerrar</Button>
            <Button asChild><a href="/transportista/ofertas">Ver mis ofertas</a></Button>
          </div>
        </div>
      ) : (
        <form onSubmit={submit} noValidate method="post" className="space-y-4">
          {failure && (isVerificationFailure(failure)
            ? <VerificationBlock failure={failure} />
            : <Alert tone="error" title={failure.code === 'LOGYX-TRP-011' ? 'El plan ya no está disponible' : 'No pudimos enviar las ofertas'}>{failure.message}{failure.code === 'LOGYX-TRP-011' ? ' No se envió ninguna oferta: genera nuevas sugerencias.' : ''}</Alert>)}
          <p className="flex items-center gap-2 rounded-input bg-steel-100 px-3 py-2 text-xs text-ink-700"><Truck className="size-4 text-petrol-700" aria-hidden="true" /><span><span className="font-mono font-semibold">{plan.vehiclePlate}</span> · {VEHICLE_LABELS[plan.vehicleType!] ?? plan.vehicleType} · {formatTons(plan.totalWeightKg)} de {formatTons(plan.capacityKg)}</span></p>
          <ul className="space-y-4">
            {items.map((i) => {
              const floor = floorOf(i.income ?? 0);
              const id = `sl-${i.requestId}`;
              return (
                <li key={i.requestId}>
                  <Field label={`Tu oferta · ${route(i.originCity, i.destinationCity)}`} htmlFor={id} error={errors[i.requestId!]} hint={`Sugerido ${formatMoney(i.income)} · piso ${formatMoney(floor)} (75 % del sugerido). Recojo ${formatDate(i.pickupDate)}.`}>
                    <MoneyInput id={id} value={amounts[i.requestId!] ?? ''} onValueChange={(v) => { setAmounts((a) => ({ ...a, [i.requestId!]: v })); setErrors((x) => ({ ...x, [i.requestId!]: '' })); }} invalid={!!errors[i.requestId!]} />
                  </Field>
                </li>
              );
            })}
          </ul>
          <Field label="Nota para las PYMEs" htmlFor="sl-notes" error={errors.notes} hint={`Opcional. Máximo ${LIMITS.smartLoadNotes} caracteres. Sin teléfonos ni correos.`}>
            <Textarea id="sl-notes" value={notes} maxLength={LIMITS.smartLoadNotes} aria-invalid={!!errors.notes || undefined} onChange={(e) => { setNotes(e.target.value); setErrors((x) => ({ ...x, notes: '' })); }} placeholder="Ej. Salgo el mismo día con otras cargas de la ruta." />
          </Field>
          <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => onClose(false)} disabled={busy}>Volver</Button>
            <Button type="submit" variant="money" loading={busy}>{busy ? 'Enviando ofertas…' : `Enviar ${items.length} ofertas`}</Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
