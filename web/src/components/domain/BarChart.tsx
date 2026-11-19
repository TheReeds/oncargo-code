import { cn } from '@/lib/utils';

export type BarDatum = { key: string; label: string; value: number; shortLabel?: string };

/** Escala «bonita»: tope redondo (1, 2, 2.5, 5 × 10ⁿ) y `steps` intervalos iguales, para que el eje no sea arbitrario con 1 o 2 datos. */
export function niceScale(max: number, steps = 4): { top: number; ticks: number[] } {
  const raw = Math.max(max, 1) / steps;
  const pow = 10 ** Math.floor(Math.log10(raw));
  const unit = [1, 2, 2.5, 5, 10].find((m) => m * pow >= raw)! * pow;
  const top = unit * steps;
  return { top, ticks: Array.from({ length: steps + 1 }, (_, i) => unit * i) };
}

/** `9,309` → `9,309` · `12,350` → `12.4 mil` · `1,250,000` → `1.3 M` (corto, para el eje y la etiqueta sobre la barra). */
export function compactNumber(n: number): string {
  const a = Math.abs(n);
  if (a >= 1_000_000) return `${+(n / 1_000_000).toFixed(1)} M`;
  if (a >= 10_000) return `${+(n / 1000).toFixed(a >= 100_000 ? 0 : 1)} mil`;
  return Math.round(n).toLocaleString('es-PE');
}

/**
 * Gráfico de columnas de una sola serie (un valor por categoría; p. ej. un valor por mes), pensado para verse bien con 1 a 14 datos.
 *  - Eje y con escala redonda (`niceScale`) y líneas guía finas; columnas de ancho máximo 28 px, esquina superior redondeada (4 px) y
 *    base recta sobre la línea cero, una sola tinta (`petrol`). Con ≤ 8 columnas, etiqueta corta sobre cada una; siempre tooltip al pasar el cursor.
 *  - Pasa TODAS las categorías del rango (los meses sin datos con `value: 0`) para que el eje no se invente: una categoría vacía
 *    queda como hueco con su etiqueta.
 *  - Accesibilidad: el gráfico es una imagen con resumen en `aria-label`; el tooltip no es la única vía a los datos, así que **acompáñalo
 *    siempre de la tabla con los mismos números** (ver `ReportView`). Solo tokens: funciona igual en modo oscuro.
 *  - `formatValue`: valor completo (tooltip, resumen). `formatShort`: eje y etiqueta sobre la barra.
 */
export function BarChart({ data, label, formatValue, formatShort = compactNumber, height = 220, className, testId }: {
  data: BarDatum[]; label: string; formatValue: (n: number) => string; formatShort?: (n: number) => string; height?: number; className?: string; testId?: string;
}) {
  const { top, ticks } = niceScale(Math.max(0, ...data.map((d) => d.value)));
  const direct = data.length <= 8;
  const summary = `${label}: ${data.map((d) => `${d.label} ${formatValue(d.value)}`).join(', ')}`;
  return (
    <figure className={cn('m-0', className)} data-testid={testId}>
      <div role="img" aria-label={summary} className="flex gap-2">
        <div className="relative w-12 shrink-0 sm:w-14" style={{ height }} aria-hidden="true">
          {ticks.map((t) => (
            <span key={t} className="absolute right-0 -translate-y-1/2 font-mono text-[11px] tabular-nums leading-none text-ink-500" style={{ bottom: `${(t / top) * 100}%` }}>{t === 0 ? '0' : formatShort(t)}</span>
          ))}
        </div>
        <div className="min-w-0 flex-1" aria-hidden="true">
          <div className="relative mt-6" style={{ height }}>
            {ticks.map((t) => <div key={t} className={cn('absolute inset-x-0 border-t', t === 0 ? 'border-ink-400/60' : 'border-steel-200')} style={{ bottom: `${(t / top) * 100}%` }} />)}
            <div className="absolute inset-0 flex">
              {data.map((d) => {
                const pct = d.value > 0 ? Math.max(1.5, (d.value / top) * 100) : 0;
                return (
                  <div key={d.key} className="group relative flex min-w-0 flex-1 items-end justify-center rounded-t transition-colors hover:bg-steel-100/70 active:bg-steel-100/70" data-testid="bar-slot">
                    {d.value > 0 && <div className="w-full max-w-7 rounded-t-[4px] bg-petrol-700 transition-opacity group-hover:opacity-85" style={{ height: `${pct}%` }} />}
                    {direct && d.value > 0 && <span className="pointer-events-none absolute left-1/2 -translate-x-1/2 whitespace-nowrap font-mono text-[11px] font-medium tabular-nums leading-none text-ink-700" style={{ bottom: `calc(${pct}% + 6px)` }}>{formatShort(d.value)}</span>}
                    <span className="pointer-events-none absolute left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded-input bg-chrome px-2.5 py-1.5 text-xs text-white shadow-lg group-hover:block group-active:block" style={{ bottom: `calc(${pct}% + ${direct && d.value > 0 ? 26 : 8}px)` }} role="presentation">
                      <span className="block font-mono text-[13px] font-semibold tabular-nums">{formatValue(d.value)}</span>
                      <span className="block text-white/75">{d.label}</span>
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="flex pt-2">
            {data.map((d) => <span key={d.key} className="min-w-0 flex-1 truncate text-center text-xs text-ink-500">{d.shortLabel ? <><span className={data.length > 6 ? undefined : 'sm:hidden'}>{d.shortLabel}</span>{data.length <= 6 && <span className="hidden sm:inline">{d.label}</span>}</> : d.label}</span>)}
          </div>
        </div>
      </div>
      <figcaption className="sr-only">{label}</figcaption>
    </figure>
  );
}
