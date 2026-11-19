import { Timeline, type TimelineItem } from '@/components/ui/timeline';
import { CITY_LABELS, formatDateTime } from '@/lib/format';
import type { StopView } from '@/lib/api/server';

/** Minutos de diferencia entre que ocurrió un hito (hora del conductor) y que llegó al servidor para avisar del retraso. */
export const LATE_SYNC_MINUTES = 10;

/** El evento se registró con señal recuperada: llegó al servidor más de 10 min después de ocurrir (roadmap 1.11). */
export function lateSync(occurredAt?: string | null, recordedAt?: string | null): boolean {
  if (!occurredAt || !recordedAt) return false;
  return new Date(recordedAt).getTime() - new Date(occurredAt).getTime() > LATE_SYNC_MINUTES * 60_000;
}

/** Mapea las paradas de un envío al `Timeline` compartido. */
export function stopsToItems(stops: StopView[]): TimelineItem[] {
  return [...stops].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).map((s) => {
    const done = s.status === 'COMPLETED';
    const here = s.status === 'ARRIVED';
    const time = s.completedAt ?? s.arrivedAt;
    const late = done ? lateSync(s.completedAt, s.completedRecordedAt) : lateSync(s.arrivedAt, s.arrivedRecordedAt);
    return {
      id: s.id!,
      title: `${s.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · ${CITY_LABELS[s.city!] ?? s.city}`,
      detail: s.address,
      meta: `${done ? 'Completada' : here ? 'El conductor llegó' : 'Pendiente'}${time ? ` · ${formatDateTime(time)}` : ''}${late ? ' · registrado con retraso (sin señal)' : ''}`,
      status: done ? 'done' : here ? 'current' : 'pending',
    };
  });
}

/** Paradas en horizontal (web). */
export function StopTimeline({ stops, orientation = 'horizontal' }: { stops: StopView[]; orientation?: 'horizontal' | 'vertical' }) {
  if (!stops.length) return <p className="text-sm text-ink-500">Este envío aún no tiene paradas registradas.</p>;
  return <Timeline label="Paradas del envío" orientation={orientation} items={stopsToItems(stops)} />;
}
