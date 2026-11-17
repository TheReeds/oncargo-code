import { useRealtimeStatus, type RealtimeStatus } from '@/lib/realtime';
import { cn } from '@/lib/utils';

const LABEL: Record<RealtimeStatus, string> = {
  connecting: 'Conectando…',
  live: 'En vivo',
  reconnecting: 'Reconectando…',
  offline: 'Sin conexión en vivo',
};
const HINT: Record<RealtimeStatus, string> = {
  connecting: 'Conectando las actualizaciones en vivo.',
  live: 'Las ofertas, mensajes y envíos se actualizan solos.',
  reconnecting: 'Se perdió la conexión en vivo; reintentando. Mientras tanto actualizamos cada minuto.',
  offline: 'No hay conexión en vivo; actualizamos cada minuto.',
};

/** Indicador discreto del stream en tiempo real (barra superior). Abre la conexión compartida de la pestaña. */
export default function LiveIndicator({ className }: { className?: string }) {
  const status = useRealtimeStatus();
  const live = status === 'live';
  return (
    <span
      role="status"
      aria-live="polite"
      title={HINT[status]}
      data-testid="live-indicator"
      data-status={status}
      className={cn('inline-flex shrink-0 items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium',
        live ? 'text-emerald-700' : status === 'connecting' ? 'text-ink-500' : 'bg-amber-50 text-amber-700', className)}
    >
      <span aria-hidden="true" className="relative flex h-2 w-2">
        {live && <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-500 opacity-60 motion-reduce:animate-none" />}
        <span className={cn('relative inline-flex h-2 w-2 rounded-full', live ? 'bg-emerald-500' : status === 'connecting' ? 'bg-ink-400' : 'bg-amber-500')} />
      </span>
      <span className="sr-only sm:not-sr-only">{LABEL[status]}</span>
    </span>
  );
}
