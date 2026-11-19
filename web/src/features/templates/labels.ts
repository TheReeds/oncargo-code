import { formatDate } from '@/lib/format';
import type { TemplateView } from './server';

export const WEEKDAYS = [
  { value: '1', label: 'Lunes', plural: 'lunes' }, { value: '2', label: 'Martes', plural: 'martes' },
  { value: '3', label: 'Miércoles', plural: 'miércoles' }, { value: '4', label: 'Jueves', plural: 'jueves' },
  { value: '5', label: 'Viernes', plural: 'viernes' }, { value: '6', label: 'Sábado', plural: 'sábados' },
  { value: '7', label: 'Domingo', plural: 'domingos' },
];
export const weekdayPlural = (n: number) => WEEKDAYS[n - 1]?.plural ?? '';

type Schedule = NonNullable<TemplateView['schedule']>;

/** «Todos los lunes» / «Un lunes sí y otro no». */
export function frequencyText(s: Pick<Schedule, 'frequency' | 'pickupWeekday'>) {
  const day = weekdayPlural(s.pickupWeekday ?? 1);
  return s.frequency === 'BIWEEKLY' ? `Cada dos semanas, los ${day}` : `Todos los ${day}`;
}

/** «Se publica 2 días antes» / «Se publica el mismo día». */
export function leadText(leadDays: number | undefined) {
  const n = leadDays ?? 0;
  return n === 0 ? 'Se publica el día del recojo' : `Se publica ${n} ${n === 1 ? 'día' : 'días'} antes`;
}

/** Estado de la programación para la lista: texto + tono del `Badge`. */
export function scheduleState(t: TemplateView): { label: string; tone: 'emerald' | 'amber' | 'neutral' | 'red' } {
  const s = t.schedule;
  if (t.archived) return { label: 'Archivada', tone: 'neutral' };
  if (!s) return { label: 'Sin programar', tone: 'neutral' };
  if (s.finished) return { label: 'Terminada', tone: 'neutral' };
  if (s.paused) return { label: (s.failures ?? 0) >= 3 ? 'Pausada por fallos' : 'Pausada', tone: (s.failures ?? 0) >= 3 ? 'red' : 'amber' };
  return { label: s.mode === 'DRAFT' ? 'Deja borrador' : 'Publica sola', tone: 'emerald' };
}

export const nextText = (s: Schedule) => (s.finished || s.paused ? '' : `Próximo recojo: ${formatDate(s.nextPickupDate)}`);

export const RUN_LABEL: Record<string, string> = { PUBLISHED: 'Publicada', DRAFT: 'Borrador', FAILED: 'No se pudo publicar', SKIPPED: 'Saltada', DISCARDED: 'Descartada' };
export const RUN_TONE: Record<string, 'emerald' | 'amber' | 'red' | 'neutral'> = { PUBLISHED: 'emerald', DRAFT: 'amber', FAILED: 'red', SKIPPED: 'neutral', DISCARDED: 'neutral' };

/** Primer recojo de una programación nueva: el primer día elegido cuyo aviso aún no pasó (espeja `RecurrencePolicy.firstPickup`). */
export function firstPickup(weekday: number, leadDays: number, today: string): string {
  const [y, m, d] = today.split('-').map(Number);
  const base = new Date(Date.UTC(y, m - 1, d + leadDays));
  const isoDay = base.getUTCDay() === 0 ? 7 : base.getUTCDay();
  base.setUTCDate(base.getUTCDate() + ((weekday - isoDay + 7) % 7));
  return base.toISOString().slice(0, 10);
}
