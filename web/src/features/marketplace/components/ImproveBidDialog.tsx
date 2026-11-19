import { useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { ArrowRight, TrendingDown } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { MoneyInput } from '@/components/ui/number-input';
import { Combobox } from '@/components/ui/combobox';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toaster';
import { toFormErrors } from '@/lib/action-error';
import { CITY_LABELS, formatDateTime, formatMoney, VEHICLE_LABELS } from '@/lib/format';
import type { BidView, RequestView } from '@/lib/api/server';

type Revision = { at?: string; previousAmount?: number; newAmount?: number; previousPlate?: string; newPlate?: string };
type EligVehicle = { vehicleId?: string; plate?: string; type?: string; eligible?: boolean; blockerCode?: string | null; blockerMessage?: string | null; warnings?: { code?: string; message?: string }[] };

/** «S/ 1,300.00 → S/ 1,250.00 · 12 nov · 10:30» (y la placa si cambió). Nunca montos de otras ofertas (RN04). */
export function revisionLine(r: Revision): string {
  const money = r.previousAmount != null && r.newAmount != null && r.previousAmount !== r.newAmount ? `${formatMoney(r.previousAmount)} → ${formatMoney(r.newAmount)}` : r.newAmount != null ? formatMoney(r.newAmount) : '';
  const plate = r.previousPlate && r.newPlate && r.previousPlate !== r.newPlate ? `${r.previousPlate} → ${r.newPlate}` : '';
  return [money, plate, formatDateTime(r.at)].filter(Boolean).join(' · ');
}

/**
 * Mejorar oferta (2.8): baja el monto y/o cambia el vehículo de una oferta pendiente con la subasta abierta. No cuenta como
 * oferta nueva (RN04) y queda en el historial. El servidor responde `LOGYX-BID-006` si el monto sube, `BID-007` si no cambia nada.
 */
export default function ImproveBidDialog({ request, open, onOpenChange, onImproved }: { request: RequestView; open: boolean; onOpenChange: (o: boolean) => void; onImproved: (bid: BidView) => void }) {
  const bid = request.myBid!;
  const current = bid.amount ?? 0;
  const floor = request.priceFloor ?? 0;
  const route = `${CITY_LABELS[request.originCity!]} a ${CITY_LABELS[request.destinationCity!]}`;
  const [amount, setAmount] = useState('');
  const [vehicleId, setVehicleId] = useState(bid.vehicleId ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [vehicles, setVehicles] = useState<EligVehicle[] | null>(null);
  const [revisions, setRevisions] = useState<Revision[] | null>(null);

  const loadRevisions = () => actions.marketplace.bidRevisions({ id: bid.id! }).then(({ data }) => setRevisions((data as Revision[]) ?? [])).catch(() => setRevisions([]));

  useEffect(() => {
    if (!open) return;
    setAmount(''); setVehicleId(bid.vehicleId ?? ''); setErrors({}); setFormError(null); setBusy(false); setVehicles(null); setRevisions(null);
    let alive = true;
    actions.marketplace.eligibility({ requestId: request.id! }).then(({ data }) => { if (alive) setVehicles(((data as { vehicles?: EligVehicle[] })?.vehicles) ?? []); }).catch(() => alive && setVehicles([]));
    loadRevisions();
    return () => { alive = false; };
  }, [open, bid.id]);

  const options = useMemo(() => (vehicles ?? []).map((v) => {
    const isCurrent = v.vehicleId === bid.vehicleId;
    const blocked = !isCurrent && v.eligible === false;
    const hint = isCurrent ? 'Vehículo actual' : blocked ? (v.blockerCode === 'LOGYX-FLT-007' ? `Bloqueado: ${v.blockerMessage ?? 'documento vencido'}` : v.blockerMessage ?? 'No puede ir en esta carga') : v.warnings?.[0]?.message ?? 'Disponible en esas fechas';
    return { value: v.vehicleId!, label: `${v.plate} · ${VEHICLE_LABELS[v.type ?? ''] ?? v.type ?? ''}`, hint, disabled: blocked };
  }), [vehicles, bid.vehicleId]);
  const picked = vehicles?.find((v) => v.vehicleId === vehicleId);
  const vehicleChanged = !!vehicleId && vehicleId !== bid.vehicleId;
  const amt = Number(amount);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setFormError(null);
    const errs: Record<string, string> = {};
    if (amount && !(amt > 0)) errs.amount = 'Ingresa un monto válido.';
    else if (amount && amt >= current) errs.amount = `El monto nuevo debe ser menor a tu oferta actual (${formatMoney(current)}). Solo puedes bajar la oferta.`;
    else if (amount && floor > 0 && amt < floor) errs.amount = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube un poco el monto.`;
    if (!amount && !vehicleChanged) errs.amount = 'Baja el monto o elige otro vehículo para mejorar tu oferta.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { data, error } = await actions.marketplace.improveBid({ id: bid.id!, amount: amount ? amt : undefined, vehicleId: vehicleChanged ? vehicleId : undefined });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['amount', 'vehicleId']);
      setErrors(r.errors);
      setFormError(r.formError);
      return;
    }
    toast.success('Oferta mejorada', 'La PYME ve que mejoraste tu oferta. No cuenta como oferta nueva.');
    onImproved(data as BidView);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !busy && onOpenChange(o)} title="Mejorar oferta" description={`${route} · hoy ofertas ${formatMoney(current)}${bid.vehiclePlate ? ` con ${bid.vehiclePlate}` : ''}`}
      footer={<><Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button><Button type="submit" form="improve-form" variant="money" loading={busy}>{busy ? 'Mejorando oferta…' : 'Mejorar oferta'}</Button></>}>
      <form id="improve-form" onSubmit={submit} noValidate method="post" className="space-y-4">
        {formError && <Alert tone="error" title="No pudimos mejorar la oferta">{formError}</Alert>}
        <Field label="Monto nuevo" htmlFor="imp-amount" error={errors.amount} hint={`Debe ser menor a ${formatMoney(current)}. Piso de la subasta: ${formatMoney(floor)}. Incluye IGV.`}>
          <MoneyInput id="imp-amount" value={amount} onValueChange={(v) => { setAmount(v); setErrors((x) => ({ ...x, amount: '' })); }} invalid={!!errors.amount} placeholder={String(Math.max(floor, Math.round(current * 0.97)))} />
        </Field>
        <Field label="Vehículo" htmlFor="imp-vehicle" error={errors.vehicleId} hint="Opcional. Cambia el vehículo si el actual ya no puede ir.">
          {vehicles === null ? <Skeleton className="h-10 w-full" /> : (
            <Combobox id="imp-vehicle" value={vehicleId} onValueChange={(v) => { setVehicleId(v); setErrors((x) => ({ ...x, vehicleId: '' })); }} options={options} placeholder="Selecciona un vehículo" searchPlaceholder="Buscar por placa…" invalid={!!errors.vehicleId} />
          )}
        </Field>
        {vehicleChanged && picked?.warnings?.map((w) => <Alert key={w.code} tone="warning" title={w.message ?? 'Revisa los documentos de este vehículo'}>Puedes mejorar la oferta igual, pero conviene regularizarlo antes del recojo.</Alert>)}
        <section aria-labelledby="imp-hist" className="rounded-input border border-steel-200 bg-steel-100/60 p-3">
          <h3 id="imp-hist" className="flex items-center gap-1.5 text-xs font-semibold text-ink-700"><TrendingDown className="size-3.5" aria-hidden="true" />Historial de mejoras</h3>
          {revisions === null ? <Skeleton className="mt-2 h-4 w-3/4" /> : revisions.length === 0 ? (
            <p className="mt-1 text-xs text-ink-500" data-testid="revisions-empty">Aún no mejoraste esta oferta.</p>
          ) : (
            <ul className="mt-1.5 space-y-1 text-xs text-ink-700" data-testid="revisions">
              {revisions.map((r, i) => <li key={i} className="flex items-start gap-1.5"><ArrowRight className="mt-0.5 size-3 shrink-0 text-ink-500" aria-hidden="true" /><span className="font-mono tabular-nums">{revisionLine(r)}</span></li>)}
            </ul>
          )}
        </section>
      </form>
    </Dialog>
  );
}
