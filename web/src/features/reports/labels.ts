import { CITY_LABELS, formatMoney, formatNumber } from '@/lib/format';

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const MONTHS_LONG = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

/** `2026-11` → `nov 2026` (o `noviembre 2026` con `long`). Si no es un mes, devuelve el texto tal cual. */
export function monthLabel(key: string | undefined | null, long = false): string {
  const m = /^(\d{4})-(\d{2})$/.exec(key ?? '');
  if (!m) return key ?? '—';
  const i = Number(m[2]) - 1;
  return `${(long ? MONTHS_LONG : MONTHS)[i] ?? m[2]} ${m[1]}`;
}

/** `LIMA-JULIACA` → `Lima → Juliaca`. */
export function routeLabel(key: string | undefined | null): string {
  const [o, d] = (key ?? '').split('-');
  return d ? `${CITY_LABELS[o] ?? o} → ${CITY_LABELS[d] ?? d}` : key ?? '—';
}

/** Porcentaje desde una fracción: `0.0337` → `3.4 %`. */
export const formatPercent = (fraction: number | null | undefined, digits = 1) => (fraction == null ? '—' : `${formatNumber(fraction * 100, digits)} %`);

export { formatMoney };
