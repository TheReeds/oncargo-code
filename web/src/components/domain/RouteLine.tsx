import { CITY_LABELS, formatKm } from '@/lib/format';

/** Lima ──── 1,010 km ────▶ Juliaca, con detalle opcional bajo cada ciudad. */
export function RouteLine({ origin, destination, km, originSub, destinationSub, size = 'md' }: { origin: string; destination: string; km?: number | null; originSub?: string; destinationSub?: string; size?: 'sm' | 'md' }) {
  const big = size === 'md';
  return (
    <div className="flex items-center gap-3">
      <div className="min-w-0">
        <div className={big ? 'font-semibold' : 'text-sm font-medium'}>{CITY_LABELS[origin] ?? origin}</div>
        {originSub && <div className="truncate text-xs text-ink-500">{originSub}</div>}
      </div>
      <div className="flex min-w-16 flex-1 items-center gap-1.5 text-ink-400" aria-hidden="true">
        <span className="size-2 rounded-full border-2 border-petrol-700" />
        <span className="h-px flex-1 bg-ink-400/60" />
        {km != null && <span className="font-mono text-[11px] text-ink-500">{formatKm(km)}</span>}
        <span className="h-px flex-1 bg-ink-400/60" />
        <span className="size-2 bg-petrol-700" />
      </div>
      <div className="min-w-0 text-right">
        <div className={big ? 'font-semibold' : 'text-sm font-medium'}>{CITY_LABELS[destination] ?? destination}</div>
        {destinationSub && <div className="truncate text-xs text-ink-500">{destinationSub}</div>}
      </div>
    </div>
  );
}
