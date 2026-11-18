import * as React from 'react';
import { Dialog } from './dialog';
import { Button } from './button';
import { Field } from './field';
import { Textarea } from './input';
import { Alert } from './alert';
import { RN07_FIELD_MESSAGE, isContactFailure, type ActionFailure } from '@/lib/action-error';

/**
 * Diálogo para acciones que piden un motivo (cancelar, rechazar, suspender, reasignar, reabrir…).
 * Sustituye a `confirm()` cuando hace falta texto: validación en línea (obligatorio, longitud), error de campo de la API
 * (`field` = `reasonField`; `LOGYX-NEG-001` muestra el texto de RN07) y un `Alert` para el resto de errores.
 *
 *  <ReasonDialog open onOpenChange title="Cancelar envío" required confirmLabel="Cancelar envío" tone="destructive"
 *    onSubmit={async (reason) => { const { error } = await actions.shipments.cancel({ id, reason }); return error ? splitActionError(error) : null; }} />
 *
 * `onSubmit` devuelve `null` si salió bien (el diálogo se cierra; si vas a recargar la página, hazlo dentro y devuelve `null`)
 * o el `ActionFailure` (de `splitActionError`). Con `children` puedes poner campos extra antes del motivo (vehículo, fecha…):
 * sus errores de campo se reciben en `onFailure` (devuelve `true` si los mostraste tú).
 */
export function ReasonDialog({
  open, onOpenChange, title, description, label = 'Motivo', hint, placeholder, required = false, minLength = 3, maxLength, rows = 4,
  confirmLabel, cancelLabel = 'Volver', tone = 'default', reasonField = 'reason', initialValue = '', hideReason = false, children, onSubmit, onFailure, canSubmit = true,
}: {
  open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: string; label?: string; hint?: string; placeholder?: string;
  required?: boolean; minLength?: number; maxLength: number; rows?: number; confirmLabel: string; cancelLabel?: string; tone?: 'default' | 'destructive';
  reasonField?: string; initialValue?: string; children?: React.ReactNode;
  /** Sin campo de motivo (solo los campos de `children`): para acciones donde el motivo depende del estado y aquí no aplica. */
  hideReason?: boolean;
  onSubmit: (reason: string) => Promise<ActionFailure | { message: string; field?: string | null; code?: string | null } | null | void>;
  onFailure?: (f: ActionFailure) => boolean | void;
  /** Validaciones de los campos extra: devuelve `false` para frenar el envío (tú muestras su error). */
  canSubmit?: boolean | (() => boolean);
}) {
  const id = React.useId();
  const ref = React.useRef<HTMLTextAreaElement>(null);
  const [reason, setReason] = React.useState(initialValue);
  const [error, setError] = React.useState<string | null>(null);
  const [formError, setFormError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  React.useEffect(() => {
    if (!open) return;
    setReason(initialValue); setError(null); setFormError(null); setBusy(false);
    if (!children && !hideReason) requestAnimationFrame(() => ref.current?.focus());
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const text = reason.trim();
    setFormError(null);
    if (required && text.length < minLength) { setError(`Indica el motivo (mínimo ${minLength} caracteres).`); ref.current?.focus(); return; }
    if (text.length > maxLength) { setError(`El motivo admite hasta ${maxLength} caracteres.`); ref.current?.focus(); return; }
    if (typeof canSubmit === 'function' ? !canSubmit() : !canSubmit) return;
    setError(null);
    setBusy(true);
    let failure: Awaited<ReturnType<typeof onSubmit>>;
    try { failure = await onSubmit(text); } finally { setBusy(false); }
    if (!failure) { onOpenChange(false); return; }
    const f: ActionFailure = { message: failure.message, field: failure.field ?? null, code: failure.code ?? null };
    if (f.field === reasonField) { setError(isContactFailure(f) ? RN07_FIELD_MESSAGE : f.message); ref.current?.focus(); return; }
    if (onFailure?.(f) === true) return;
    setFormError(f.message);
  }

  const over = reason.length > maxLength;
  return (
    <Dialog open={open} onOpenChange={(o) => !busy && onOpenChange(o)} title={title} description={description}>
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {formError && <Alert tone="error" title="No pudimos completar la acción">{formError}</Alert>}
        {children}
        {!hideReason && <>
        <Field label={label} htmlFor={`${id}-reason`} required={required} error={error} hint={hint ?? (required ? undefined : 'Opcional.')}>
          <Textarea
            ref={ref} id={`${id}-reason`} value={reason} rows={rows} placeholder={placeholder}
            aria-invalid={!!error || over || undefined} aria-describedby={error ? `${id}-reason-error` : `${id}-count`}
            onChange={(e) => { setReason(e.target.value); if (error) setError(null); }}
          />
        </Field>
        <p id={`${id}-count`} className={`-mt-2 text-right font-mono text-xs tabular-nums ${over ? 'text-red-600' : 'text-ink-500'}`}>{reason.length}/{maxLength}</p>
        </>}
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>{cancelLabel}</Button>
          <Button type="submit" variant={tone === 'destructive' ? 'destructive-solid' : 'default'} loading={busy}>{confirmLabel}</Button>
        </div>
      </form>
    </Dialog>
  );
}
