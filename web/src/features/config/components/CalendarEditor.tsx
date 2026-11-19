import { useState } from 'react';
import { actions } from 'astro:actions';
import { CalendarOff, Plus, Trash2 } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { Input, Textarea } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { MultiSelect } from '@/components/ui/combobox';
import { DatePicker } from '@/components/ui/date-picker';
import { Checkbox, InlineLabel } from '@/components/ui/form-controls';
import { Dialog } from '@/components/ui/dialog';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { CITY_LABELS, formatDate } from '@/lib/format';
import type { CalendarDay } from '../server';

const KIND_TONE: Record<string, 'petrol' | 'amber' | 'neutral'> = { HOLIDAY: 'petrol', STRIKE: 'amber', OTHER: 'neutral' };
const CITY_OPTIONS = Object.entries(CITY_LABELS).map(([value, label]) => ({ value, label }));

type Draft = { day: string; kind: string; name: string; cities: string[]; skipRecurrence: boolean; note: string };
const EMPTY: Draft = { day: '', kind: 'HOLIDAY', name: '', cities: [], skipRecurrence: true, note: '' };

/**
 * Calendario de feriados, paros y avisos (2.14). Lo ven todos (banner y aviso al elegir la fecha de recojo); las cargas
 * recurrentes que saltan feriados no se publican para recoger un día marcado. Solo el operador agrega o quita.
 */
export default function CalendarEditor({ days, today }: { days: CalendarDay[]; today: string }) {
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState<CalendarDay | null>(null);

  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => { setDraft((d) => (d ? { ...d, [k]: v } : d)); setErrors((e) => ({ ...e, [k]: '' })); };

  async function save() {
    if (!draft) return;
    const e: Record<string, string> = {};
    if (!draft.day) e.day = 'Elige la fecha.';
    if (!draft.name.trim()) e.name = 'Indica el nombre.';
    setErrors(e);
    if (Object.keys(e).length) return;
    setSaving(true);
    const { error } = await actions.config.addCalendarDay({
      day: draft.day, kind: draft.kind as 'HOLIDAY' | 'STRIKE' | 'OTHER', name: draft.name.trim(), cities: draft.cities,
      skipRecurrence: draft.skipRecurrence, note: draft.note.trim() || undefined,
    });
    setSaving(false);
    if (error) {
      const f = splitActionError(error);
      setErrors({ [f.field ?? 'form']: f.message });
      return;
    }
    flash('Día agregado al calendario', 'success');
    window.location.reload();
  }

  async function remove(reason: string) {
    if (!removing) return null;
    const { error } = await actions.config.removeCalendarDay({ id: removing.id!, reason });
    if (error) return splitActionError(error);
    flash('Día quitado del calendario', 'success');
    window.location.reload();
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>Próximos 12 meses</CardTitle>
          <CardDescription>Las cargas recurrentes que saltan feriados no se publican para esos días.</CardDescription>
        </div>
        <Button size="sm" onClick={() => { setDraft({ ...EMPTY }); setErrors({}); }}><Plus aria-hidden="true" /> Agregar día</Button>
      </CardHeader>
      {days.length === 0 ? (
        <EmptyState compact icon={CalendarOff} title="Sin feriados cargados" description="Mientras no haya feriados de un año, rige la lista nacional." />
      ) : (
        <Table label="Calendario de feriados y paros" stack dense>
          <thead><tr><Th>Fecha</Th><Th>Nombre</Th><Th>Alcance</Th><Th>Plantillas</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
          <tbody>
            {days.map((d) => (
              <Tr key={d.id} data-testid="calendar-row">
                <Td primary className="whitespace-nowrap"><span className="font-mono">{formatDate(d.day)}</span></Td>
                <Td label="Nombre"><span className="mr-2 text-ink-900">{d.name}</span><Badge tone={KIND_TONE[d.kind ?? 'OTHER']}>{d.kindLabel}</Badge>{d.note ? <span className="block text-xs text-ink-500">{d.note}</span> : null}</Td>
                <Td label="Alcance">{d.national ? 'Todo el país' : (d.cities ?? []).map((c) => CITY_LABELS[c] ?? c).join(', ')}</Td>
                <Td label="Plantillas">{d.skipRecurrence ? 'No publica' : 'Publica'}</Td>
                <Td label="Acciones"><Button variant="ghost" size="sm" onClick={() => setRemoving(d)}><Trash2 aria-hidden="true" /> Quitar<span className="sr-only"> {d.name} del {formatDate(d.day)}</span></Button></Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}

      <Dialog open={!!draft} onOpenChange={(o) => !o && setDraft(null)} title="Agregar al calendario"
        description="Un feriado, un paro anunciado o un aviso para un día."
        footer={<><Button variant="outline" onClick={() => setDraft(null)}>Volver</Button><Button onClick={save} disabled={saving}>{saving ? 'Guardando…' : 'Agregar'}</Button></>}>
        {draft && (
          <div className="space-y-4">
            {errors.form && <p role="alert" className="text-sm text-red-600">{errors.form}</p>}
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Fecha" htmlFor="cal-day" error={errors.day || null}>
                <DatePicker id="cal-day" value={draft.day} onValueChange={(v) => set('day', v)} min={today} invalid={!!errors.day} />
              </Field>
              <Field label="Tipo" htmlFor="cal-kind">
                <Select id="cal-kind" value={draft.kind} onValueChange={(v) => set('kind', v)}
                  options={[{ value: 'HOLIDAY', label: 'Feriado' }, { value: 'STRIKE', label: 'Paro' }, { value: 'OTHER', label: 'Aviso' }]} />
              </Field>
            </div>
            <Field label="Nombre" htmlFor="cal-name" error={errors.name || null} hint="Se muestra en el aviso.">
              <Input id="cal-name" value={draft.name} maxLength={120} onChange={(e) => set('name', e.target.value)} aria-invalid={!!errors.name || undefined} placeholder="Ej. Paro de transportistas en Puno" />
            </Field>
            <Field label="Ciudades afectadas" htmlFor="cal-cities" hint="Vacío = todo el país." error={errors.cities || null}>
              <MultiSelect id="cal-cities" value={draft.cities} onValueChange={(v) => set('cities', v)} options={CITY_OPTIONS} placeholder="Todo el país" />
            </Field>
            <div className="flex items-center gap-2">
              <Checkbox id="cal-skip" checked={draft.skipRecurrence} onCheckedChange={(v) => set('skipRecurrence', v === true)} />
              <InlineLabel htmlFor="cal-skip">Las cargas recurrentes no se publican para ese día</InlineLabel>
            </div>
            <Field label="Nota" htmlFor="cal-note" hint="Opcional." error={errors.note || null}>
              <Textarea id="cal-note" rows={2} maxLength={300} value={draft.note} onChange={(e) => set('note', e.target.value)} />
            </Field>
          </div>
        )}
      </Dialog>

      <ReasonDialog
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title={removing ? `Quitar «${removing.name}»` : 'Quitar día'}
        description={removing ? `${formatDate(removing.day)}. Deja de verse y las plantillas vuelven a publicar ese día.` : undefined}
        required minLength={3} maxLength={300} label="Motivo" confirmLabel="Quitar" tone="destructive"
        onSubmit={remove}
      />
    </Card>
  );
}
