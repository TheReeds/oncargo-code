import { useState } from 'react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { toast } from '@/components/ui/toaster';
import { DOCUMENT_TYPE_LABELS } from '@/lib/format';

const TYPE_OPTIONS = ['REMISSION_GUIDE', 'SERVICE_ORDER', 'RECEIPT', 'OTHER'].map((v) => ({ value: v, label: DOCUMENT_TYPE_LABELS[v] }));

/** Subida de documentos de las partes (guía de remisión, orden de servicio, comprobante, otro). */
export function DocumentUpload({ shipmentId, cancelled, onUploaded }: { shipmentId: string; cancelled: boolean; onUploaded: () => void }) {
  const [type, setType] = useState('REMISSION_GUIDE');
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<{ file?: string; type?: string }>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!type) return setErrors({ type: 'Elige el tipo de documento.' });
    if (!file) return setErrors({ file: 'Elige el archivo que quieres subir.' });
    setErrors({});
    setBusy(true);
    const form = new FormData();
    form.set('shipmentId', shipmentId);
    form.set('type', type);
    form.set('file', file);
    try {
      const res = await fetch('/documentos/subir', { method: 'POST', body: form });
      if (res.ok) {
        toast.success('Documento subido', 'La otra parte ya puede verlo en este envío.');
        setFile(null);
        onUploaded();
      } else {
        const body = await res.json().catch(() => null);
        const msg: string = body?.error?.message ?? 'No pudimos subir el documento. Inténtalo de nuevo.';
        const field: string | null = body?.error?.field ?? null;
        if (field === 'file' || field === 'type') setErrors({ [field]: msg });
        else toast.error(msg);
      }
    } catch {
      toast.error('No pudimos subir el documento. Revisa tu conexión e inténtalo de nuevo.');
    }
    setBusy(false);
  }

  return (
    <Card>
      <CardHeader><div><CardTitle>Subir un documento</CardTitle><CardDescription>PDF, JPG o PNG de hasta 10 MB. La otra parte lo verá en este envío.</CardDescription></div></CardHeader>
      <CardBody>
        {cancelled ? (
          <Alert tone="info" title="Este envío fue cancelado">Ya no se pueden subir documentos. Los que ya existían siguen disponibles para consulta.</Alert>
        ) : (
          <form method="post" onSubmit={submit} noValidate className="space-y-4" aria-label="Subir documento">
            <Field label="Tipo de documento" htmlFor={`doc-type-${shipmentId}`} error={errors.type} required>
              <Select id={`doc-type-${shipmentId}`} value={type} onValueChange={(v) => { setType(v); setErrors((x) => ({ ...x, type: undefined })); }} options={TYPE_OPTIONS} invalid={!!errors.type} />
            </Field>
            <Field label="Archivo" htmlFor={`doc-file-${shipmentId}`} required>
              <FileDropzone id={`doc-file-${shipmentId}`} file={file} onFileChange={(f) => { setFile(f); setErrors((x) => ({ ...x, file: undefined })); }} error={errors.file} disabled={busy} />
            </Field>
            <p className="text-xs text-ink-500">La foto y la firma de entrega las registra el conductor desde la app.</p>
            <div className="flex justify-end"><Button type="submit" loading={busy}>{busy ? 'Subiendo documento…' : 'Subir documento'}</Button></div>
          </form>
        )}
      </CardBody>
    </Card>
  );
}
