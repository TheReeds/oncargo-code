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

export const INCIDENT_CATEGORY_LABELS: Record<string, string> = {
  CARGO_DAMAGE: 'Daño en la carga', DELAY: 'Retraso', LOST_CARGO: 'Carga perdida', WRONG_DELIVERY: 'Entrega equivocada', DRIVER_CONDUCT: 'Conducta del conductor', OTHER: 'Otro',
  // Ola 2 (2.7)
  PICKUP_NO_SHOW: 'El transportista no llegó al recojo', SHIPPER_NO_SHOW: 'No había carga en el recojo', BREAKDOWN: 'Avería del vehículo',
  ACCIDENT: 'Accidente', THEFT: 'Robo', ROAD_BLOCKED: 'Vía bloqueada', PARTIAL_DELIVERY: 'Entrega parcial', PAYMENT_DISPUTE: 'Disputa de pago',
};
export const INCIDENT_SEVERITY_LABELS: Record<string, string> = { LOW: 'Baja', MEDIUM: 'Media', HIGH: 'Alta', CRITICAL: 'Crítica' };
export const INCIDENT_STATUS_LABELS: Record<string, string> = { OPEN: 'Abierta', IN_REVIEW: 'En revisión', RESOLVED: 'Resuelta', CLOSED: 'Cerrada' };
export const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  PICKUP_PHOTO: 'Foto de la carga',
  SERVICE_ORDER: 'Orden de servicio', REMISSION_GUIDE: 'Guía de remisión', RECEIPT: 'Comprobante', DELIVERY_PHOTO: 'Foto de entrega', SIGNATURE: 'Firma de recepción', OTHER: 'Otro documento',
  PAYMENT_PROOF: 'Constancia de pago', CARRIER_INVOICE: 'Comprobante del transportista',
};

/** `hace 5 min`, `hace 3 h`, o la fecha corta si pasó más de un día. */
export function formatRelative(iso: string | null | undefined, now = Date.now()) {
  if (!iso) return '—';
  const diff = Math.max(0, now - new Date(iso).getTime());
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `hace ${h} h`;
  return formatDateTime(iso);
}

export const formatFileSize = (bytes: number | null | undefined) =>
  bytes == null ? '—' : bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(0)} KB` : `${(bytes / 1048576).toFixed(1)} MB`;

/** Tarifa de un viaje: `S/ 0.48 por kg` o `S/ 3,200.00 por el tramo`. */
export const formatTripRate = (mode: string | null | undefined, rate: number | null | undefined) =>
  mode === 'PER_TRIP' ? `${formatMoney(rate)} por el tramo` : `${formatMoney(rate)} por kg`;

/** Precio de una reserva (misma regla que el backend: PER_KG = tarifa × kg; PER_TRIP = tarifa × kg / capacidad; 2 decimales, mínimo S/ 1.00). */
export function estimateBookingPrice(mode: string | null | undefined, rate: number, kg: number, capacityKg: number) {
  const raw = mode === 'PER_TRIP' ? (capacityKg > 0 ? (rate * kg) / capacityKg : 0) : rate * kg;
  return Math.max(1, Math.round(raw * 100) / 100);
}
