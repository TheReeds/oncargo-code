import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { DatePicker } from '@/components/ui/date-picker';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { isVerificationFailure, splitActionError, type ActionFailure } from '@/lib/action-error';
import { CITY_LABELS } from '@/lib/format';
import type { TemplateView } from '../server';

/** «Publicar ahora»: elige la fecha de recojo y crea la solicitud desde la plantilla. Con Idempotency-Key: un doble clic no publica dos. */
export function PublishTemplateDialog({ template, today, onClose }: { template: TemplateView; today: string; onClose: () => void }) {
  const [date, setDate] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [failure, setFailure] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);
  const key = useRef('');
  useEffect(() => { key.current = crypto.randomUUID(); }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setFieldError(null); setFailure(null);
    if (!date) { setFieldError('Selecciona la fecha de recojo.'); return; }
    if (date < today) { setFieldError('Elige una fecha de hoy en adelante.'); return; }
    setBusy(true);
    const { data, error } = await actions.templates.publish({ id: template.id!, pickupDate: date, idempotencyKey: key.current });
    if (error) {
      setBusy(false);
      const f = splitActionError(error);
      if (f.field === 'pickupDate') setFieldError(f.message); else setFailure(f);
      return;
    }
    flash('Solicitud publicada', 'success');
    window.location.href = `/pyme/solicitudes/${(data as { requestId?: string }).requestId}`;
  }

  return (
    <Dialog open onOpenChange={(o) => !busy && !o && onClose()} title="Publicar solicitud"
      description={`${template.name} · ${CITY_LABELS[template.originCity!]} → ${CITY_LABELS[template.destinationCity!]}`}>
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {failure && (isVerificationFailure(failure) ? <VerificationBlock failure={failure} /> : <Alert tone="error" title="No pudimos publicar la solicitud">{failure.message}</Alert>)}
        <Field label="Fecha de recojo" htmlFor="pub-date" error={fieldError}>
          <DatePicker id="pub-date" value={date} onValueChange={(v) => { setDate(v); setFieldError(null); }} min={today} invalid={!!fieldError} />
        </Field>
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>Publicar solicitud</Button>
        </div>
      </form>
    </Dialog>
  );
}
