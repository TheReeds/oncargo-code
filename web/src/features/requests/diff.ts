/** Diferencia porcentual de un monto frente al precio sugerido (`null` si no hay sugerido). */
export function diffPct(amount: number, suggested?: number | null) {
  if (!suggested) return null;
  return ((amount - suggested) / suggested) * 100;
}

/** `+4.2%` · `-14.9%` · `0.0%` (sin el "-0.0%" del redondeo). */
export function formatDiff(d: number) {
  const t = d.toFixed(1);
  return Number(t) === 0 ? '0.0%' : `${d > 0 ? '+' : ''}${t}%`;
}
