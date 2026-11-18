import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { toFormErrors } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { MoneyInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { formatMoney } from '@/lib/format';
import { notifyNegotiationChanged } from '../usePolling';

/**
 * Propone un nuevo monto sobre una oferta pendiente. La PYME (RF27) o el transportista (RF26) pueden hacerlo: hay una sola
 * contra-oferta vigente por oferta, así que si la otra parte ya había propuesto una, esta la reemplaza (`replacesTheirs`).
 */
export function CounterOfferDialog({ open, onOpenChange, bidId, perspective = 'PYME', carrierName, currentAmount, floor, replacesTheirs, onDone }: { open: boolean; onOpenChange: (o: boolean) => void; bidId: string; perspective?: 'PYME' | 'CARRIER'; carrierName?: string; currentAmount: number; floor?: number | null; replacesTheirs?: boolean; onDone: () => void }) {
  const [amount, setAmount] = useState('');
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (open) { setAmount(''); setMessage(''); setErrors({}); setFormError(null); setBusy(false); }
  }, [open, bidId]);

  const amt = Number(amount);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    const errs: Record<string, string> = {};
    if (!(amt > 0)) errs.amount = 'Ingresa el monto de tu contra-oferta.';
    else if (floor && amt < floor) errs.amount = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube la contra-oferta para continuar.`;
    else if (amt === currentAmount) errs.amount = `Es el mismo monto de la oferta (${formatMoney(currentAmount)}). Propón un monto distinto.`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { error } = await actions.negotiation.propose({ bidId, amount: amt, message: message.trim() || undefined });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['amount', 'message']);
      setErrors(r.errors);
      setFormError(r.formError);
      return;
    }
    toast.success('Contra-oferta enviada');
    notifyNegotiationChanged();
    onOpenChange(false);
    onDone();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !busy && onOpenChange(o)}
      title={perspective === 'CARRIER' ? 'Proponer un nuevo monto' : 'Hacer una contra-oferta'}
      description={perspective === 'CARRIER'
        ? `Tu oferta actual es ${formatMoney(currentAmount)}. Propón otro monto a la PYME y espera su respuesta: si lo acepta, tu oferta pasa a ese monto; adjudicar sigue siendo decisión de la PYME.`
        : `${carrierName ?? 'El transportista'} ofertó ${formatMoney(currentAmount)}. Propón un monto y espera su respuesta.`}
    >
      <form onSubmit={submit} noValidate method="post" className="space-y-4" id="counter-form">
        {formError && <Alert tone="error" title="No pudimos enviar la contra-oferta">{formError}</Alert>}
        {replacesTheirs && <Alert tone="info">{perspective === 'CARRIER' ? 'Tu propuesta reemplaza la que te hizo la PYME: esa quedará rechazada.' : 'Tu propuesta reemplaza la que hizo el transportista: esa quedará rechazada.'}</Alert>}
        <Field label="Monto que propones" htmlFor="co-amount" error={errors.amount} hint={floor ? `Mínimo ${formatMoney(floor)} (piso de la subasta).` : undefined}>
          <MoneyInput id="co-amount" value={amount} onValueChange={(v) => { setAmount(v); setErrors((x) => ({ ...x, amount: '' })); }} invalid={!!errors.amount} placeholder={String(currentAmount)} className="h-12 text-lg" />
        </Field>
        <Field label="Mensaje" htmlFor="co-message" error={errors.message} hint={`Opcional. Máximo ${LIMITS.counterOfferMessage} caracteres.`}>
          <Textarea id="co-message" value={message} maxLength={LIMITS.counterOfferMessage} aria-invalid={!!errors.message || undefined} onChange={(e) => { setMessage(e.target.value); setErrors((x) => ({ ...x, message: '' })); }} placeholder="Ej. Si puedes cargar el mismo día, cerramos hoy." />
        </Field>
        <div className="flex justify-end gap-2 border-t border-steel-200 pt-3">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy}>{busy ? 'Enviando…' : 'Enviar contra-oferta'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
