import { Timeline, type TimelineItem } from '@/components/ui/timeline';
import { CITY_LABELS, formatDateTime } from '@/lib/format';
import type { StopView } from '@/lib/api/server';

/** Mapea las paradas de un envío al `Timeline` compartido. */
export function stopsToItems(stops: StopView[]): TimelineItem[] {
  return [...stops].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).map((s) => {
    const done = s.status === 'COMPLETED';
    const here = s.status === 'ARRIVED';
    const time = s.completedAt ?? s.arrivedAt;
    return {
      id: s.id!,
      title: `${s.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · ${CITY_LABELS[s.city!] ?? s.city}`,
      detail: s.address,
      meta: `${done ? 'Completada' : here ? 'El conductor llegó' : 'Pendiente'}${time ? ` · ${formatDateTime(time)}` : ''}`,
      status: done ? 'done' : here ? 'current' : 'pending',
    };
  });
}

/** Paradas en horizontal (web). */
export function StopTimeline({ stops, orientation = 'horizontal' }: { stops: StopView[]; orientation?: 'horizontal' | 'vertical' }) {
  if (!stops.length) return <p className="text-sm text-ink-500">Este envío aún no tiene paradas registradas.</p>;
  return <Timeline label="Paradas del envío" orientation={orientation} items={stopsToItems(stops)} />;
}
