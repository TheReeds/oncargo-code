import { ShieldCheck } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { limaToday } from '@/lib/lima-date';
import { formatDate } from '@/lib/format';

/**
 * Sellos de una oferta que la PYME ve al comparar (roadmap 2.4/2.8): «Mejorada» (el transportista bajó el monto o cambió el
 * vehículo después de ofertar, `improvedCount > 0`) y «SOAT vigente» / «Revisión técnica vigente» cuando el documento sigue
 * vigente hoy (`soatValidUntil` / `inspectionValidUntil` ≥ hoy). La fecha va en el `title` (tooltip nativo) y en texto para
 * lectores de pantalla. Sirve en SSR y en islas; no renderiza nada si no hay sellos. No muestra montos.
 */
export function BidSeals({ improvedCount, soatValidUntil, inspectionValidUntil, today, className }: {
  improvedCount?: number | null; soatValidUntil?: string | null; inspectionValidUntil?: string | null;
  /** `YYYY-MM-DD`; por omisión hoy en Lima (pásalo en pruebas). */
  today?: string; className?: string;
}) {
  const now = today ?? limaToday();
  const seals = [
    soatValidUntil && soatValidUntil >= now ? { key: 'soat', label: 'SOAT vigente', until: soatValidUntil } : null,
    inspectionValidUntil && inspectionValidUntil >= now ? { key: 'insp', label: 'Revisión técnica vigente', until: inspectionValidUntil } : null,
  ].filter((s): s is { key: string; label: string; until: string } => !!s);
  const improved = (improvedCount ?? 0) > 0;
  if (!improved && seals.length === 0) return null;
  return (
    <ul className={className ?? 'flex flex-wrap items-center gap-1.5'} aria-label="Sellos de la oferta" data-testid="bid-seals">
      {improved && (
        <li><Badge tone="amber" title="El transportista mejoró esta oferta después de enviarla"><span className="sr-only">Oferta </span>Mejorada{(improvedCount ?? 0) > 1 ? ` · ${improvedCount} veces` : ''}</Badge></li>
      )}
      {seals.map((s) => (
        <li key={s.key}>
          <Badge tone="emerald" title={`Vigente hasta el ${formatDate(s.until)}`}>
            <ShieldCheck className="size-3" aria-hidden="true" />{s.label}<span className="sr-only"> hasta el {formatDate(s.until)}</span>
          </Badge>
        </li>
      ))}
    </ul>
  );
}
