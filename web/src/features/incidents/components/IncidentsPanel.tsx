import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
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
import { toFormErrors } from '@/lib/action-error';
import { INCIDENT_CATEGORY_LABELS, formatDateTime } from '@/lib/format';
import { MoneyInput } from '@/components/ui/number-input';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Badge } from '@/components/ui/badge';
import { CATEGORY_OPTIONS, INFORMATIVE_CATEGORIES, SEVERITY_OPTIONS } from '../lib';
import type { IncidentView } from '../server';

const MAX = 2000;

/** Pestaña "Incidencias" de un envío: lista, detalle y reporte (RN11: una activa congela el envío). */
export default function IncidentsPanel({ shipmentId, status, hasActiveIncident }: { shipmentId: string; perspective: 'PYME' | 'CARRIER'; status: string; hasActiveIncident?: boolean }) {
  const canOperate = useCan('OPERATE');
  const [list, setList] = useState<IncidentView[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState('AUTO');
  const [claimed, setClaimed] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [key, setKey] = useState('');
  const [errors, setErrors] = useState<{ category?: string; description?: string; claimedAmount?: string; file?: string }>({});
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setLoadError(null);
    const { data, error } = await actions.incidents.forShipment({ shipmentId });
    if (error) { setLoadError(error.message); setList([]); return; }
    setList(data);
  }, [shipmentId]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], match: (e) => e.data.shipmentId === shipmentId, refresh: load, fallbackMs: 60_000 });

  // RN11 (V23): solo una activa que congela por envío; las informativas no bloquean el alta (la API responde INC-001).
  const holding = hasActiveIncident || !!list?.some((i) => i.holdsShipment);
  const cancelled = status === 'CANCELLED';
  const blocked = cancelled;

  function reset() {
    setCategory(''); setDescription(''); setSeverity('AUTO'); setClaimed(''); setFile(null); setErrors({});
    setKey(typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `inc-${Date.now()}-${Math.random().toString(36).slice(2)}`);
  }

  /** Sube el adjunto como documento del envío (tipo «Otro») y devuelve su id. */
  async function uploadAttachment(f: File): Promise<string | { error: string }> {
    const body = new FormData();
    body.set('shipmentId', shipmentId);
    body.set('type', 'OTHER');
    body.set('file', f, f.name);
    const res = await fetch('/documentos/subir', { method: 'POST', body }).catch(() => null);
    const payload = res ? await res.json().catch(() => null) : null;
    if (!res || !res.ok || !payload?.data?.id) return { error: payload?.error?.message ?? 'No pudimos subir el adjunto. Inténtalo de nuevo.' };
    return payload.data.id as string;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!category) next.category = 'Selecciona una categoría.';
    if (!description.trim()) next.description = 'Describe qué ocurrió.';
    setErrors(next);
    if (Object.keys(next).length) return;
    const claimedAmount = claimed ? Number(claimed) : undefined;
    if (claimed && !(claimedAmount! > 0)) { setErrors({ claimedAmount: 'Ingresa un monto mayor a 0.' }); return; }
    setBusy(true);
    let attachmentIds: string[] | undefined;
    if (file) {
      const up = await uploadAttachment(file);
      if (typeof up !== 'string') { setBusy(false); setErrors({ file: up.error }); return; }
      attachmentIds = [up];
    }
    const { error } = await actions.incidents.report({
      shipmentId, category: category as never, description, severity: severity === 'AUTO' ? undefined : (severity as never),
      claimedAmount, attachmentIds, idempotencyKey: key || undefined,
    });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['description', 'category', 'claimedAmount']); // RN07: `LOGYX-NEG-001` queda bajo la descripción
      if (Object.keys(r.errors).length) { setErrors(r.errors); document.getElementById(`inc-${Object.keys(r.errors)[0]}`)?.focus(); }
      else toast.error(r.formError ?? 'No pudimos reportar la incidencia.');
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
          <p className="text-sm text-ink-500">Reporta daños, retrasos u otros problemas. OnCargo te ayuda a resolverlos.</p>
        </div>
        {canOperate && <Button variant="destructive" onClick={() => { reset(); setOpen(true); }} disabled={blocked}><TriangleAlert /> Reportar incidencia</Button>}
      </div>
      <ReadOnlyNotice>No puedes reportar incidencias.</ReadOnlyNotice>
      {holding && <p className="text-sm text-ink-500">Hay una incidencia que detiene el viaje. Puedes reportar otras informativas (retraso, vía bloqueada, disputa de pago).</p>}
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
                {i.severity && <StatusPill status={i.severity} kind="severity" />}
                {i.blocking === false && <Badge tone="neutral">Informativa</Badge>}
                <span className="text-xs text-ink-500">Reportada {formatDateTime(i.createdAt)}</span>
                <a href={`/incidencias/${i.id}`} className="ml-auto text-sm font-medium text-petrol-700 hover:underline">Ver detalle</a>
              </div>
              <p className="mt-2 line-clamp-2 text-sm text-ink-700">{i.description}</p>
              {i.resolutionNotes && <p className="mt-2 rounded-input bg-emerald-50 px-3 py-2 text-sm text-emerald-700"><span className="font-semibold">Resolución: </span>{i.resolutionNotes}</p>}
            </li>
          ))}
        </ul>
      )}

      <Dialog open={open} onOpenChange={(o) => !busy && setOpen(o)} title="Reportar incidencia" description={category && INFORMATIVE_CATEGORIES.has(category) ? 'Es informativa: el viaje sigue mientras OnCargo la revisa.' : 'Cuéntanos qué pasó. Si detiene el viaje, el conductor no podrá avanzar las paradas hasta que OnCargo la resuelva.'}>
        <form onSubmit={submit} method="post" className="space-y-4" noValidate>
          <Field label="Categoría" htmlFor="inc-category" error={errors.category} required>
            <Select id="inc-category" value={category} onValueChange={setCategory} options={CATEGORY_OPTIONS} placeholder="Selecciona una categoría" invalid={!!errors.category} />
          </Field>
          <Field label="Descripción" htmlFor="inc-description" error={errors.description} required hint={`${description.length}/${MAX} caracteres`}>
            <Textarea id="inc-description" value={description} maxLength={MAX} rows={5} onChange={(e) => setDescription(e.target.value)} aria-invalid={!!errors.description} placeholder="Qué ocurrió, dónde y cuándo." />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Severidad" htmlFor="inc-severity" hint="Fija el plazo de respuesta de OnCargo.">
              <Select id="inc-severity" value={severity} onValueChange={setSeverity} options={[{ value: 'AUTO', label: 'Según el tipo' }, ...SEVERITY_OPTIONS]} />
            </Field>
            <Field label="Monto reclamado" htmlFor="inc-claimed" error={errors.claimedAmount} hint="Opcional.">
              <MoneyInput id="inc-claimed" value={claimed} onValueChange={setClaimed} invalid={!!errors.claimedAmount} />
            </Field>
          </div>
          <Field label="Adjunto" htmlFor="inc-file" error={errors.file} hint="Opcional. Foto o PDF, hasta 10 MB.">
            <FileDropzone id="inc-file" file={file} onFileChange={(f) => { setFile(f); setErrors((e) => ({ ...e, file: undefined })); }} disabled={busy} />
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
