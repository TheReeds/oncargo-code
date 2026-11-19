import { formatMoney } from '@/lib/format';
import { monthLabel } from '../labels';

export type BarDatum = { key: string; value: number };

/** `12,350` → `12.4 mil`: etiqueta corta sobre la barra (el valor exacto está en la tabla y en el tooltip). */
const short = (n: number) => (n >= 10_000 ? `${(n / 1000).toFixed(n >= 100_000 ? 0 : 1)} mil` : Math.round(n).toLocaleString('es-PE'));

/**
 * Gráfico de barras de una sola serie (un valor por mes). Marcas finas con el extremo redondeado (4 px) sobre la línea base,
 * una sola tinta (petrol), etiqueta directa solo con ≤ 8 meses, tooltip al pasar el cursor. Es decorativo para lectores de
 * pantalla: la tabla «Por mes» de al lado trae los mismos datos.
 */
export default function MonthlyBars({ data, label }: { data: BarDatum[]; label: string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const direct = data.length <= 8;
  return (
    <figure className="m-0" data-testid="monthly-bars">
      <div role="img" aria-label={`${label}: ${data.map((d) => `${monthLabel(d.key)} ${formatMoney(d.value)}`).join(', ')}`} className="flex h-48 items-end gap-3 border-b border-steel-200 px-2 pt-6">
        {data.map((d) => {
          const pct = Math.max(2, (d.value / max) * 100);
          return (
            <div key={d.key} className="group relative flex h-full min-w-0 flex-1 flex-col items-center justify-end">
              {direct && <span className="mb-1 font-mono text-[11px] tabular-nums text-ink-500">{short(d.value)}</span>}
              <div className="w-full max-w-10 rounded-t bg-petrol-700 transition-opacity group-hover:opacity-80" style={{ height: `${pct}%` }} />
              <span className="pointer-events-none absolute -top-1 left-1/2 z-10 hidden -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-input bg-chrome px-2 py-1 font-mono text-xs text-white shadow group-hover:block">
                {monthLabel(d.key)} · {formatMoney(d.value)}
              </span>
            </div>
          );
        })}
      </div>
      <div className="flex gap-3 px-2 pt-1.5" aria-hidden="true">
        {data.map((d) => <span key={d.key} className="min-w-0 flex-1 truncate text-center text-xs text-ink-500">{monthLabel(d.key)}</span>)}
      </div>
      <figcaption className="sr-only">{label}</figcaption>
    </figure>
  );
}
