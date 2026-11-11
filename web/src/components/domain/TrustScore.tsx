import { ShieldCheck } from 'lucide-react';
import { Tooltip } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

/** Banda del Trust Score (0–100; 75 = sin calificaciones). */
export function trustTone(score: number): 'emerald' | 'petrol' | 'amber' | 'red' {
  if (score >= 85) return 'emerald';
  if (score >= 70) return 'petrol';
  if (score >= 50) return 'amber';
  return 'red';
}
const TONE: Record<string, string> = { emerald: 'bg-emerald-50 text-emerald-700', petrol: 'bg-petrol-50 text-petrol-700', amber: 'bg-amber-50 text-amber-700', red: 'bg-red-50 text-red-600' };
const BAR: Record<string, string> = { emerald: 'bg-emerald-500', petrol: 'bg-petrol-700', amber: 'bg-amber-500', red: 'bg-red-600' };
export const TRUST_HELP = 'Trust Score: puntaje de confianza de 0 a 100 según las calificaciones de puntualidad, estado de la carga y comunicación. 75 = aún sin calificaciones.';

interface Props {
  score: number | null | undefined;
  /** `chip` (listas densas) · `meter` (barra + número, paneles de detalle). */
  variant?: 'chip' | 'meter';
  /** Cantidad de calificaciones, se muestra junto al puntaje. */
  reviews?: number | null;
  className?: string;
}

export function TrustScore({ score, variant = 'chip', reviews, className }: Props) {
  if (score == null) return null;
  const tone = trustTone(score);
  const text = score.toFixed(1);
  if (variant === 'meter')
    return (
      <div className={cn('min-w-32', className)}>
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-xs font-medium text-ink-500">Trust Score</span>
          <span className="font-mono text-sm font-semibold tabular-nums text-ink-900">{text}<span className="text-ink-500">/100</span></span>
        </div>
        <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-steel-200" role="meter" aria-label="Trust Score" aria-valuemin={0} aria-valuemax={100} aria-valuenow={score}>
          <div className={cn('h-full rounded-full', BAR[tone])} style={{ width: `${Math.max(2, Math.min(100, score))}%` }} />
        </div>
        {reviews != null && <p className="mt-1 text-xs text-ink-500">{reviews === 0 ? 'Sin calificaciones aún' : `${reviews} ${reviews === 1 ? 'calificación' : 'calificaciones'}`}</p>}
      </div>
    );
  return (
    <Tooltip content={TRUST_HELP}>
      <span tabIndex={0} className={cn('inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium', TONE[tone], className)}>
        <ShieldCheck className="size-3" aria-hidden="true" />
        <span className="sr-only">Trust Score </span>
        <span className="font-mono tabular-nums">{text}</span>
      </span>
    </Tooltip>
  );
}
