import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { DatePicker } from '@/components/ui/date-picker';
import { Alert } from '@/components/ui/alert';
import { Checkbox, InlineLabel, RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { toast } from '@/components/ui/toaster';
import { splitActionError, type ActionFailure } from '@/lib/action-error';
import { formatDate } from '@/lib/format';
import { WEEKDAYS, firstPickup } from '../labels';
import type { TemplateView } from '../server';

const LEAD_OPTIONS = Array.from({ length: 15 }, (_, n) => ({ value: String(n), label: n === 0 ? 'El mismo día del recojo' : n === 1 ? '1 día antes' : `${n} días antes` }));
const FREQ_OPTIONS = [{ value: 'WEEKLY', label: 'Cada semana' }, { value: 'BIWEEKLY', label: 'Cada dos semanas' }];

/** Programa una plantilla: día de recojo, frecuencia, anticipación, fecha de fin, feriados y si publica sola o deja borrador (roadmap 2.1). */
export function ScheduleDialog({ template, today, onClose, onSaved }: { template: TemplateView; today: string; onClose: () => void; onSaved: () => void }) {
  const s = template.schedule;
  const [frequency, setFrequency] = useState(s?.frequency ?? 'WEEKLY');
  const [weekday, setWeekday] = useState(String(s?.pickupWeekday ?? 1));
  const [lead, setLead] = useState(String(s?.leadDays ?? 2));
  const [endsOn, setEndsOn] = useState(s?.endsOn ?? '');
  const [skip, setSkip] = useState(s?.skipHolidays ?? true);
  const [mode, setMode] = useState<string>(s?.mode ?? 'PUBLISH');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [failure, setFailure] = useState<ActionFailure | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { setFieldError(null); setFailure(null); }, [frequency, weekday, lead, endsOn]);

  const first = firstPickup(Number(weekday), Number(lead), today);
  const tooLate = !!endsOn && first > endsOn;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setFieldError(null); setFailure(null);
    if (endsOn && endsOn < today) { setFieldError('La fecha de fin ya pasó. Elige una de hoy en adelante.'); return; }
    if (tooLate) { setFieldError('El primer recojo cae después de la fecha de fin.'); return; }
    setBusy(true);
    const { error } = await actions.templates.schedule({ id: template.id!, frequency: frequency as 'WEEKLY', pickupWeekday: Number(weekday), leadDays: Number(lead), endsOn: endsOn || undefined, skipHolidays: skip, mode: mode as 'PUBLISH' });
    setBusy(false);
    if (error) {
      const f = splitActionError(error);
      if (f.field === 'endsOn') setFieldError(f.message); else setFailure(f);
      return;
    }
    toast.success(s ? 'Programación actualizada' : 'Plantilla programada');
    onSaved();
  }

  return (
    <Dialog open onOpenChange={(o) => !busy && !o && onClose()} title={`Programar «${template.name}»`} description="Se crea una solicitud por cada recojo. Los precios y las reglas se evalúan al publicar." className="max-w-xl">
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        {failure && <Alert tone="error" title="No pudimos guardar la programación">{failure.message}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Frecuencia" htmlFor="sch-frequency"><Select id="sch-frequency" value={frequency} onValueChange={(v) => setFrequency(v as 'WEEKLY')} options={FREQ_OPTIONS} /></Field>
          <Field label="Día de recojo" htmlFor="sch-weekday"><Select id="sch-weekday" value={weekday} onValueChange={setWeekday} options={WEEKDAYS.map((d) => ({ value: d.value, label: d.label }))} /></Field>
          <Field label="Publicar" htmlFor="sch-lead"><Select id="sch-lead" value={lead} onValueChange={setLead} options={LEAD_OPTIONS} /></Field>
          <Field label="Termina el" htmlFor="sch-ends" error={fieldError} hint="Opcional. Sin fecha, sigue hasta que la pauses.">
            <DatePicker id="sch-ends" value={endsOn} onValueChange={setEndsOn} min={today} clearable invalid={!!fieldError} placeholder="Sin fecha de fin" />
          </Field>
        </div>
        <fieldset className="space-y-2">
          <legend className="text-[13px] font-medium text-ink-900">Al llegar la fecha</legend>
          <RadioGroup value={mode} onValueChange={setMode} aria-label="Al llegar la fecha">
            <div className="flex items-start gap-2"><RadioGroupItem id="sch-mode-publish" value="PUBLISH" className="mt-0.5" /><div><InlineLabel htmlFor="sch-mode-publish">Publicar la solicitud</InlineLabel><p className="text-xs text-ink-500">Los transportistas ya pueden ofertar. Te avisamos.</p></div></div>
            <div className="flex items-start gap-2"><RadioGroupItem id="sch-mode-draft" value="DRAFT" className="mt-0.5" /><div><InlineLabel htmlFor="sch-mode-draft">Dejar un borrador</InlineLabel><p className="text-xs text-ink-500">Lo revisas y lo publicas tú desde esta página.</p></div></div>
          </RadioGroup>
        </fieldset>
        <div className="flex items-center gap-2">
          <Checkbox id="sch-skip" checked={skip} onCheckedChange={(c) => setSkip(c === true)} />
          <InlineLabel htmlFor="sch-skip">Saltar los feriados nacionales</InlineLabel>
        </div>
        <p className="rounded-input bg-petrol-50 px-3 py-2 text-sm text-petrol-800" aria-live="polite">
          Primer recojo: <span className="font-mono font-semibold">{formatDate(first)}</span>. Si falla una publicación te avisamos y no se reintenta; tras 3 fallos seguidos la programación se pausa.
        </p>
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>{s ? 'Guardar programación' : 'Programar plantilla'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
