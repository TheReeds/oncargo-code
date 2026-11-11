import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { useLiveRefresh } from '@/lib/realtime';
import { TriangleAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { SkeletonRows } from '@/components/ui/skeleton';
import { StatusPill } from '@/components/domain/StatusPill';
import { toast, flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { INCIDENT_CATEGORY_LABELS, formatDateTime } from '@/lib/format';
import { CATEGORY_OPTIONS } from '../lib';
import type { IncidentView } from '../server';

const MAX = 2000;

/** Pestaña "Incidencias" de un envío: lista, detalle y reporte (RN11: una activa congela el envío). */
export default function IncidentsPanel({ shipmentId, status, hasActiveIncident }: { shipmentId: string; perspective: 'PYME' | 'CARRIER'; status: string; hasActiveIncident?: boolean }) {
  const [list, setList] = useState<IncidentView[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [errors, setErrors] = useState<{ category?: string; description?: string }>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoadError(null);
    const { data, error } = await actions.incidents.forShipment({ shipmentId });
    if (error) { setLoadError(error.message); setList([]); return; }
    setList(data);
  }, [shipmentId]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], match: (e) => e.data.shipmentId === shipmentId, refresh: load, fallbackMs: 60_000 });

  const active = hasActiveIncident || !!list?.some((i) => i.active);
  const cancelled = status === 'CANCELLED';
  const blocked = active || cancelled;

  function reset() { setCategory(''); setDescription(''); setErrors({}); }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!category) next.category = 'Selecciona una categoría.';
    if (!description.trim()) next.description = 'Describe qué ocurrió.';
    setErrors(next);
    if (Object.keys(next).length) return;
    setBusy(true);
    const { error } = await actions.incidents.report({ shipmentId, category: category as never, description });
    setBusy(false);
    if (error) {
      const { message, field } = splitActionError(error);
      if (field === 'description' || field === 'category') setErrors({ [field]: message });
      else toast.error(message);
      return;
    }
    setOpen(false);
    reset();
    flash('Incidencia reportada');
    window.location.reload();
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-[15px] font-semibold text-ink-900">Incidencias del envío</h3>
          <p className="text-sm text-ink-500">Reporta daños, retrasos u otros problemas. Un operador de LOGYX te ayudará a resolverlos.</p>
        </div>
        <Button variant="destructive" onClick={() => { reset(); setOpen(true); }} disabled={blocked}><TriangleAlert /> Reportar incidencia</Button>
      </div>
      {active && <p className="text-sm text-ink-500">Ya hay una incidencia activa en este envío (LOGYX-INC-001). Podrás reportar otra cuando se resuelva.</p>}
      {cancelled && <p className="text-sm text-ink-500">Este envío fue cancelado, por eso no se pueden reportar incidencias.</p>}

      {loadError && <Alert tone="error" title="No pudimos cargar las incidencias" action={<Button variant="outline" size="sm" onClick={load}>Reintentar</Button>}>{loadError}</Alert>}
      {list === null ? <SkeletonRows rows={2} /> : list.length === 0 && !loadError ? (
        <p className="rounded-card border border-dashed border-steel-200 px-4 py-8 text-center text-sm text-ink-500">Este envío no tiene incidencias.</p>
      ) : (
        <ul className="space-y-3">
          {list.map((i) => (
            <li key={i.id} className="rounded-card border border-steel-200 bg-surface p-4">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span className="text-sm font-semibold text-ink-900">{INCIDENT_CATEGORY_LABELS[i.category!]}</span>
                <StatusPill status={i.status!} />
                <span className="text-xs text-ink-500">Reportada {formatDateTime(i.createdAt)}</span>
                <a href={`/incidencias/${i.id}`} className="ml-auto text-sm font-medium text-petrol-700 hover:underline">Ver detalle</a>
              </div>
              <p className="mt-2 line-clamp-2 text-sm text-ink-700">{i.description}</p>
              {i.resolutionNotes && <p className="mt-2 rounded-input bg-emerald-50 px-3 py-2 text-sm text-emerald-700"><span className="font-semibold">Resolución: </span>{i.resolutionNotes}</p>}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={open} onOpenChange={(o) => !busy && setOpen(o)} title="Reportar incidencia" description="Cuéntanos qué pasó. Mientras la incidencia esté activa, el conductor no podrá avanzar las paradas del envío.">
        <form onSubmit={submit} method="post" className="space-y-4" noValidate>
          <Field label="Categoría" htmlFor="inc-category" error={errors.category} required>
            <Select id="inc-category" value={category} onValueChange={setCategory} options={CATEGORY_OPTIONS} placeholder="Selecciona una categoría" invalid={!!errors.category} />
          </Field>
          <Field label="Descripción" htmlFor="inc-description" error={errors.description} required hint={`${description.length}/${MAX} caracteres`}>
            <Textarea id="inc-description" value={description} maxLength={MAX} rows={5} onChange={(e) => setDescription(e.target.value)} aria-invalid={!!errors.description} placeholder="Qué ocurrió, dónde y cuándo." />
          </Field>
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={busy}>Volver</Button>
            <Button type="submit" variant="destructive-solid" loading={busy}>{busy ? 'Reportando…' : 'Reportar incidencia'}</Button>
          </div>
        </form>
      </Dialog>
    </div>
  );
}
