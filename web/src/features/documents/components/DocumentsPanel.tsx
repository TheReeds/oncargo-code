import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Camera, Download, Eye, FileSignature, FileText, FolderOpen, ReceiptText, ClipboardList, File as FileIcon } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/domain/EmptyState';
import { DOCUMENT_TYPE_LABELS, formatDateTime, formatFileSize } from '@/lib/format';
import type { DocumentView } from '../server';
import { DocumentUpload } from './DocumentUpload';
import { DocumentViewer } from './DocumentViewer';
import { docUrl, isEvidence, isImage } from './documentUtils';

type Props = { shipmentId: string; perspective: 'PYME' | 'CARRIER' | 'OPERATOR'; status: string; hasActiveIncident?: boolean; /** Sin subida (detalle de incidencia, operador). */ readOnly?: boolean; title?: string };
const TYPE_ICON: Record<string, typeof FileText> = { DELIVERY_PHOTO: Camera, SIGNATURE: FileSignature, REMISSION_GUIDE: ClipboardList, SERVICE_ORDER: FileText, RECEIPT: ReceiptText, OTHER: FileIcon };
const ORDER = ['DELIVERY_PHOTO', 'SIGNATURE', 'REMISSION_GUIDE', 'SERVICE_ORDER', 'RECEIPT', 'OTHER'];

function Thumb({ doc, onOpen, className = 'size-14' }: { doc: DocumentView; onOpen: () => void; className?: string }) {
  const Icon = TYPE_ICON[doc.type ?? 'OTHER'] ?? FileIcon;
  if (isImage(doc))
    return (
      <button type="button" onClick={onOpen} className={`${className} shrink-0 overflow-hidden rounded-input border border-steel-200 bg-steel-100`}>
        <img src={docUrl(doc)} alt="" loading="lazy" className="size-full object-cover" />
        <span className="sr-only">Ver {doc.fileName}</span>
      </button>
    );
  return <span aria-hidden="true" className={`${className} flex shrink-0 items-center justify-center rounded-input bg-petrol-50 text-petrol-700`}><Icon className="size-6" /></span>;
}

export default function DocumentsPanel({ shipmentId, perspective, status, readOnly: ro, title = 'Documentos del envío' }: Props) {
  const canOperate = useCan('OPERATE');
  const [docs, setDocs] = useState<DocumentView[] | null>(null);
  const [orgId, setOrgId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState('ALL');
  const [viewing, setViewing] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError(null);
    const res = await actions.documents.list({ shipmentId });
    if (res.error) setError(res.error.message);
    else { setDocs(res.data.documents); setOrgId(res.data.orgId ?? ''); }
  }, [shipmentId]);
  useEffect(() => { setDocs(null); setFilter('ALL'); load(); }, [load]);

  const counts = useMemo(() => {
    const c: Record<string, number> = {};
    (docs ?? []).forEach((d) => { c[d.type ?? 'OTHER'] = (c[d.type ?? 'OTHER'] ?? 0) + 1; });
    return c;
  }, [docs]);
  const visible = useMemo(() => (docs ?? []).filter((d) => filter === 'ALL' || d.type === filter), [docs, filter]);
  const images = useMemo(() => visible.filter(isImage), [visible]);
  const evidence = useMemo(() => (docs ?? []).filter(isEvidence), [docs]);
  const evidenceImages = useMemo(() => evidence.filter(isImage), [evidence]);
  const [viewerList, setViewerList] = useState<DocumentView[]>([]);
  const open = (list: DocumentView[], d: DocumentView) => { setViewerList(list); setViewing(list.findIndex((x) => x.id === d.id)); };

  if (error) return <Alert tone="error" title="No pudimos cargar los documentos" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{error}</Alert>;
  if (!docs) return <div className="space-y-3" aria-busy="true" aria-label="Cargando documentos"><Skeleton className="h-28 w-full" /><Skeleton className="h-20 w-full" /></div>;

  const hasPhoto = docs.some((d) => d.type === 'DELIVERY_PHOTO');
  const readOnly = ro || perspective === 'OPERATOR' || !canOperate;
  const who = (d: DocumentView) => (perspective === 'OPERATOR' ? null : d.uploadedByOrgId === orgId ? 'Tú' : perspective === 'PYME' ? 'El transportista' : 'La PYME');

  return (
    <div className="space-y-4">
      {/* Evidencia de entrega (RF37) */}
      {evidence.length > 0 ? (
        <section aria-labelledby={`ev-${shipmentId}`} className="rounded-card border border-emerald-500/40 bg-emerald-50/50 p-4">
          <h3 id={`ev-${shipmentId}`} className="flex items-center gap-2 text-[15px] font-semibold text-ink-900"><Camera className="size-4 text-emerald-700" aria-hidden="true" />Evidencia de entrega</h3>
          <p className="mt-0.5 text-xs text-ink-500">Registrada por el conductor al entregar la carga.</p>
          <ul className="mt-3 flex flex-wrap gap-3">
            {evidence.map((d) => (
              <li key={d.id} className="w-36">
                <Thumb doc={d} className="h-28 w-36" onOpen={() => open(evidenceImages, d)} />
                <p className="mt-1 text-xs font-medium text-ink-900">{DOCUMENT_TYPE_LABELS[d.type ?? '']}</p>
                <p className="text-xs text-ink-500">{formatDateTime(d.createdAt)}</p>
              </li>
            ))}
          </ul>
        </section>
      ) : (
        (status === 'IN_TRANSIT' || status === 'DELIVERED') && !hasPhoto && (
          <Alert tone="warning" title="Aún no hay foto de entrega">
            {status === 'DELIVERED' ? 'Este envío figura como entregado, pero no tiene foto de entrega. Sin ella no se pueden calificar los servicios.' : 'El conductor debe registrar la foto de entrega antes de completar el envío. Aparecerá aquí apenas la suba.'}
          </Alert>
        )
      )}

      <Card>
        <CardHeader>
          <div><CardTitle>{title}</CardTitle><CardDescription>Guías de remisión, órdenes de servicio y comprobantes.</CardDescription></div>
          <Badge tone="neutral"><span className="font-mono tabular-nums">{docs.length}</span> {docs.length === 1 ? 'archivo' : 'archivos'}</Badge>
        </CardHeader>
        {docs.length === 0 ? (
          <EmptyState icon={FolderOpen} title="Aún no hay documentos" description={readOnly ? 'Las partes todavía no subieron guías, órdenes ni comprobantes de este envío.' : 'Sube la guía de remisión, la orden de servicio o un comprobante para que ambas partes los tengan a mano.'} />
        ) : (
          <>
            <div className="border-b border-steel-200 px-5 py-3">
              <QuickFilters label="Tipo de documento" value={filter} onValueChange={setFilter} options={[{ value: 'ALL', label: 'Todos', count: docs.length }, ...ORDER.filter((t) => counts[t]).map((t) => ({ value: t, label: DOCUMENT_TYPE_LABELS[t], count: counts[t] }))]} />
            </div>
            <ul className="divide-y divide-steel-200">
              {visible.map((d) => {
                const img = isImage(d);
                return (
                  <li key={d.id} className="flex flex-wrap items-center gap-3 px-5 py-3" data-testid="document-row">
                    <Thumb doc={d} onOpen={() => open(images, d)} />
                    <div className="min-w-40 flex-1">
                      <p className="truncate text-sm font-medium text-ink-900">{d.fileName}</p>
                      <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
                        <Badge tone={isEvidence(d) ? 'emerald' : 'petrol'}>{DOCUMENT_TYPE_LABELS[d.type ?? ''] ?? 'Documento'}</Badge>
                        <span className="font-mono tabular-nums">{formatFileSize(d.sizeBytes)}</span>
                        {who(d) && <span>· {who(d)}</span>}
                        <span>· {formatDateTime(d.createdAt)}</span>
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      {img ? (
                        <Button variant="outline" size="sm" onClick={() => open(images, d)}><Eye aria-hidden="true" />Ver<span className="sr-only"> {d.fileName}</span></Button>
                      ) : (
                        <Button asChild variant="outline" size="sm"><a href={docUrl(d)} target="_blank" rel="noopener"><Eye aria-hidden="true" />Ver<span className="sr-only"> {d.fileName} en una pestaña nueva</span></a></Button>
                      )}
                      <Button asChild variant="ghost" size="sm"><a href={docUrl(d, true)}><Download aria-hidden="true" />Descargar<span className="sr-only"> {d.fileName}</span></a></Button>
                    </div>
                  </li>
                );
              })}
            </ul>
            {visible.length === 0 && <p className="px-5 py-6 text-center text-sm text-ink-500">No hay documentos de este tipo.</p>}
          </>
        )}
      </Card>

      {!canOperate && perspective !== 'OPERATOR' && <ReadOnlyNotice>No puedes subir documentos.</ReadOnlyNotice>}
      {!readOnly && <DocumentUpload shipmentId={shipmentId} cancelled={status === 'CANCELLED'} onUploaded={load} />}
      <DocumentViewer images={viewerList} index={viewing} onIndexChange={setViewing} onClose={() => setViewing(null)} />
    </div>
  );
}
