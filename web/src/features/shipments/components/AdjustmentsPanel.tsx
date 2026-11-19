import { useCallback, useEffect, useId, useState } from 'react';
import { actions } from 'astro:actions';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { MoneyInput } from '@/components/ui/number-input';
import { Select } from '@/components/ui/select';
import { Checkbox, InlineLabel, RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { SkeletonRows } from '@/components/ui/skeleton';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { PriceBreakdown } from '@/components/domain/PriceBreakdown';
import { Can } from '@/lib/permissions/react';
import { useLiveRefresh } from '@/lib/realtime';
import { splitActionError, toFormErrors } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import type { ShipmentView } from '@/lib/api/server';
import type { AdjustmentView, ShipmentAdjustmentsView } from '../server';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';
type AdjType = 'WEIGHT_DIFF' | 'DETENTION' | 'TOLL' | 'HANDLING' | 'OTHER';

export const ADJUSTMENT_LABELS: Record<string, string> = {
  WEIGHT_DIFF: 'Peso real', DETENTION: 'Estadía', TOLL: 'Peaje', HANDLING: 'Maniobras', OTHER: 'Otro',
};
const TYPE_HINT: Record<AdjType, string> = {
  WEIGHT_DIFF: 'Pesó distinto de lo declarado. Puede ser un descuento.',
  DETENTION: 'Tiempo de espera en el recojo o la entrega.',
  TOLL: 'Peajes no previstos en la oferta.',
  HANDLING: 'Carga, descarga o estiba.',
  OTHER: 'Explica el cobro en la nota. Puede ser un descuento.',
};

/** Monto con signo: «− S/ 50.00» para descuentos. */
export const signedMoney = (n?: number | null) => (n == null ? '—' : n < 0 ? `− ${formatMoney(-n)}` : formatMoney(n));

/**
 * Ajustes del precio de un envío (roadmap 2.6, ADR-0012): el transportista propone (con sugerencias de estadía y peso),
 * la PYME acepta o rechaza dentro del plazo y, si no responde, decide el operador. El precio final suma los aceptados.
 */
export default function AdjustmentsPanel({ shipment, perspective, onChanged }: { shipment: ShipmentView; perspective: Perspective; onChanged?: () => void }) {
  const id = shipment.id!;
  const [view, setView] = useState<ShipmentAdjustmentsView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [proposing, setProposing] = useState<{ type: AdjType; amount: string; stopId: string } | null>(null);
  const [rejecting, setRejecting] = useState<AdjustmentView | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoadError(null);
    const { data, error } = await actions.shipments.adjustments({ id });
    if (error) { setLoadError(error.message); return; }
    setView(data);
  }, [id]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['shipment.adjustment_updated'], match: (e) => e.data.shipmentId === id, refresh: load, fallbackMs: 60_000 });

  const changed = async () => { await load(); onChanged?.(); };
  const cancelled = shipment.status === 'CANCELLED';

  async function accept(a: AdjustmentView) {
    const ok = await confirm({
      title: `¿Aceptar ${ADJUSTMENT_LABELS[a.type!].toLowerCase()} de ${signedMoney(a.amount)}?`,
      description: 'Se suma al precio final del envío.', confirmLabel: 'Aceptar ajuste',
    });
    if (!ok) return;
    setBusy(a.id!);
    const { error } = await actions.shipments.decideAdjustment({ adjustmentId: a.id!, accept: true });
    setBusy(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Ajuste aceptado');
    changed();
  }

  async function withdraw(a: AdjustmentView) {
    const ok = await confirm({
      title: '¿Retirar este ajuste?', description: 'La PYME ya no lo verá como pendiente. No se puede deshacer.',
      confirmLabel: 'Retirar ajuste', tone: 'destructive',
    });
    if (!ok) return;
    setBusy(a.id!);
    const { error } = await actions.shipments.withdrawAdjustment({ adjustmentId: a.id! });
    setBusy(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Ajuste retirado');
    changed();
  }

  const canDecide = (a: AdjustmentView) => a.status === 'PENDING' && (perspective === 'OPERATOR' ? !!a.escalated : perspective === 'PYME' && !a.escalated);
  const stopLabel = (stopId?: string | null) => {
    const st = shipment.stops?.find((s) => s.id === stopId);
    return st ? `${st.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · ${CITY_LABELS[st.city!] ?? st.city}` : null;
  };

  return (
    <section className="space-y-4" aria-labelledby={`adj-title-${id}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 id={`adj-title-${id}`} className="text-[15px] font-semibold text-ink-900">Ajustes del precio</h3>
          <p className="text-sm text-ink-500">Peso real, estadía, peajes y maniobras. Suman al precio final si la PYME los acepta.</p>
        </div>
        {perspective === 'CARRIER' && !cancelled && (
          <Can readOnly="No puedes proponer ajustes.">
            <Button variant="outline" onClick={() => setProposing({ type: 'TOLL', amount: '', stopId: '' })}><Plus aria-hidden="true" /> Proponer ajuste</Button>
          </Can>
        )}
      </div>

      {loadError && <Alert tone="error" title="No pudimos cargar los ajustes" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{loadError}</Alert>}
      {!view && !loadError ? <SkeletonRows rows={2} /> : view && (
        <>
          <dl className="grid gap-x-6 gap-y-1 rounded-card border border-steel-200 bg-surface p-4 text-sm sm:grid-cols-3" data-testid="adjustments-summary">
            <div><dt className="text-ink-500">Precio acordado</dt><dd className="font-mono tabular-nums text-ink-900">{formatMoney(view.agreedPrice)}</dd></div>
            <div><dt className="text-ink-500">Ajustes aceptados</dt><dd className="font-mono tabular-nums text-ink-900">{signedMoney(view.acceptedTotal)}</dd></div>
            <div><dt className="text-ink-500">Precio final</dt><dd className="money text-base font-semibold">{formatMoney(view.finalPrice)}</dd></div>
          </dl>
          <PriceBreakdown breakdown={view.finalPriceBreakdown} totalLabel="Precio final" className="-mt-2 px-1" />

          {perspective === 'CARRIER' && !!view.suggestions?.length && (
            <Can>
              <ul className="space-y-2" aria-label="Ajustes sugeridos">
                {view.suggestions.map((s) => (
                  <li key={`${s.type}-${s.stopId}`} className="flex flex-wrap items-center gap-3 rounded-card border border-emerald-500/40 bg-emerald-50 px-4 py-3 text-sm">
                    <span className="font-medium text-ink-900">{ADJUSTMENT_LABELS[s.type!]} sugerida: <span className="money">{formatMoney(s.amount)}</span></span>
                    <span className="text-ink-700">{s.detail}</span>
                    <Button size="sm" variant="money" className="ml-auto" onClick={() => setProposing({ type: s.type as AdjType, amount: String(s.amount ?? ''), stopId: s.stopId ?? '' })}>
                      Proponer {ADJUSTMENT_LABELS[s.type!].toLowerCase()}
                    </Button>
                  </li>
                ))}
              </ul>
            </Can>
          )}

          {view.adjustments?.length ? (
            <ul className="space-y-3">
              {view.adjustments.map((a) => (
                <li key={a.id} className="rounded-card border border-steel-200 bg-surface p-4" data-testid="adjustment">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="text-sm font-semibold text-ink-900">{ADJUSTMENT_LABELS[a.type!] ?? a.type}</span>
                    <span className={`font-mono text-sm tabular-nums ${(a.amount ?? 0) < 0 ? 'text-ink-700' : 'text-emerald-700'}`}>{signedMoney(a.amount)}</span>
                    <StatusPill status={a.status!} kind="adjustment" />
                    {a.status === 'PENDING' && a.escalated && <StatusPill status="ESCALATED" kind="adjustment" />}
                    {stopLabel(a.stopId) && <span className="text-xs text-ink-500">{stopLabel(a.stopId)}</span>}
                    <span className="ml-auto text-xs text-ink-500">Propuesto {formatDateTime(a.createdAt)}</span>
                  </div>
                  {a.note && <p className="mt-2 text-sm text-ink-700">{a.note}</p>}
                  {a.status === 'PENDING' && !a.escalated && (
                    <p className="mt-1 text-xs text-ink-500">La PYME responde hasta el {formatDateTime(a.respondBy)}. Si no, decide OnCargo.</p>
                  )}
                  {a.decisionNote && <p className="mt-2 rounded-input bg-steel-100 px-3 py-2 text-sm text-ink-700"><span className="font-medium">Nota de la decisión:</span> {a.decisionNote}</p>}
                  {(canDecide(a) || (perspective === 'CARRIER' && a.status === 'PENDING')) && (
                    <Can>
                      <div className="mt-3 flex flex-wrap gap-2">
                        {canDecide(a) && <>
                          <Button size="sm" variant="money" loading={busy === a.id} onClick={() => accept(a)}>Aceptar ajuste</Button>
                          <Button size="sm" variant="destructive" disabled={busy === a.id} onClick={() => setRejecting(a)}>Rechazar ajuste</Button>
                        </>}
                        {perspective === 'CARRIER' && a.status === 'PENDING' && (
                          <Button size="sm" variant="ghost" loading={busy === a.id} onClick={() => withdraw(a)}>Retirar ajuste</Button>
                        )}
                      </div>
                    </Can>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p className="rounded-card border border-dashed border-steel-200 px-4 py-6 text-center text-sm text-ink-500">Este envío no tiene ajustes.</p>
          )}
        </>
      )}

      {proposing && (
        <ProposeDialog shipment={shipment} initial={proposing} onClose={() => setProposing(null)} onDone={() => { setProposing(null); changed(); }} />
      )}
      <ReasonDialog
        open={!!rejecting} onOpenChange={(o) => !o && setRejecting(null)} title="Rechazar ajuste" required tone="destructive"
        description={rejecting ? `${ADJUSTMENT_LABELS[rejecting.type!]} de ${signedMoney(rejecting.amount)}. El transportista verá tu motivo.` : undefined}
        confirmLabel="Rechazar ajuste" maxLength={LIMITS.adjustmentNote} reasonField="note"
        onSubmit={async (note) => {
          const { error } = await actions.shipments.decideAdjustment({ adjustmentId: rejecting!.id!, accept: false, note });
          if (error) return splitActionError(error);
          toast.success('Ajuste rechazado');
          setRejecting(null);
          changed();
          return null;
        }}
      />
    </section>
  );
}

function ProposeDialog({ shipment, initial, onClose, onDone }: { shipment: ShipmentView; initial: { type: AdjType; amount: string; stopId: string }; onClose: () => void; onDone: () => void }) {
  const uid = useId();
  const [type, setType] = useState<AdjType>(initial.type);
  const [amount, setAmount] = useState(initial.amount);
  const [discount, setDiscount] = useState(false);
  const [stopId, setStopId] = useState(initial.stopId);
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<{ amount?: string; note?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Una clave por diálogo: un doble clic o un reintento no crea dos ajustes.
  const [key] = useState(() => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `adj-${Date.now()}-${Math.random().toString(36).slice(2)}`));
  const allowsDiscount = type === 'WEIGHT_DIFF' || type === 'OTHER';

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(amount);
    const next: typeof errors = {};
    if (!amount || !Number.isFinite(n) || n <= 0) next.amount = 'Ingresa el monto con IGV.';
    if (type === 'OTHER' && !note.trim()) next.note = 'Explica el ajuste.';
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length) return;
    setBusy(true);
    const { error } = await actions.shipments.proposeAdjustment({
      id: shipment.id!, type, amount: allowsDiscount && discount ? -n : n, note: note.trim() || undefined,
      stopId: stopId || undefined, idempotencyKey: key,
    });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['amount', 'note']);
      setErrors(r.errors);
      if (r.formError) setFormError(r.formError);
      return;
    }
    toast.success('Ajuste propuesto', 'La PYME lo verá en el detalle del envío.');
    onDone();
  }

  const stops = (shipment.stops ?? []).map((s) => ({ value: s.id!, label: `${s.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · ${CITY_LABELS[s.city!] ?? s.city}` }));
  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} title="Proponer ajuste" description="Monto con IGV. La PYME lo acepta o rechaza; si no responde a tiempo, decide OnCargo.">
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <fieldset className="space-y-2">
          <legend className="text-[13px] font-medium text-ink-900">Tipo</legend>
          <RadioGroup value={type} onValueChange={(v) => { setType(v as AdjType); if (v !== 'WEIGHT_DIFF' && v !== 'OTHER') setDiscount(false); }}>
            {(Object.keys(TYPE_HINT) as AdjType[]).map((t) => (
              <div key={t} className="flex items-start gap-2">
                <RadioGroupItem value={t} id={`${uid}-${t}`} className="mt-0.5" />
                <div><InlineLabel htmlFor={`${uid}-${t}`}>{ADJUSTMENT_LABELS[t]}</InlineLabel><p className="text-xs text-ink-500">{TYPE_HINT[t]}</p></div>
              </div>
            ))}
          </RadioGroup>
        </fieldset>
        <Field label="Monto" htmlFor={`${uid}-amount`} error={errors.amount} required hint="Con IGV, como el precio acordado.">
          <MoneyInput id={`${uid}-amount`} value={amount} onValueChange={setAmount} invalid={!!errors.amount} />
        </Field>
        {allowsDiscount && (
          <div className="flex items-center gap-2">
            <Checkbox id={`${uid}-discount`} checked={discount} onCheckedChange={(c) => setDiscount(c === true)} />
            <InlineLabel htmlFor={`${uid}-discount`}>Es un descuento (baja el precio)</InlineLabel>
          </div>
        )}
        <Field label="Parada" htmlFor={`${uid}-stop`} hint="Opcional. En la estadía, dónde esperó el conductor.">
          <Select id={`${uid}-stop`} value={stopId || 'ALL'} onValueChange={(v) => setStopId(v === 'ALL' ? '' : v)} options={[{ value: 'ALL', label: 'Todo el envío' }, ...stops]} />
        </Field>
        <Field label="Nota" htmlFor={`${uid}-note`} error={errors.note} required={type === 'OTHER'} hint={`${note.length}/${LIMITS.adjustmentNote} · Sin teléfonos ni correos.`}>
          <Textarea id={`${uid}-note`} value={note} rows={3} maxLength={LIMITS.adjustmentNote} onChange={(e) => setNote(e.target.value)} aria-invalid={!!errors.note} placeholder="Peaje Pativilca, 2 ejes" />
        </Field>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy}>{busy ? 'Enviando…' : 'Proponer ajuste'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
