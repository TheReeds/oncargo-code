import { useEffect } from 'react';
import { ChevronLeft, ChevronRight, Download } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { DOCUMENT_TYPE_LABELS, formatDateTime, formatFileSize } from '@/lib/format';
import type { DocumentView } from '../server';
import { docUrl } from './documentUtils';

/** Vista grande de imágenes con navegación (botones y flechas del teclado). */
export function DocumentViewer({ images, index, onIndexChange, onClose }: { images: DocumentView[]; index: number | null; onIndexChange: (i: number) => void; onClose: () => void }) {
  const open = index != null && !!images[index];
  const doc = open ? images[index!] : null;

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' && index! < images.length - 1) onIndexChange(index! + 1);
      if (e.key === 'ArrowLeft' && index! > 0) onIndexChange(index! - 1);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, index, images.length, onIndexChange]);

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={doc?.fileName ?? 'Documento'}
      description={doc ? `${DOCUMENT_TYPE_LABELS[doc.type ?? ''] ?? 'Documento'} · ${formatFileSize(doc.sizeBytes)} · ${formatDateTime(doc.createdAt)}` : undefined}
      className="max-w-3xl"
      footer={doc && (
        <div className="flex w-full items-center justify-between gap-2">
          <div className="flex items-center gap-1">
            <Button variant="outline" size="sm" disabled={index! <= 0} onClick={() => onIndexChange(index! - 1)}><ChevronLeft aria-hidden="true" />Anterior</Button>
            <span className="px-2 font-mono text-xs tabular-nums text-ink-500" aria-live="polite">{index! + 1} de {images.length}</span>
            <Button variant="outline" size="sm" disabled={index! >= images.length - 1} onClick={() => onIndexChange(index! + 1)}>Siguiente<ChevronRight aria-hidden="true" /></Button>
          </div>
          <Button asChild size="sm"><a href={docUrl(doc, true)}><Download aria-hidden="true" />Descargar</a></Button>
        </div>
      )}
    >
      {doc && (
        <div className="flex max-h-[65vh] items-center justify-center overflow-auto rounded-input bg-steel-100 p-2">
          <img src={docUrl(doc)} alt={`${DOCUMENT_TYPE_LABELS[doc.type ?? ''] ?? 'Documento'}: ${doc.fileName}`} className="max-h-[60vh] min-h-48 min-w-48 max-w-full rounded object-contain" />
        </div>
      )}
    </Dialog>
  );
}
