import { ArrowRight } from 'lucide-react';
import { StatusPill } from '@/components/domain/StatusPill';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney } from '@/lib/format';
import type { CounterOffer } from '../server';

/** Una contra-oferta: monto previo → propuesto, estado, fecha y mensaje. `actions` = botones (transportista). */
export function CounterOfferCard({ offer, actions, className }: { offer: CounterOffer; actions?: React.ReactNode; className?: string }) {
  return (
    <div className={cn('rounded-input border px-3 py-2.5 text-sm', offer.status === 'PENDING' ? 'border-amber-500/40 bg-amber-50' : 'border-steel-200 bg-surface', className)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 font-mono tabular-nums">
          <span className="text-ink-500 line-through decoration-ink-400">{formatMoney(offer.previousAmount)}</span>
          <ArrowRight className="size-3.5 text-ink-500" aria-hidden="true" />
          <span className="sr-only">propuesta de</span>
          <span className="font-semibold text-emerald-700">{formatMoney(offer.amount)}</span>
        </div>
        {offer.status && <StatusPill status={offer.status} kind="counter" />}
      </div>
      <p className="mt-1 text-xs text-ink-500">{formatDateTime(offer.createdAt)}{offer.respondedAt && <> · respondida {formatDateTime(offer.respondedAt)}</>}</p>
      {offer.message && <p className="mt-1 text-ink-700">{offer.message}</p>}
      {actions && <div className="mt-2 flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}
