import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { ChevronRight, Download, FileCheck2, FilePlus2, History } from 'lucide-react';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { DatePicker } from '@/components/ui/date-picker';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { Can } from '@/lib/permissions/react';
import { formatDate, formatDateTime } from '@/lib/format';
import { limaToday } from '@/lib/lima-date';
import type { DriverView, VehicleView } from '@/lib/api/server';
import type { FleetDocument } from '../server';
import { DOC_LABEL, DOC_TYPES, LICENSE_CATEGORIES, VIGENCY_LABEL, VIGENCY_TONE, expiryText, requiresExpiry, type DocOwner, type DocType } from '../documents';

export type DocsTarget = { ownerType: DocOwner; id: string; title: string; subtitle?: string };

/** Estado (color + texto) y «Vence en 12 días» de un documento. El color nunca va solo. */
export function VigencyBadge({ doc }: { doc: Pick<FleetDocument, 'status' | 'expiresAt' | 'daysLeft'> }) {
  const st = doc.status ?? 'NO_EXPIRY';
  return <Badge tone={VIGENCY_TONE[st] ?? 'neutral'}>{VIGENCY_LABEL[st] ?? st}<span className="sr-only">. {expiryText(doc.status, doc.expiresAt, doc.daysLeft)}</span></Badge>;
}

function UploadForm({ target, type, onDone, onCancel }: { target: DocsTarget; type: DocType; onDone: () => void; onCancel: () => void }) {
  const needs = requiresExpiry(type);
  const [file, setFile] = useState<File | null>(null);
  const [number, setNumber] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const id = `doc-${type}`;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setFormError(null);
    const errs: Record<string, string> = {};
    if (!file) errs.file = 'Elige el archivo del documento.';
    if (needs && !expiresAt) errs.expiresAt = 'Indica la fecha de vencimiento.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const form = new FormData();
    form.set('ownerType', target.ownerType); form.set('ownerId', target.id); form.set('type', type); form.set('file', file!);
    if (number.trim()) form.set('number', number.trim());
    if (expiresAt) form.set('expiresAt', expiresAt);
    try {
      const res = await fetch('/flota/documentos/subir', { method: 'POST', body: form });
      if (res.ok) {
        toast.success('Documento subido', `${DOC_LABEL[type]} actualizado.`);
        onDone();
        return;
      }
      const body = await res.json().catch(() => null);
      const msg: string = body?.error?.message ?? 'No pudimos subir el documento. Inténtalo de nuevo.';
      const field: string | null = body?.error?.field ?? null;
      if (field === 'file' || field === 'expiresAt' || field === 'number') setErrors({ [field]: msg });
      else setFormError(msg);
    } catch {
      setFormError('No pudimos subir el documento. Revisa tu conexión e inténtalo de nuevo.');
    }
    setBusy(false);
  }

  return (
    <form onSubmit={submit} noValidate method="post" className="mt-3 space-y-3 rounded-input border border-steel-200 bg-steel-100/60 p-3" aria-label={`Subir ${DOC_LABEL[type]}`}>
      {formError && <Alert tone="error" title="No pudimos subir el documento">{formError}</Alert>}
      <Field label="Archivo" htmlFor={`${id}-file`} error={errors.file} hint="JPG, PNG o PDF. Reemplaza al vigente; el anterior queda en el historial."><FileDropzone id={`${id}-file`} file={file} onFileChange={(f) => { setFile(f); setErrors((x) => ({ ...x, file: '' })); }} /></Field>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Número" htmlFor={`${id}-number`} hint="Opcional" error={errors.number}><Input id={`${id}-number`} value={number} maxLength={40} onChange={(e) => setNumber(e.target.value)} className="font-mono" autoComplete="off" /></Field>
        <Field label="Vence el" htmlFor={`${id}-exp`} required={needs} hint={needs ? undefined : 'Opcional'} error={errors.expiresAt}>
          <DatePicker id={`${id}-exp`} value={expiresAt} onValueChange={(v) => { setExpiresAt(v); setErrors((x) => ({ ...x, expiresAt: '' })); }} placeholder="Elige la fecha" min={limaToday()} invalid={!!errors.expiresAt} clearable={!needs} />
        </Field>
      </div>
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" size="sm" onClick={onCancel} disabled={busy}>Volver</Button>
        <Button type="submit" size="sm" loading={busy}>{busy ? 'Subiendo documento…' : 'Subir documento'}</Button>
      </div>
    </form>
  );
}

function DriverData({ driver, onSaved }: { driver: DriverView; onSaved: (d: DriverView) => void }) {
  const [category, setCategory] = useState(driver.licenseCategory ?? '');
  const [dni, setDni] = useState(driver.dni ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (dni && !/^\d{8}$/.test(dni)) { setErrors({ dni: 'El DNI tiene 8 dígitos.' }); return; }
    setErrors({});
    setBusy(true);
    const { data, error } = await actions.fleet.patchDriver({ id: driver.id!, licenseCategory: category || undefined, dni: dni || undefined });
    setBusy(false);
    if (error) { const f = splitActionError(error); if (f.field === 'dni' || f.field === 'licenseCategory') setErrors({ [f.field]: f.message }); else toast.error(f.message); return; }
    toast.success('Datos del conductor guardados');
    onSaved(data as DriverView);
  }
  return (
    <section aria-labelledby="drv-data" className="rounded-card border border-steel-200 p-4">
      <h3 id="drv-data" className="text-sm font-semibold text-ink-900">Datos de licencia y DNI</h3>
      <Can permission="MANAGE_DRIVERS" fallback={
        <dl className="mt-2 grid grid-cols-2 gap-2 text-sm"><div><dt className="text-xs text-ink-500">Categoría</dt><dd className="font-mono">{driver.licenseCategory ?? '—'}</dd></div><div><dt className="text-xs text-ink-500">DNI</dt><dd className="font-mono">{driver.dni ?? '—'}</dd></div></dl>
      }>
        <form onSubmit={save} noValidate method="post" className="mt-2 grid gap-3 sm:grid-cols-2">
          <Field label="Categoría de licencia" htmlFor="drv-cat" error={errors.licenseCategory}><Select id="drv-cat" value={category} onValueChange={setCategory} options={LICENSE_CATEGORIES} placeholder="Elige la categoría" /></Field>
          <Field label="DNI" htmlFor="drv-dni" error={errors.dni}><Input id="drv-dni" value={dni} inputMode="numeric" maxLength={8} onChange={(e) => { setDni(e.target.value.replace(/\D/g, '')); setErrors({}); }} className="font-mono" autoComplete="off" aria-invalid={!!errors.dni} /></Field>
          <div className="sm:col-span-2 sm:text-right"><Button type="submit" size="sm" variant="outline" loading={busy}>{busy ? 'Guardando…' : 'Guardar datos'}</Button></div>
        </form>
      </Can>
    </section>
  );
}

/**
 * «Documentos» de un vehículo (SOAT, revisión técnica, tarjeta de propiedad, póliza, permiso MTC) o de un conductor
 * (licencia de conducir, DNI): vigente por tipo con su estado, subir uno nuevo (reemplaza al vigente; el anterior queda en el
 * historial) y descargar por el proxy autenticado `/flota/documentos/<id>`. Subir va bajo `<Can>`.
 */
export default function FleetDocumentsSheet({ target, driver, onClose, onChanged, onDriverSaved }: { target: DocsTarget | null; driver?: DriverView | null; onClose: () => void; onChanged: () => void; onDriverSaved?: (d: DriverView) => void }) {
  const [docs, setDocs] = useState<FleetDocument[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState<DocType | null>(null);

  const load = useCallback(async () => {
    if (!target) return;
    const { data, error: err } = await actions.fleet.documents({ ownerType: target.ownerType, id: target.id });
    if (err) { setError(splitActionError(err).message); setDocs([]); return; }
    setError(null);
    setDocs((data as FleetDocument[]) ?? []);
  }, [target?.ownerType, target?.id]);

  useEffect(() => { setDocs(null); setError(null); setUploading(null); if (target) load(); }, [target?.ownerType, target?.id]);

  const current = (t: string) => docs?.find((d) => d.type === t && d.current !== false);
  const history = (docs ?? []).filter((d) => d.current === false);

  return (
    <Sheet open={!!target} onOpenChange={(o) => !o && onClose()} title={target ? `Documentos · ${target.title}` : 'Documentos'} description={target?.subtitle ?? 'Documentos vigentes e historial.'}>
      {target && (
        <div className="space-y-4 p-5">
          {error && <Alert tone="error" title="No pudimos cargar los documentos">{error}</Alert>}
          {docs === null ? <div className="space-y-2"><Skeleton className="h-20 w-full" /><Skeleton className="h-20 w-full" /></div> : (
            <ul className="space-y-3" aria-label="Documentos vigentes">
              {DOC_TYPES[target.ownerType].map((t) => {
                const d = current(t);
                return (
                  <li key={t} className="rounded-card border border-steel-200 p-4" data-testid={`doc-${t}`}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <h3 className="flex items-center gap-1.5 text-sm font-semibold text-ink-900"><FileCheck2 className="size-4 text-petrol-700" aria-hidden="true" />{DOC_LABEL[t]}</h3>
                        {d ? (
                          <p className="mt-0.5 text-xs text-ink-700" data-testid="doc-expiry">{expiryText(d.status, d.expiresAt, d.daysLeft)}{d.expiresAt ? <> · <span className="font-mono">{formatDate(d.expiresAt)} {d.expiresAt.slice(0, 4)}</span></> : null}{d.number ? <> · N.º <span className="font-mono">{d.number}</span></> : null}</p>
                        ) : <p className="mt-0.5 text-xs text-ink-500">Aún no subiste este documento.</p>}
                      </div>
                      {d ? <VigencyBadge doc={d} /> : <Badge tone="neutral">Sin subir</Badge>}
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      {d && <Button asChild size="sm" variant="ghost"><a href={`/flota/documentos/${d.id}?download=1`}><Download aria-hidden="true" />Descargar<span className="sr-only"> {DOC_LABEL[t]}</span></a></Button>}
                      <Can>{uploading !== t && <Button size="sm" variant="outline" onClick={() => setUploading(t)}><FilePlus2 aria-hidden="true" />{d ? 'Subir uno nuevo' : 'Subir documento'}<span className="sr-only"> de {DOC_LABEL[t]}</span></Button>}</Can>
                    </div>
                    {uploading === t && <UploadForm target={target} type={t} onCancel={() => setUploading(null)} onDone={() => { setUploading(null); load(); onChanged(); }} />}
                  </li>
                );
              })}
            </ul>
          )}
          {target.ownerType === 'DRIVER' && driver && <DriverData driver={driver} onSaved={(d) => onDriverSaved?.(d)} />}
          {history.length > 0 && (
            <details className="group rounded-card border border-steel-200 px-4 py-3" data-testid="doc-history">
              <summary className="flex cursor-pointer list-none items-center gap-1.5 text-sm font-medium text-ink-900 [&::-webkit-details-marker]:hidden"><ChevronRight className="size-4 transition-transform group-open:rotate-90 motion-reduce:transition-none" aria-hidden="true" /><History className="size-4 text-ink-500" aria-hidden="true" />Historial ({history.length})</summary>
              <ul className="mt-2 divide-y divide-steel-200">
                {history.map((d) => (
                  <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 py-2 text-xs text-ink-700">
                    <span><span className="font-medium text-ink-900">{DOC_LABEL[d.type ?? ''] ?? d.typeLabel}</span>{d.expiresAt ? ` · vencía ${formatDate(d.expiresAt)} ${d.expiresAt.slice(0, 4)}` : ''} · subido {formatDateTime(d.createdAt)}</span>
                    <Button asChild size="sm" variant="ghost"><a href={`/flota/documentos/${d.id}?download=1`}><Download aria-hidden="true" />Descargar<span className="sr-only"> {DOC_LABEL[d.type ?? ''] ?? d.typeLabel} anterior</span></a></Button>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
    </Sheet>
  );
}
