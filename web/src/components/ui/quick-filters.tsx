import { cn } from '@/lib/utils';

export type QuickFilterOption = { value: string; label: string; count?: number };

/**
 * Filtros rápidos tipo "segmentos" sobre un listado (Todas · Publicadas · Adjudicadas…), con contador.
 * Son botones con `aria-pressed` (no tabs: no cambian de panel). Se desplazan horizontalmente en móvil.
 *  <QuickFilters label="Estado" value={status} onValueChange={setStatus} options={[{ value: '', label: 'Todas', count: 13 }, …]} />
 */
export function QuickFilters({ options, value, onValueChange, label, className }: { options: QuickFilterOption[]; value: string; onValueChange: (v: string) => void; label: string; className?: string }) {
  return (
    <div role="group" aria-label={label} className={cn('-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-0.5', className)}>
      {options.map((o) => {
        const on = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={on}
            onClick={() => onValueChange(o.value)}
            className={cn('inline-flex h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 text-[13px] font-medium transition-colors', on ? 'border-petrol-700 bg-petrol-700 text-on-solid' : 'border-steel-200 bg-surface text-ink-700 hover:border-ink-400 hover:bg-steel-100')}
          >
            {o.label}
            {o.count != null && <span className={cn('rounded-full px-1.5 font-mono text-[11px] tabular-nums', on ? 'bg-on-solid/20' : 'bg-steel-100 text-ink-500')}>{o.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
