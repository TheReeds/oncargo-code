import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { DatePicker } from '@/components/ui/date-picker';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { formatDate } from '@/lib/format';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { isVerificationFailure, type ActionFailure } from '@/lib/action-error';

const todayLima = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());

/**
 * Reabrir la subasta que cerró por tiempo (F07, `AUC-003`) o republicar una solicitud cancelada (RF18, `REQ-004`).
 * La fecha de recojo nueva es opcional; si ya pasó, la API responde `REQ-003` y se muestra bajo el campo.
 * Reabrir recarga la página; republicar navega a la solicitud nueva.
 */
export function RescheduleDialog({ mode, id, open, onOpenChange, currentPickup }: { mode: 'reopen' | 'republish'; id: string; open: boolean; onOpenChange: (o: boolean) => void; currentPickup?: string | null }) {
  const [date, setDate] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [failure, setFailure] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const reopen = mode === 'reopen';
  const today = todayLima();
  const stale = !!currentPickup && currentPickup < today;

  useEffect(() => { if (open) { setDate(''); setFieldError(null); setFailure(null); setBusy(false); } }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setFieldError(null);
    setFailure(null);
    if (date && date < today) { setFieldError('Elige una fecha de hoy en adelante.'); return; }
    if (stale && !date) { setFieldError('La fecha de recojo ya pasó. Elige una nueva.'); return; }
    setBusy(true);
    const { data, error } = reopen ? await actions.requests.reopen({ id, pickupDate: date || undefined }) : await actions.requests.republish({ id, pickupDate: date || undefined });
    if (error) {
      setBusy(false);
      const f = splitActionError(error);
      if (f.code === 'LOGYX-REQ-003' || f.field === 'pickupDate') setFieldError(f.message);
      else setFailure(f);
      return;
    }
    if (reopen) { flash('Subasta reabierta', 'success'); window.location.reload(); }
    else { flash('Solicitud republicada', 'success'); window.location.href = `/pyme/solicitudes/${(data as { id?: string }).id}`; }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !busy && onOpenChange(o)}
      title={reopen ? 'Reabrir la subasta' : 'Republicar la solicitud'}
      description={reopen
        ? 'La subasta vuelve a abrirse con una duración nueva y los transportistas podrán seguir ofertando. Las ofertas que ya recibiste se mantienen.'
        : 'Crearemos una solicitud nueva con los mismos datos y una subasta abierta. La cancelada se conserva como historial.'}
    >
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {failure && (isVerificationFailure(failure) ? <VerificationBlock failure={failure} /> : <Alert tone="error" title={reopen ? 'No pudimos reabrir la subasta' : 'No pudimos republicar la solicitud'}>{failure.message}</Alert>)}
        {stale && <Alert tone="warning">La fecha de recojo original ({formatDate(currentPickup)}) ya pasó: elige una nueva para continuar.</Alert>}
        <Field label="Nueva fecha de recojo" htmlFor="rs-date" error={fieldError} hint={stale ? undefined : 'Opcional. Si no eliges una, se mantiene la fecha actual.'}>
          <DatePicker id="rs-date" value={date} onValueChange={(v) => { setDate(v); setFieldError(null); }} min={today} clearable invalid={!!fieldError} placeholder="Mantener la fecha actual" />
        </Field>
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>{reopen ? 'Reabrir subasta' : 'Republicar solicitud'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
