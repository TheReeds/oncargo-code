import { INCIDENT_CATEGORY_LABELS, INCIDENT_SEVERITY_LABELS } from '@/lib/format';

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

/** Informativas (RN11, F21): avisan sin detener el viaje. Espejo de `IncidentCategory.blocking` en el backend. */
export const INFORMATIVE_CATEGORIES = new Set(['DELAY', 'ROAD_BLOCKED', 'PAYMENT_DISPUTE']);

export const CATEGORY_OPTIONS = [
  'CARGO_DAMAGE', 'LOST_CARGO', 'PARTIAL_DELIVERY', 'WRONG_DELIVERY', 'PICKUP_NO_SHOW', 'SHIPPER_NO_SHOW', 'BREAKDOWN', 'ACCIDENT', 'THEFT',
  'DRIVER_CONDUCT', 'DELAY', 'ROAD_BLOCKED', 'PAYMENT_DISPUTE', 'OTHER',
].map((value) => ({ value, label: INCIDENT_CATEGORY_LABELS[value], hint: INFORMATIVE_CATEGORIES.has(value) ? 'Informativa: el viaje sigue' : 'Detiene el viaje' }));

export const SEVERITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => ({ value, label: INCIDENT_SEVERITY_LABELS[value] }));

/** Respuestas frecuentes del operador (2.7): se insertan en el comentario y se pueden editar antes de enviar. */
export const CANNED_REPLIES = [
  { value: 'revisando', label: 'Estamos revisando', text: 'Recibimos la incidencia y la estamos revisando. Te escribimos apenas tengamos novedades.' },
  { value: 'fotos', label: 'Pedir fotos', text: 'Para avanzar necesitamos fotos de la carga y, si la hay, de la guía de remisión. Súbelas en Documentos del envío.' },
  { value: 'coordinando', label: 'Coordinando con la contraparte', text: 'Estamos coordinando con la otra parte. Te avisamos en cuanto haya una propuesta.' },
  { value: 'cierre', label: 'Propuesta de cierre', text: 'Con lo revisado proponemos cerrar la incidencia. Si no estás de acuerdo, respóndenos aquí hoy.' },
];

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
