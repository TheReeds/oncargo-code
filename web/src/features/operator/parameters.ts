import type { ParameterRow } from './server';

/** Valor legible: la comisión como porcentaje ("8 %"), un interruptor («Activado»), el resto en horas ("24 h"). */
export function formatParam(kind: string | undefined, value: string | undefined): string {
  if (value == null) return '—';
  if (kind === 'FLAG') return value === '1' ? 'Activado' : 'Desactivado';
  if (kind === 'RATE') return `${rateToPercent(value)} %`;
  if (kind === 'AMOUNT') return `S/ ${Number(value).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  if (kind === 'MINUTES') {
    const m = Number(value);
    return m >= 60 && m % 60 === 0 ? `${m / 60} h` : `${value} min`;
  }
  if (kind === 'COUNT') return `${value} ${value === '1' ? 'oferta' : 'ofertas'}`;
  if (kind === 'DAYS') return `${value} ${value === '1' ? 'día' : 'días'}`;
  return `${value} h`;
}

/** "0.0825" → "8.25" (porcentaje sin ceros sobrantes). Aritmética decimal sobre el texto: sin errores de coma flotante. */
export function rateToPercent(rate: string): string {
  const [i = '0', d = ''] = rate.split('.');
  const digits = (d + '00').slice(0, Math.max(2, d.length));
  const intPart = String(Number(i + digits.slice(0, 2)));
  const frac = digits.slice(2).replace(/0+$/, '');
  return frac ? `${intPart}.${frac}` : intPart;
}

/** "8.25" (porcentaje) → "0.0825" (fracción que espera la API); `null` si no es un número válido de hasta 2 decimales. */
export function percentToRate(percent: string): string | null {
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(percent)) return null;
  const [i, d = ''] = percent.split('.');
  const scaled = Number(i) * 100 + Number((d + '00').slice(0, 2));   // centésimas de punto porcentual
  const s = String(scaled).padStart(5, '0');
  const rate = `${s.slice(0, -4)}.${s.slice(-4)}`.replace(/\.?0+$/, '');
  return rate === '' ? '0' : rate;
}

/** Valor en el campo de edición: porcentaje para RATE, horas para HOURS. */
export const toInput = (r: ParameterRow) => (r.kind === 'RATE' ? rateToPercent(r.value!) : r.value!);
