import { Check, MapPin } from 'lucide-react';
import { cn } from '@/lib/utils';
import { CITY_LABELS, formatDateTime } from '@/lib/format';
import type { StopView } from '@/lib/api/server';

/** Paradas en horizontal (web). */
export function StopTimeline({ stops }: { stops: StopView[] }) {
  const sorted = [...stops].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0));
  if (!sorted.length) return <p className="text-sm text-ink-500">Este envío aún no tiene paradas registradas.</p>;
  return (
    <ol aria-label="Paradas del envío" className="flex flex-col gap-4 sm:flex-row sm:gap-0">
      {sorted.map((s, i) => {
        const done = s.status === 'COMPLETED';
        const here = s.status === 'ARRIVED';
        const time = s.completedAt ?? s.arrivedAt;
        return (
          <li key={s.id} className="relative flex flex-1 gap-3 sm:flex-col sm:gap-2">
            {i < sorted.length - 1 && <span aria-hidden="true" className={cn('absolute left-3 top-7 h-[calc(100%-0.5rem)] w-0.5 sm:left-7 sm:top-3 sm:h-0.5 sm:w-[calc(100%-1.75rem)]', done ? 'bg-emerald-500' : 'bg-steel-200')} />}
            <span className={cn('relative z-[1] grid size-6 shrink-0 place-items-center rounded-full border-2', done ? 'border-emerald-500 bg-emerald-500 text-white' : here ? 'border-petrol-700 bg-petrol-50 text-petrol-700 ring-4 ring-petrol-50' : 'border-ink-400 bg-white text-ink-400')}>
              {done ? <Check className="size-3.5" aria-hidden="true" /> : <MapPin className="size-3" aria-hidden="true" />}
            </span>
            <div className="min-w-0 pr-3 text-sm">
              <div className={cn('font-medium', !done && !here && 'text-ink-700')}>{s.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · {CITY_LABELS[s.city!] ?? s.city}</div>
              <div className="text-xs text-ink-500 sm:line-clamp-2" title={s.address ?? undefined}>{s.address}</div>
              <div className={cn('mt-0.5 text-xs', done ? 'text-emerald-700' : here ? 'text-petrol-700' : 'text-ink-500')}>
                {done ? 'Completada' : here ? 'El conductor llegó' : 'Pendiente'}{time ? ` · ${formatDateTime(time)}` : ''}
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
