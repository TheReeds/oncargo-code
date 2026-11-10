import * as React from 'react';
import { Check, MapPin } from 'lucide-react';
import { cn } from '@/lib/utils';

export type TimelineStatus = 'done' | 'current' | 'pending';
export type TimelineItem = { id: string; title: string; detail?: string | null; meta?: string | null; status: TimelineStatus; icon?: React.ReactNode };

/**
 * Línea de tiempo / stepper de pasos (paradas de un envío, hitos). `orientation="horizontal"` (default) apila en móvil;
 * `"vertical"` siempre en columna (paneles estrechos, conductor).
 */
export function Timeline({ items, orientation = 'horizontal', label, className }: { items: TimelineItem[]; orientation?: 'horizontal' | 'vertical'; label: string; className?: string }) {
  const h = orientation === 'horizontal';
  return (
    <ol aria-label={label} className={cn('flex flex-col gap-4', h && 'sm:flex-row sm:gap-0', className)}>
      {items.map((s, i) => {
        const done = s.status === 'done';
        const here = s.status === 'current';
        return (
          <li key={s.id} className={cn('relative flex flex-1 gap-3', h && 'sm:flex-col sm:gap-2')} aria-current={here ? 'step' : undefined}>
            {i < items.length - 1 && <span aria-hidden="true" className={cn('absolute left-3 top-7 h-[calc(100%-0.5rem)] w-0.5', h && 'sm:left-7 sm:top-3 sm:h-0.5 sm:w-[calc(100%-1.75rem)]', done ? 'bg-emerald-500' : 'bg-steel-200')} />}
            <span className={cn('relative z-[1] grid size-6 shrink-0 place-items-center rounded-full border-2', done ? 'border-emerald-500 bg-emerald-500 text-white' : here ? 'border-petrol-700 bg-petrol-50 text-petrol-700 ring-4 ring-petrol-50' : 'border-ink-400 bg-white text-ink-500')}>
              {done ? <Check className="size-3.5" aria-hidden="true" /> : (s.icon ?? <MapPin className="size-3" aria-hidden="true" />)}
            </span>
            <div className="min-w-0 pr-3 text-sm">
              <div className={cn('font-medium', !done && !here && 'text-ink-700')}>{s.title}</div>
              {s.detail && <div className={cn('text-xs text-ink-500', h && 'sm:line-clamp-2')} title={s.detail}>{s.detail}</div>}
              {s.meta && <div className={cn('mt-0.5 text-xs', done ? 'text-emerald-700' : here ? 'text-petrol-700' : 'text-ink-500')}>{s.meta}</div>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
