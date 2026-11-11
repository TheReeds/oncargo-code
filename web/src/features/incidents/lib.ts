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

/** Ámbar > 12 h, rojo > 24 h (solo activas). */
export const ageTone = (hours: number, active?: boolean) => (!active ? 'text-ink-700' : hours > 24 ? 'font-semibold text-red-600' : hours > 12 ? 'font-semibold text-amber-700' : 'text-ink-700');

export const CATEGORY_OPTIONS = [
  ['CARGO_DAMAGE', 'Daño en la carga'], ['DELAY', 'Retraso'], ['LOST_CARGO', 'Carga perdida'], ['WRONG_DELIVERY', 'Entrega equivocada'], ['DRIVER_CONDUCT', 'Conducta del conductor'], ['OTHER', 'Otro'],
].map(([value, label]) => ({ value, label }));

export const STATUS_LABEL_LONG: Record<string, string> = { OPEN: 'Abierta', IN_REVIEW: 'En revisión', RESOLVED: 'Resuelta', CLOSED: 'Cerrada' };
