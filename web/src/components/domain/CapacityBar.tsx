import { cn } from '@/lib/utils';
import { formatNumber } from '@/lib/format';

/**
 * Ocupación de un vehículo o viaje: barra + "usado / total". `used` y `total` en kg.
 * Verde cuando hay holgura, ámbar desde 90 %. Accesible como `meter` con nombre.
 */
export function CapacityBar({ used, total, label = 'Capacidad ocupada', className, unit = 'kg' }: { used: number; total: number; label?: string; className?: string; unit?: string }) {
  const pct = total > 0 ? Math.min(100, Math.max(0, (used / total) * 100)) : 0;
  return (
    <div className={cn('min-w-0', className)}>
      <div role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={total} aria-valuenow={used} aria-valuetext={`${formatNumber(used)} de ${formatNumber(total)} ${unit}`} className="h-2 overflow-hidden rounded-full bg-steel-200">
        <div className={cn('h-full rounded-full', pct >= 90 ? 'bg-amber-500' : 'bg-emerald-500')} style={{ width: `${pct}%` }} />
      </div>
      <div className="mt-1 font-mono text-xs tabular-nums text-ink-500">{formatNumber(used)} / {formatNumber(total)} {unit}</div>
    </div>
  );
}
