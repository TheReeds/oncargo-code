import { useEffect, useId, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Checkbox, InlineLabel } from '@/components/ui/form-controls';
import { DatePicker } from '@/components/ui/date-picker';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { SkeletonRows } from '@/components/ui/skeleton';
import { flash } from '@/components/ui/toaster';
import { toFormErrors, splitActionError } from '@/lib/action-error';
import { CITY_LABELS, formatDate, formatMoney } from '@/lib/format';
import { limaDateOf, limaToday } from '@/lib/lima-date';
import type { SettlementRow } from '../server';
import { PAYMENT_METHOD_OPTIONS } from '../labels';

export type PendingCarrier = { id: string; name: string; count: number; amount: number };

const due = (r: SettlementRow) => (r.commission ?? 0) + (r.commissionVat ?? 0);

/**
 * El operador registra que cobró la comisión (con su IGV) de envíos de un transportista: elige el transportista, marca los
 * envíos y deja la operación. El monto lo calcula el backend; aquí se muestra el total de lo marcado.
 */
export default function CommissionCollectDialog({ carriers, initialCarrierId, onClose, onDone }: { carriers: PendingCarrier[]; initialCarrierId?: string; onClose: () => void; onDone: () => void }) {
  const uid = useId();
  const [carrierId, setCarrierId] = useState(initialCarrierId ?? carriers[0]?.id ?? '');
  const [rows, setRows] = useState<SettlementRow[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [method, setMethod] = useState('TRANSFER');
  const [operation, setOperation] = useState('');
  const [paidOn, setPaidOn] = useState(limaToday());
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!carrierId) return;
    let alive = true;
    setRows(null); setLoadError(null);
    actions.settlements.list({ carrierOrgId: carrierId, commissionPending: true, size: 100 }).then(({ data, error }) => {
      if (!alive) return;
      if (error) { setLoadError(splitActionError(error).message); return; }
      const items = (data?.items ?? []) as SettlementRow[];
      setRows(items);
      setPicked(new Set(items.filter((r) => r.status !== 'DISPUTED').map((r) => r.shipmentId!)));
    });
    return () => { alive = false; };
  }, [carrierId]);

  const total = useMemo(() => (rows ?? []).filter((r) => picked.has(r.shipmentId!)).reduce((s, r) => s + due(r), 0), [rows, picked]);
  const toggle = (id: string, on: boolean) => setPicked((p) => { const n = new Set(p); if (on) n.add(id); else n.delete(id); return n; });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!picked.size) next.shipmentIds = 'Elige al menos un envío.';
    if (!operation.trim()) next.operationNumber = 'Ingresa el número de operación.';
    if (!paidOn) next.paidOn = 'Elige la fecha del cobro.';
    setErrors(next); setFormError(null);
    if (Object.keys(next).length) return;
    setBusy(true);
    const { data, error } = await actions.settlements.collectCommission({ carrierOrgId: carrierId, shipmentIds: [...picked], method: method as 'TRANSFER', operationNumber: operation.trim(), paidOn, note: note.trim() || undefined });
    setBusy(false);
    if (error) { const r = toFormErrors(error, ['operationNumber', 'paidOn', 'shipmentIds', 'note']); setErrors(r.errors); setFormError(r.formError); return; }
    flash(`Cobro de comisión registrado: ${formatMoney(data?.amount)} de ${data?.shipmentsCount ?? picked.size} envíos`);
    onDone();
  }

  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} className="max-w-2xl" title="Registrar cobro de comisión" description="Marca los envíos que cobraste al transportista. El monto es la comisión más su IGV.">
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <Field label="Transportista" htmlFor={`${uid}-c`} required>
          <Select id={`${uid}-c`} value={carrierId} onValueChange={setCarrierId} options={carriers.map((c) => ({ value: c.id, label: c.name, hint: `${c.count} ${c.count === 1 ? 'envío' : 'envíos'} · ${formatMoney(c.amount)}` }))} />
        </Field>
        <fieldset className="space-y-2" aria-describedby={errors.shipmentIds ? `${uid}-sh-err` : undefined}>
          <legend className="text-[13px] font-medium text-ink-900">Envíos con comisión pendiente</legend>
          {loadError && <Alert tone="error">{loadError}</Alert>}
          {!rows && !loadError ? <SkeletonRows rows={3} /> : rows && (rows.length === 0 ? <p className="text-sm text-ink-500">Este transportista no tiene comisión pendiente.</p> : (
            <ul className="max-h-56 divide-y divide-steel-200 overflow-y-auto rounded-card border border-steel-200">
              {rows.map((r) => (
                <li key={r.shipmentId} className="flex items-center gap-3 px-3 py-2 text-sm">
                  <Checkbox id={`${uid}-${r.shipmentId}`} checked={picked.has(r.shipmentId!)} onCheckedChange={(c) => toggle(r.shipmentId!, c === true)} />
                  <div className="min-w-0 flex-1"><InlineLabel htmlFor={`${uid}-${r.shipmentId}`}>
                    <span className="font-mono text-xs">{r.code}</span> · {CITY_LABELS[r.originCity ?? ''] ?? r.originCity} → {CITY_LABELS[r.destinationCity ?? ''] ?? r.destinationCity}
                    <span className="block text-xs text-ink-500">Entregado {r.deliveredAt ? formatDate(limaDateOf(r.deliveredAt)) : '—'} · {r.requesterName}{r.status === 'DISPUTED' ? ' · en disputa' : ''}</span>
                  </InlineLabel></div>
                  <span className="font-mono text-sm tabular-nums">{formatMoney(due(r))}</span>
                </li>
              ))}
            </ul>
          ))}
          {errors.shipmentIds && <p id={`${uid}-sh-err`} role="alert" className="text-xs text-red-700">{errors.shipmentIds}</p>}
          <p className="flex items-baseline justify-between border-t border-steel-200 pt-2 text-sm"><span className="text-ink-700">Total a cobrar ({picked.size} {picked.size === 1 ? 'envío' : 'envíos'})</span><span className="money text-base" data-testid="commission-total">{formatMoney(total)}</span></p>
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Forma de pago" htmlFor={`${uid}-m`} required><Select id={`${uid}-m`} value={method} onValueChange={setMethod} options={PAYMENT_METHOD_OPTIONS} /></Field>
          <Field label="Número de operación" htmlFor={`${uid}-o`} error={errors.operationNumber} required><Input id={`${uid}-o`} value={operation} maxLength={40} onChange={(e) => setOperation(e.target.value)} aria-invalid={!!errors.operationNumber} autoComplete="off" /></Field>
          <Field label="Fecha del cobro" htmlFor={`${uid}-d`} error={errors.paidOn} required><DatePicker id={`${uid}-d`} value={paidOn} onValueChange={setPaidOn} max={limaToday()} invalid={!!errors.paidOn} /></Field>
          <Field label="Nota" htmlFor={`${uid}-n`} hint="Opcional." error={errors.note}><Input id={`${uid}-n`} value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy} disabled={!rows?.length}>{busy ? 'Registrando…' : 'Registrar cobro'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
