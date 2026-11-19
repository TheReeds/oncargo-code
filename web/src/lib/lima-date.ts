// Fechas calendario (LocalDate `YYYY-MM-DD`) en la zona de Lima. Sin `Date` locales: sirven igual en el servidor y en el navegador.
const fmt = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' });

/** Hoy en Lima como `YYYY-MM-DD`. */
export const limaToday = (): string => fmt.format(new Date());

const toUtc = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
};

/** Suma `n` días (puede ser negativo) a una fecha ISO. */
export function addDays(iso: string, n: number): string {
  return new Date(toUtc(iso) + n * 86_400_000).toISOString().slice(0, 10);
}

/** Días de calendario de `a` a `b` (positivo si `b` es posterior). */
export const daysBetween = (a: string, b: string): number => Math.round((toUtc(b) - toUtc(a)) / 86_400_000);

/** Lista de `count` fechas consecutivas desde `from`. */
export const dayRange = (from: string, count: number): string[] => Array.from({ length: count }, (_, i) => addDays(from, i));

/** Lunes de la semana de `iso` (la semana de la agenda empieza en lunes). */
export function mondayOf(iso: string): string {
  const dow = new Date(toUtc(iso)).getUTCDay(); // 0 = domingo
  return addDays(iso, -((dow + 6) % 7));
}

/** `lun`, `mar`… */
export const weekdayShort = (iso: string): string => new Intl.DateTimeFormat('es-PE', { timeZone: 'UTC', weekday: 'short' }).format(new Date(toUtc(iso))).replace('.', '').toLowerCase();

/** Día calendario en Lima (`YYYY-MM-DD`) de un instante ISO (`2026-11-19T02:06:03Z` → `2026-11-18`). */
export const limaDateOf = (iso: string): string => fmt.format(new Date(iso));
