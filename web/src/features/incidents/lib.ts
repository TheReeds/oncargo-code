/** Horas que lleva (o llevó) abierta una incidencia. */
export function hoursOpen(i: { createdAt?: string; resolvedAt?: string; active?: boolean }, now = Date.now()) {
  if (!i.createdAt) return 0;
  const end = !i.active && i.resolvedAt ? new Date(i.resolvedAt).getTime() : now;
  return Math.max(0, (end - new Date(i.createdAt).getTime()) / 3_600_000);
}

export function formatOpenTime(hours: number) {
  if (hours < 1) return `${Math.max(1, Math.round(hours * 60))} min`;
  if (hours < 24) return `${Math.floor(hours)} h`;
  const d = Math.floor(hours / 24);
  const h = Math.floor(hours % 24);
  return h ? `${d} d ${h} h` : `${d} d`;
}

/** Umbrales por omisión de color de una incidencia activa (RN11). El operador los cambia en /operador/parametros y llegan en `/operator/metrics`. */
export const DEFAULT_AGE_THRESHOLDS = { warn: 12, breach: 24 } as const;
/** Ámbar pasado `warn` horas, rojo pasado `breach` (solo activas). */
export const ageTone = (hours: number, active?: boolean, t: { warn: number; breach: number } = DEFAULT_AGE_THRESHOLDS) =>
  (!active ? 'text-ink-700' : hours > t.breach ? 'font-semibold text-red-600' : hours > t.warn ? 'font-semibold text-amber-700' : 'text-ink-700');

export const CATEGORY_OPTIONS = [
  ['CARGO_DAMAGE', 'Daño en la carga'], ['DELAY', 'Retraso'], ['LOST_CARGO', 'Carga perdida'], ['WRONG_DELIVERY', 'Entrega equivocada'], ['DRIVER_CONDUCT', 'Conducta del conductor'], ['OTHER', 'Otro'],
].map(([value, label]) => ({ value, label }));

export const STATUS_LABEL_LONG: Record<string, string> = { OPEN: 'Abierta', IN_REVIEW: 'En revisión', RESOLVED: 'Resuelta', CLOSED: 'Cerrada' };

/** Estados finales del envío que el operador puede fijar al resolver o cerrar, según cómo esté el envío (RF55, `IncidentOutcomePolicy`). */
export const OUTCOME_BY_SHIPMENT: Record<string, string[]> = {
  PENDING: ['RESUME', 'CANCELLED'],
  IN_TRANSIT: ['RESUME', 'DELIVERED', 'CANCELLED'],
  DELIVERED: ['DELIVERED'],
  CANCELLED: ['CANCELLED'],
};
export const OUTCOME_LABEL: Record<string, { label: string; hint: string }> = {
  RESUME: { label: 'El envío continúa', hint: 'Se libera y el conductor retoma las paradas.' },
  DELIVERED: { label: 'Darlo por entregado', hint: 'Se cierran las paradas y el envío queda entregado.' },
  CANCELLED: { label: 'Cancelar el envío', hint: 'Se cancelan el envío y la solicitud, y se libera la flota. No se puede deshacer.' },
};
