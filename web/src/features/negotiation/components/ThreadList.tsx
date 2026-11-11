import { Avatar } from '@/components/ui/avatar';
import { Skeleton } from '@/components/ui/skeleton';
import { StatusPill } from '@/components/domain/StatusPill';
import { cn } from '@/lib/utils';
import { formatMoney, formatRelative } from '@/lib/format';
import type { ChatThread } from '../server';

/** Lista de hilos de una solicitud (uno por transportista). */
export function ThreadList({ threads, selected, onSelect, loading, className }: { threads: ChatThread[]; selected: string | null; onSelect: (carrierOrgId: string) => void; loading?: boolean; className?: string }) {
  if (loading)
    return <div className={cn('space-y-2 p-3', className)} role="status" aria-label="Cargando conversaciones"><Skeleton className="h-16" /><Skeleton className="h-16" /></div>;
  return (
    <ul className={cn('divide-y divide-steel-200', className)} aria-label="Conversaciones por transportista">
      {threads.map((t) => {
        const on = t.carrierOrgId === selected;
        return (
          <li key={t.carrierOrgId}>
            <button
              type="button"
              onClick={() => onSelect(t.carrierOrgId!)}
              aria-current={on ? 'true' : undefined}
              className={cn('flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-steel-100', on && 'bg-petrol-50 hover:bg-petrol-50')}
            >
              <Avatar name={t.carrierName ?? '?'} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="flex items-baseline justify-between gap-2">
                  <span className="truncate text-sm font-semibold text-ink-900">{t.carrierName}</span>
                  <span className="shrink-0 text-xs text-ink-500">{t.lastMessageAt ? formatRelative(t.lastMessageAt) : ''}</span>
                </span>
                <span className="mt-0.5 flex items-center gap-2 text-xs">
                  {t.bidAmount != null ? <><span className="font-mono font-semibold tabular-nums text-emerald-700">{formatMoney(t.bidAmount)}</span>{t.bidStatus && <StatusPill status={t.bidStatus} kind="bid" />}</> : <span className="text-ink-500">Sin oferta aún</span>}
                </span>
                <span className="mt-1 flex items-center justify-between gap-2">
                  <span className="truncate text-xs text-ink-500">{t.lastMessage || 'Sin mensajes'}</span>
                  {(t.messageCount ?? 0) > 0 && <span className="shrink-0 rounded-full bg-steel-200 px-1.5 font-mono text-xs tabular-nums text-ink-700" title="Mensajes en el hilo"><span className="sr-only">Mensajes: </span>{t.messageCount}</span>}
                </span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
