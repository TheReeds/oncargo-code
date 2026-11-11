import { StarRating } from '@/components/ui/star-rating';
import { Avatar } from '@/components/ui/avatar';
import { formatDateTime } from '@/lib/format';
import type { ReviewView } from '../server';

/** Nombre de la dimensión "carga" según quién califica: la PYME evalúa el estado; el transportista, la exactitud de lo declarado. */
export const cargoLabel = (direction?: string) => (direction === 'CARRIER_TO_PYME' ? 'Exactitud de la carga declarada' : 'Estado de la carga');
export const DIRECTION_LABELS: Record<string, string> = { PYME_TO_CARRIER: 'PYME a transportista', CARRIER_TO_PYME: 'Transportista a PYME' };

/** Una calificación recibida o emitida: autor, promedio, 3 dimensiones y comentario. */
export function ReviewCard({ review, showReviewer = true }: { review: ReviewView; showReviewer?: boolean }) {
  const dims: [string, number | undefined][] = [['Puntualidad', review.punctuality], [cargoLabel(review.direction), review.cargoCondition], ['Comunicación', review.communication]];
  return (
    <li className="rounded-card border border-steel-200 bg-surface p-4" data-testid="review-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <Avatar name={review.reviewerName ?? '?'} />
          <div className="min-w-0">
            {showReviewer && (
              <p className="truncate text-sm font-semibold text-ink-900">
                {review.reviewerOrgId ? <a href={`/organizaciones/${review.reviewerOrgId}`} className="rounded hover:text-petrol-700 hover:underline">{review.reviewerName}</a> : review.reviewerName}
              </p>
            )}
            <p className="text-xs text-ink-500">{DIRECTION_LABELS[review.direction ?? ''] ?? ''} · {formatDateTime(review.createdAt)}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <StarRating readOnly value={review.rating} size="sm" label="Calificación general" />
          <span className="font-mono text-sm font-semibold tabular-nums text-ink-900">{review.rating?.toFixed(1)}</span>
        </div>
      </div>
      <ul className="mt-3 grid gap-x-6 gap-y-1 sm:grid-cols-3">
        {dims.map(([label, v]) => (
          <li key={label} className="flex items-center justify-between gap-2 text-xs text-ink-700 sm:flex-col sm:items-start sm:gap-0.5">
            <span>{label}</span>
            <StarRating readOnly value={v} size="sm" label={label} />
          </li>
        ))}
      </ul>
      {review.comment && <p className="mt-3 border-l-2 border-steel-200 pl-3 text-sm text-ink-700">{review.comment}</p>}
    </li>
  );
}
