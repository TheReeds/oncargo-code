import * as React from 'react';
import { FileText, ImageIcon, UploadCloud, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { formatFileSize } from '@/lib/format';
import { Button } from './button';

const MB = 1024 * 1024;

/** Valida un archivo contra tipos y tamaño; devuelve el mensaje en español o `null` si es válido. */
export function validateFile(file: File, accept: string[], maxMB: number): string | null {
  const ext = '.' + (file.name.split('.').pop() ?? '').toLowerCase();
  if (!accept.includes(ext)) return `«${file.name}» no es un formato permitido. Sube un archivo ${accept.map((a) => a.slice(1).toUpperCase()).join(', ').replace(/, ([^,]*)$/, ' o $1')}.`;
  if (file.size > maxMB * MB) return `«${file.name}» pesa ${formatFileSize(file.size)} y supera el límite de ${maxMB} MB. Elige un archivo más liviano.`;
  if (file.size === 0) return `«${file.name}» está vacío. Elige otro archivo.`;
  return null;
}

interface Props {
  /** Archivo elegido (controlado). */
  file: File | null;
  onFileChange: (file: File | null) => void;
  /** Extensiones aceptadas. */
  accept?: string[];
  maxSizeMB?: number;
  /** Error externo (p. ej. de la API); se muestra bajo la zona. */
  error?: string | null;
  disabled?: boolean;
  id?: string;
  className?: string;
}

/**
 * Zona de carga de un archivo: arrastrar y soltar o elegir con botón (accesible por teclado).
 * Valida tipo y tamaño en el cliente; el archivo inválido no se acepta y se explica el motivo.
 */
export function FileDropzone({ file, onFileChange, accept = ['.jpg', '.jpeg', '.png', '.pdf'], maxSizeMB = 10, error, disabled, id, className }: Props) {
  const input = React.useRef<HTMLInputElement>(null);
  const [over, setOver] = React.useState(false);
  const [localError, setLocalError] = React.useState<string | null>(null);
  const errId = React.useId();
  const hintId = React.useId();
  const shown = localError ?? error ?? null;

  function take(files: FileList | null) {
    const f = files?.[0];
    if (!f) return;
    const msg = validateFile(f, accept, maxSizeMB);
    setLocalError(msg);
    onFileChange(msg ? null : f);
    if (input.current) input.current.value = '';
  }
  function clear() {
    setLocalError(null);
    onFileChange(null);
    input.current?.focus();
  }

  const formats = accept.map((a) => a.slice(1).toUpperCase()).filter((a) => a !== 'JPEG').join(', ');
  const isImage = file && /\.(jpe?g|png)$/i.test(file.name);

  return (
    <div className={className}>
      <input ref={input} type="file" hidden accept={accept.join(',')} onChange={(e) => take(e.target.files)} disabled={disabled} tabIndex={-1} />
      {file ? (
        <div className="flex items-center gap-3 rounded-input border border-steel-200 bg-surface px-3 py-2.5">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-input bg-petrol-50 text-petrol-700" aria-hidden="true">{isImage ? <ImageIcon className="size-4" /> : <FileText className="size-4" />}</span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-ink-900">{file.name}</p>
            <p className="font-mono text-xs tabular-nums text-ink-500">{formatFileSize(file.size)}</p>
          </div>
          <Button type="button" variant="ghost" size="sm" onClick={clear} disabled={disabled}><X aria-hidden="true" />Quitar<span className="sr-only"> archivo {file.name}</span></Button>
        </div>
      ) : (
        <button
          type="button"
          id={id}
          disabled={disabled}
          aria-describedby={`${hintId}${shown ? ' ' + errId : ''}`}
          aria-invalid={shown ? true : undefined}
          onClick={() => input.current?.click()}
          onDragOver={(e) => { e.preventDefault(); if (!disabled) setOver(true); }}
          onDragLeave={() => setOver(false)}
          onDrop={(e) => { e.preventDefault(); setOver(false); if (!disabled) take(e.dataTransfer.files); }}
          className={cn(
            'flex w-full flex-col items-center gap-1.5 rounded-input border-2 border-dashed px-4 py-6 text-center transition-colors disabled:cursor-not-allowed disabled:opacity-50',
            over ? 'border-petrol-700 bg-petrol-50' : 'border-ink-400/60 bg-surface hover:border-petrol-700 hover:bg-petrol-50/50',
            shown && 'border-red-600',
          )}
        >
          <UploadCloud className="size-6 text-petrol-700" aria-hidden="true" />
          <span className="text-sm font-medium text-ink-900">Arrastra el archivo aquí o <span className="text-petrol-700 underline underline-offset-2">elígelo desde tu equipo</span></span>
          <span id={hintId} className="text-xs text-ink-500">{formats} · máximo {maxSizeMB} MB</span>
        </button>
      )}
      {shown && <p id={errId} role="alert" className="mt-1.5 text-xs text-red-600">{shown}</p>}
    </div>
  );
}
