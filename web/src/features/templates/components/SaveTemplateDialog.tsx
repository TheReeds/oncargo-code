import { useEffect, useState } from 'react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { Checkbox, InlineLabel } from '@/components/ui/form-controls';
import { LIMITS } from '@/lib/limits';
import type { ActionFailure } from '@/lib/action-error';

/**
 * Pide el nombre de una plantilla nueva (y si debe invitar a los transportistas preferidos al publicar). Lo usan
 * «Guardar como plantilla» del formulario y del detalle de la solicitud; `onSave` hace la llamada y devuelve el fallo.
 */
export function SaveTemplateDialog({ open, onOpenChange, onSave, suggested = '' }: {
  open: boolean; onOpenChange: (o: boolean) => void; suggested?: string;
  onSave: (name: string, invitePreferred: boolean) => Promise<ActionFailure | null>;
}) {
  const [name, setName] = useState(suggested);
  const [invite, setInvite] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (open) { setName(suggested); setInvite(false); setError(null); setFormError(null); setBusy(false); } }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) { setError('Ponle un nombre a la plantilla.'); return; }
    setError(null); setFormError(null); setBusy(true);
    const failure = await onSave(name.trim(), invite);
    setBusy(false);
    if (!failure) { onOpenChange(false); return; }
    if (failure.field === 'name' || failure.code === 'LOGYX-TPL-001') setError(failure.message); else setFormError(failure.message);
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !busy && onOpenChange(o)} title="Guardar como plantilla" description="Se guardan la ruta, la carga y los contactos. La fecha de recojo se elige al publicar.">
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {formError && <Alert tone="error" title="No pudimos guardar la plantilla">{formError}</Alert>}
        <Field label="Nombre de la plantilla" htmlFor="tpl-name" error={error} hint="Ej.: Quinua Juliaca a Arequipa, 3 t">
          <Input id="tpl-name" autoComplete="off" maxLength={LIMITS.templateName} value={name} onChange={(e) => { setName(e.target.value); setError(null); }} aria-invalid={!!error} autoFocus />
        </Field>
        <div className="flex items-center gap-2">
          <Checkbox id="tpl-invite" checked={invite} onCheckedChange={(c) => setInvite(c === true)} />
          <InlineLabel htmlFor="tpl-invite">Avisar a mis transportistas preferidos al publicar</InlineLabel>
        </div>
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>Guardar plantilla</Button>
        </div>
      </form>
    </Dialog>
  );
}
