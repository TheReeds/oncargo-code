import { useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Card, CardBody, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { InlineLabel, RadioGroup, RadioGroupItem, Switch } from '@/components/ui/form-controls';
import { TimeSelect, timeOptions } from '@/components/ui/time-select';
import { Select } from '@/components/ui/select';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';

export type EmailSettings = {
  emailEnabled: boolean;
  mode: 'IMMEDIATE' | 'DAILY';
  digestHour: number;
  quietFrom: string | null;
  quietTo: string | null;
  unsubscribedAt?: string | null;
};

const HOURS = timeOptions(60).map((o, i) => ({ value: String(i), label: o.label }));
/** La API devuelve `22:00:00`; los selectores trabajan con `22:00`. */
const hhmm = (t?: string | null) => (t ? t.slice(0, 5) : null);
const normalize = (s: EmailSettings): EmailSettings => ({ ...s, quietFrom: hhmm(s.quietFrom), quietTo: hhmm(s.quietTo) });
const same = (a: EmailSettings, b: EmailSettings) => a.emailEnabled === b.emailEnabled && a.mode === b.mode
  && a.digestHour === b.digestHour && a.quietFrom === b.quietFrom && a.quietTo === b.quietTo;

/**
 * `/cuenta/avisos` (V24, 2.2): cómo llegan los correos de la persona, encima de la matriz categoría × canal. Correo
 * activo, al momento o en un resumen diario a la hora elegida, y horario de silencio (hora de Lima; puede cruzar la
 * medianoche: los correos esperan hasta que termine). Cualquier rol, también Lector: es su propia cuenta.
 */
export default function EmailSettingsCard({ initial }: { initial: EmailSettings }) {
  const [saved, setSaved] = useState(() => normalize(initial));
  const [v, setV] = useState(() => normalize(initial));
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<{ quietFrom?: string; quietTo?: string; form?: string }>({});
  const dirty = useMemo(() => !same(v, saved), [v, saved]);
  const quiet = v.quietFrom !== null || v.quietTo !== null;
  const set = (patch: Partial<EmailSettings>) => { setV((p) => ({ ...p, ...patch })); setErrors({}); };

  async function save() {
    if (quiet && (!v.quietFrom || !v.quietTo)) {
      setErrors({ [v.quietFrom ? 'quietTo' : 'quietFrom']: 'Elige la hora de inicio y la de fin.' });
      return;
    }
    if (quiet && v.quietFrom === v.quietTo) { setErrors({ quietTo: 'El silencio debe terminar a otra hora.' }); return; }
    setBusy(true);
    const { data, error } = await actions.notifications.updateEmailSettings({ emailEnabled: v.emailEnabled, mode: v.mode, digestHour: v.digestHour, quietFrom: v.quietFrom, quietTo: v.quietTo });
    setBusy(false);
    if (error || !data) {
      const { message, field } = error ? splitActionError(error) : { message: 'No pudimos guardar tus correos.', field: null };
      setErrors(field === 'quietFrom' || field === 'quietTo' ? { [field]: message } : { form: message });
      return;
    }
    const next = normalize(data as EmailSettings);
    setSaved(next);
    setV(next);
    toast.success('Correos guardados');
  }

  return (
    <Card className="min-w-0" data-testid="email-settings">
      <CardHeader>
        <CardTitle>Correo</CardTitle>
        <CardDescription>Se aplica a todos los avisos que marques «Por correo». Los de tu contraseña e invitaciones llegan siempre.</CardDescription>
      </CardHeader>
      <CardBody className="space-y-5">
        {saved.unsubscribedAt && !saved.emailEnabled && (
          <Alert tone="info">Te diste de baja con el enlace de un correo. Activa el correo para volver a recibir avisos.</Alert>
        )}
        <div className="flex items-start gap-3">
          <Switch id="email-enabled" checked={v.emailEnabled} onCheckedChange={(c) => set({ emailEnabled: c })} className="mt-0.5" />
          <div>
            <InlineLabel htmlFor="email-enabled">Recibir avisos por correo</InlineLabel>
            <p className="text-xs text-ink-500">Apagado, los avisos solo llegan a la app.</p>
          </div>
        </div>

        {v.emailEnabled && (
          <>
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium text-ink-900">Cuándo</legend>
              <RadioGroup value={v.mode} onValueChange={(m) => set({ mode: m as EmailSettings['mode'] })} aria-label="Cuándo llegan los correos">
                <div className="flex items-start gap-2"><RadioGroupItem id="mode-immediate" value="IMMEDIATE" className="mt-0.5" /><div><InlineLabel htmlFor="mode-immediate">Al momento</InlineLabel><p className="text-xs text-ink-500">Un correo por aviso.</p></div></div>
                <div className="flex items-start gap-2"><RadioGroupItem id="mode-daily" value="DAILY" className="mt-0.5" /><div><InlineLabel htmlFor="mode-daily">Resumen diario</InlineLabel><p className="text-xs text-ink-500">Un solo correo al día con todos tus avisos.</p></div></div>
              </RadioGroup>
              {v.mode === 'DAILY' && (
                <Field label="Hora del resumen" htmlFor="digest-hour" hint="Hora de Lima." className="max-w-48 pl-6">
                  <Select id="digest-hour" value={String(v.digestHour)} onValueChange={(h) => set({ digestHour: Number(h) })} options={HOURS} />
                </Field>
              )}
            </fieldset>

            <div className="space-y-3">
              <div className="flex items-start gap-3">
                <Switch id="quiet-enabled" checked={quiet} onCheckedChange={(c) => set(c ? { quietFrom: '22:00', quietTo: '07:00' } : { quietFrom: null, quietTo: null })} className="mt-0.5" />
                <div>
                  <InlineLabel htmlFor="quiet-enabled">Horario de silencio</InlineLabel>
                  <p className="text-xs text-ink-500">En ese horario no te escribimos; los correos llegan juntos cuando termina.</p>
                </div>
              </div>
              {quiet && (
                <div className="grid max-w-sm grid-cols-2 gap-3 pl-12">
                  <Field label="Desde" htmlFor="quiet-from" error={errors.quietFrom}>
                    <TimeSelect id="quiet-from" value={v.quietFrom ?? ''} onValueChange={(t) => set({ quietFrom: t || null })} invalid={!!errors.quietFrom} />
                  </Field>
                  <Field label="Hasta" htmlFor="quiet-to" error={errors.quietTo}>
                    <TimeSelect id="quiet-to" value={v.quietTo ?? ''} onValueChange={(t) => set({ quietTo: t || null })} invalid={!!errors.quietTo} />
                  </Field>
                </div>
              )}
            </div>
          </>
        )}
        {errors.form && <Alert tone="error">{errors.form}</Alert>}
        <div className="flex justify-end gap-2 border-t border-steel-200 pt-4">
          <Button variant="ghost" onClick={() => { setV(saved); setErrors({}); }} disabled={!dirty || busy}>Descartar</Button>
          <Button onClick={save} loading={busy} disabled={!dirty}>{busy ? 'Guardando…' : 'Guardar correos'}</Button>
        </div>
      </CardBody>
    </Card>
  );
}
