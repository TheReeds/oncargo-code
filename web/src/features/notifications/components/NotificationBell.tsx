import { useCallback, useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Bell, CheckCheck } from 'lucide-react';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useLiveRefresh, useRealtime } from '@/lib/realtime';
import { notificationHref, type NotifOrg } from '@/lib/notification-links';
import { NotificationItem, type NotificationRow } from './NotificationItem';

const FALLBACK_MS = 60_000;
type Row = NotificationRow & { relatedType?: string; relatedId?: string };

/** Campana de la barra superior: contador de no leídas (tiempo real; respaldo lento), últimas notificaciones y accesos a /notificaciones. */
export default function NotificationBell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<Row[] | null>(null);
  const [orgType, setOrgType] = useState<NotifOrg>(null);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);
  const openRef = useRef(false);

  const refreshCount = useCallback(async () => {
    if (document.hidden) return;
    const { data } = await actions.notifications.unreadCount();
    if (data) setUnread(data.unread);
  }, []);

  const loadList = useCallback(async () => {
    setError(false);
    const { data, error: err } = await actions.notifications.list({ size: 8 });
    if (err || !data) { setError(true); return; }
    setItems(data.items as Row[]);
    setOrgType(data.orgType as NotifOrg);
    refreshCount();
  }, [refreshCount]);

  useEffect(() => { refreshCount(); }, [refreshCount]);

  // `notification.created` trae el contador exacto; si el panel está abierto se recarga la lista.
  useRealtime(['notification.created'], (e) => {
    const n = Number(e.data.unreadCount);
    if (Number.isFinite(n)) setUnread(n); else refreshCount();
    if (openRef.current) loadList();
  });
  // Al reconectar y, solo sin conexión en vivo, cada minuto.
  useLiveRefresh({ types: [], refresh: () => { refreshCount(); if (openRef.current && !document.hidden) loadList(); }, fallbackMs: FALLBACK_MS });

  function onOpenChange(o: boolean) {
    setOpen(o);
    openRef.current = o;
    if (o) loadList();
  }

  async function go(n: Row) {
    const href = notificationHref(n, orgType);
    if (!n.read && n.id) {
      setUnread((u) => Math.max(0, u - 1));
      await actions.notifications.markRead({ id: n.id }).catch(() => null);
    }
    window.location.href = href;
  }

  async function readAll() {
    setBusy(true);
    const { error: err } = await actions.notifications.markAllRead();
    setBusy(false);
    if (err) { setError(true); return; }
    setUnread(0);
    setItems((l) => l?.map((n) => ({ ...n, read: true })) ?? l);
  }

  const badge = unread > 9 ? '9+' : String(unread);
  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <button type="button" className="relative rounded-input p-2 text-ink-700 hover:bg-steel-100" aria-haspopup="dialog">
          <Bell className="size-5" aria-hidden="true" />
          <span className="sr-only">{unread > 0 ? `Notificaciones, ${unread} sin leer` : 'Notificaciones'}</span>
          {unread > 0 && <span aria-hidden="true" className="absolute right-0.5 top-0.5 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[10px] font-semibold leading-4 text-on-solid">{badge}</span>}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(24rem,calc(100vw-1.5rem))] p-0" aria-label="Notificaciones">
        <div className="flex items-center justify-between gap-2 border-b border-steel-200 px-4 py-3">
          <div className="min-w-0"><h2 className="text-sm font-semibold text-ink-900">Notificaciones</h2>{unread > 0 && <p className="text-xs text-ink-500"><span className="font-mono tabular-nums">{unread}</span> sin leer</p>}</div>
          <Button variant="ghost" size="sm" onClick={readAll} disabled={busy || unread === 0}><CheckCheck /> Marcar todas como leídas</Button>
        </div>
        <div className="max-h-[min(26rem,65vh)] overflow-y-auto">
          {error ? (
            <div role="alert" className="space-y-2 px-4 py-6 text-center text-sm text-ink-700">
              <p>No pudimos cargar tus notificaciones. Inténtalo de nuevo.</p>
              <Button variant="outline" size="sm" onClick={loadList}>Reintentar</Button>
            </div>
          ) : items === null ? (
            <div className="space-y-3 p-4" aria-busy="true">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-12 w-full" />)}</div>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-8 text-center"><span className="grid size-10 place-items-center rounded-full bg-petrol-50 text-petrol-700"><Bell className="size-5" aria-hidden="true" /></span><p className="text-sm font-medium text-ink-900">Todavía no tienes notificaciones</p><p className="text-xs text-ink-500">Aquí verás ofertas, mensajes y cambios en tus envíos.</p></div>
          ) : (
            <ul className="divide-y divide-steel-200">
              {items.map((n) => (
                <li key={n.id}>
                  <a href={notificationHref(n, orgType)} onClick={(e) => { e.preventDefault(); go(n); }} className={`block px-4 py-3 hover:bg-steel-100/60 focus-visible:bg-steel-100 ${n.read ? '' : 'bg-petrol-50/50'}`}>
                    <NotificationItem n={n} />
                  </a>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="border-t border-steel-200 px-4 py-2.5 text-center">
          <a href="/notificaciones" className="text-sm font-medium text-petrol-700 hover:underline">Ver todas las notificaciones</a>
        </div>
      </PopoverContent>
    </Popover>
  );
}
