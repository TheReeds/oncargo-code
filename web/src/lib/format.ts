// Formatos es-PE: montos `S/ 1,850.00`, fechas `28 nov · 08:00`.
const money = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const formatMoney = (n: number | null | undefined) =>
  n == null ? '—' : `S/ ${money.format(n).replace(/\s/g, '')}`;

export const formatNumber = (n: number | null | undefined, digits = 0) =>
  n == null ? '—' : new Intl.NumberFormat('es-PE', { maximumFractionDigits: digits }).format(n);

const TZ = 'America/Lima';
const parts = (d: Date, opts: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('es-PE', { timeZone: TZ, ...opts }).formatToParts(d);

export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return '—';
  const d = new Date(iso);
  const p = Object.fromEntries(parts(d, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }).map((x) => [x.type, x.value]));
  return `${p.day} ${String(p.month).replace('.', '')} · ${p.hour}:${p.minute}`;
}

/** Fecha sin hora (LocalDate `2026-11-28`) → `28 nov`. */
export function formatDate(date: string | null | undefined) {
  if (!date) return '—';
  const [y, m, d] = date.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d, 12));
  const mon = new Intl.DateTimeFormat('es-PE', { timeZone: 'UTC', month: 'short' }).format(dt).replace('.', '').toLowerCase();
  return `${d} ${mon}`;
}

export const formatKm = (n: number | null | undefined) => (n == null ? '—' : `${formatNumber(n)} km`);
export const formatTons = (kg: number | null | undefined) =>
  kg == null ? '—' : `${new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 }).format(kg / 1000)} t`;

export function formatCountdown(ms: number) {
  if (ms <= 0) return 'Cerrada';
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (h >= 24) return `${Math.floor(h / 24)} d ${h % 24} h`;
  if (h > 0) return `${h} h ${String(m).padStart(2, '0')} min`;
  return `${m}:${String(sec).padStart(2, '0')} min`;
}

export const CARGO_LABELS: Record<string, string> = {
  GENERAL: 'Carga general', FRAGILE: 'Frágil', PERISHABLE: 'Perecible', HAZARDOUS: 'Peligrosa', BULK: 'A granel', OVERSIZED: 'Sobredimensionada',
};
export const VEHICLE_LABELS: Record<string, string> = {
  VAN: 'Van', TRUCK_SMALL: 'Camión pequeño', TRUCK_MEDIUM: 'Camión mediano', TRUCK_LARGE: 'Camión grande', PLATFORM: 'Plataforma', REFRIGERATED: 'Refrigerado', TANKER: 'Cisterna',
};
export const CITY_LABELS: Record<string, string> = {
  LIMA: 'Lima', ICA: 'Ica', NAZCA: 'Nazca', AREQUIPA: 'Arequipa', MOQUEGUA: 'Moquegua', TACNA: 'Tacna', JULIACA: 'Juliaca', PUNO: 'Puno', CUSCO: 'Cusco', ABANCAY: 'Abancay', AYACUCHO: 'Ayacucho',
};
