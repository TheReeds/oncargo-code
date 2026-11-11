import { notificationMeta } from '@/lib/notification-links';
import { formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

export type NotificationRow = { id?: string; type?: string; title?: string; body?: string; read?: boolean; createdAt?: string };

/** Contenido de una notificación (ícono por tipo, título, cuerpo, hora y punto de no leída). Se envuelve en un enlace/botón. */
export function NotificationItem({ n, clamp = true }: { n: NotificationRow; clamp?: boolean }) {
  const m = notificationMeta(n.type);
  const Icon = m.icon;
  return (
    <span className="flex items-start gap-3">
      <span className={cn('mt-0.5 grid size-8 shrink-0 place-items-center rounded-full', m.tone)} aria-hidden="true"><Icon className="size-4" /></span>
      <span className="min-w-0 flex-1">
        <span className="flex items-baseline justify-between gap-2">
          <span className={cn('truncate text-sm text-ink-900', n.read ? 'font-medium' : 'font-semibold')}>{n.title}</span>
          <span className="shrink-0 text-xs text-ink-500">{formatRelative(n.createdAt)}</span>
        </span>
        <span className={cn('mt-0.5 block text-[13px] text-ink-700', clamp && 'line-clamp-2')}>{n.body}</span>
      </span>
      {!n.read ? (
        <span className="mt-2 size-2 shrink-0 rounded-full bg-petrol-700"><span className="sr-only">Sin leer</span></span>
      ) : (
        <span className="size-2 shrink-0" aria-hidden="true" />
      )}
    </span>
  );
}
