import type { ComponentType, ReactNode } from 'react';
import { ArrowRight, TrendingDown, TrendingUp } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

const TONE = {
  default: { value: 'text-ink-900', icon: 'bg-steel-100 text-ink-700', bar: '' },
  petrol: { value: 'text-ink-900', icon: 'bg-petrol-50 text-petrol-700', bar: 'before:bg-petrol-700' },
  money: { value: 'text-emerald-700', icon: 'bg-emerald-50 text-emerald-700', bar: 'before:bg-emerald-500' },
  warn: { value: 'text-ink-900', icon: 'bg-amber-50 text-amber-700', bar: 'before:bg-amber-500' },
  alert: { value: 'text-red-700', icon: 'bg-red-50 text-red-700', bar: 'before:bg-red-600' },
} as const;
export type KpiTone = keyof typeof TONE;

/**
 * Indicador clave: etiqueta + cifra grande + contexto. Máx. 4 por fila (usa `KpiGrid`).
 *  - `tone`: `money` (dinero/oportunidad, emerald), `warn` (por cerrar/pendiente), `alert` (incidencia), `petrol`. Pinta ícono y filete lateral.
 *  - `delta`: variación respecto de un periodo (`{ value: '+12%', direction: 'up'|'down'|'flat', good?: boolean }`); `good` decide el color.
 *  - `unit`: sufijo pequeño junto a la cifra (`t`, `%`). `loading`: esqueleto.
 *  - `href`: toda la tarjeta es un enlace con flecha de "ver".
 * Retrocompatible: `{label, value, context, icon, href}` sigue igual.
 */
export function KpiTile({ label, value, context, icon: Icon, href, tone = 'default', unit, delta, loading, className }: {
  label: string; value: ReactNode; context?: ReactNode; icon?: ComponentType<{ size?: number; className?: string }>; href?: string;
  tone?: KpiTone; unit?: string; delta?: { value: string; direction?: 'up' | 'down' | 'flat'; good?: boolean }; loading?: boolean; className?: string;
}) {
  const t = TONE[tone];
  const long = typeof value === 'string' && value.length > 8; // cifras largas (S/ 4,800.00) bajan de tamaño para no desbordar en 2 columnas
  const body = (
    <Card className={cn('group relative h-full overflow-hidden p-4 transition-colors before:absolute before:inset-y-0 before:left-0 before:w-0.5', t.bar, href && 'hover:border-petrol-700/40 hover:bg-steel-100/40', className)}>
      <div className="flex items-start justify-between gap-2">
        <span className="text-[13px] font-medium text-ink-500">{label}</span>
        {Icon && <span className={cn('grid size-7 shrink-0 place-items-center rounded-input', t.icon)}><Icon size={15} /></span>}
      </div>
      {loading ? <Skeleton className="mt-3 h-8 w-20" /> : (
        <div className="mt-1.5 flex items-baseline gap-1.5">
          <span className={cn('font-mono font-semibold leading-none tabular-nums', long ? 'text-xl sm:text-2xl' : 'text-[28px] sm:text-3xl', t.value)}>{value}</span>
          {unit && <span className="font-mono text-sm text-ink-500">{unit}</span>}
          {delta && (
            <span className={cn('ml-auto inline-flex items-center gap-0.5 text-xs font-medium tabular-nums', delta.good === false ? 'text-red-700' : delta.good ? 'text-emerald-700' : 'text-ink-500')}>
              {delta.direction === 'up' ? <TrendingUp size={13} aria-hidden="true" /> : delta.direction === 'down' ? <TrendingDown size={13} aria-hidden="true" /> : null}{delta.value}
            </span>
          )}
        </div>
      )}
      {(context || href) && (
        <div className="mt-2 flex items-center justify-between gap-2 text-xs text-ink-500">
          <span className="line-clamp-2 min-w-0">{loading ? <Skeleton className="h-3 w-28" /> : context}</span>
          {href && <ArrowRight size={13} className="shrink-0 -translate-x-1 opacity-0 transition group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:opacity-100" aria-hidden="true" />}
        </div>
      )}
    </Card>
  );
  return href ? <a href={href} className="block h-full rounded-card" aria-label={undefined}>{body}</a> : body;
}

/**
 * Rejilla de KPIs: 2 columnas en móvil, 4 desde `lg` (`cols={3}` para tres). Úsala siempre con un número PAR por fila
 * completa: 4 u 8 tiles con `cols` 4; 3 o 6 con `cols={3}`. Nunca 4 + 3 en dos filas de anchos distintos: si tienes 7, suma o
 * quita uno. En móvil, un último tile impar ocupa todo el ancho (no queda un hueco).
 */
export function KpiGrid({ children, cols = 4, className }: { children: ReactNode; cols?: 2 | 3 | 4; className?: string }) {
  return <div className={cn('grid grid-cols-2 gap-3 max-lg:[&>:last-child:nth-child(odd)]:col-span-2 sm:gap-4', cols === 4 && 'lg:grid-cols-4', cols === 3 && 'lg:grid-cols-3', className)}>{children}</div>;
}
