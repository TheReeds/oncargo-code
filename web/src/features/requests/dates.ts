// Fechas de «Duplicar» (zona America/Lima; las fechas viajan como texto `yyyy-MM-dd`).

/** Primer día no pasado con el mismo día de la semana que `date`, una semana después (o más, si ya pasó). */
export function nextWeekSameDay(date: string | null | undefined, today: string): string {
  const [ty, tm, td] = today.split('-').map(Number);
  const t = Date.UTC(ty, tm - 1, td);
  const base = date ? date.split('-').map(Number) : null;
  let d = base ? Date.UTC(base[0], base[1] - 1, base[2]) + 7 * 86_400_000 : t;
  while (d < t) d += 7 * 86_400_000;
  return new Date(d).toISOString().slice(0, 10);
}
