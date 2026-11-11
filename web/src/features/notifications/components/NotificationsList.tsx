import { useCallback, useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { CheckCheck } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonRows } from '@/components/ui/skeleton';
import { Alert } from '@/components/ui/alert';
import { EmptyState } from '@/components/domain/EmptyState';
import { toast } from '@/components/ui/toaster';
import { notificationHref, type NotifOrg } from '@/lib/notification-links';
import { NotificationItem, type NotificationRow } from './NotificationItem';

type Row = NotificationRow & { relatedType?: string; relatedId?: string };
const SIZE = 20;

/** Página /notificaciones: filtro Todas / No leídas, paginación de 20 y marcar leídas. */
export default function NotificationsList({ orgType }: { orgType: NotifOrg }) {
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
 

  const seq = useRef(0);
  const load = useCallback(async (f: 'all' | 'unread', p: number) => {
    setError(null);
    const mine = ++seq.current;
    const { data, error: err } = await actions.notifications.list({ unread: f === 'unread', page: p - 1, size: SIZE });
    if (mine !== seq.current) return; // respuesta obsoleta (cambió el filtro o la página)
    if (err || !data) { setError(err?.message ?? 'No pudimos cargar tus notificaciones.'); setRows([]); return; }
    setRows(data.items as Row[]);
    setTotal(data.meta.total);
  }, []);

  useEffect(() => { load(filter, page); }, [filter, page, load]);

  async function open(n: Row) {
    if (!n.read && n.id) await actions.notifications.markRead({ id: n.id }).catch(() => null);
    window.location.href = notificationHref(n, orgType);
  }

  async function readAll() {
    setBusy(true);
    const { error: err } = await actions.notifications.markAllRead();
    setBusy(false);
    if (err) { toast.error(err.message); return; }
    toast.success('Notificaciones marcadas como leídas');
    load(filter, page);
  }

  async function readOne(n: Row) {
    if (!n.id) return;
    const { error: err } = await actions.notifications.markRead({ id: n.id });
    if (err) { toast.error(err.message); return; }
    load(filter, page);
  }

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-steel-200 px-4 py-2 sm:px-5">
        <Tabs value={filter} onValueChange={(v) => { setFilter(v as 'all' | 'unread'); setPage(1); setRows(null); }}>
          <TabsList aria-label="Filtrar notificaciones" className="border-b-0">
            <TabsTrigger value="all" aria-controls={undefined}>Todas</TabsTrigger>
            <TabsTrigger value="unread" aria-controls={undefined}>No leídas</TabsTrigger>
          </TabsList>
        </Tabs>
        <Button variant="outline" size="sm" onClick={readAll} loading={busy}><CheckCheck /> Marcar todas como leídas</Button>
      </div>
      {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar tus notificaciones">{error}</Alert></div>}
      {rows === null ? (
        <div className="p-4"><SkeletonRows rows={5} /></div>
      ) : rows.length === 0 && !error ? (
        <EmptyState title={filter === 'unread' ? 'No tienes notificaciones sin leer' : 'Todavía no tienes notificaciones'} description="Aquí verás ofertas, mensajes, cambios en tus envíos e incidencias." />
      ) : (
        <ul className="divide-y divide-steel-200">
          {rows.map((n) => (
            <li key={n.id} className={`flex items-start gap-2 pr-3 ${n.read ? '' : 'bg-petrol-50/50'}`}>
              <a href={notificationHref(n, orgType)} onClick={(e) => { e.preventDefault(); open(n); }} className="block min-w-0 flex-1 px-4 py-3.5 hover:bg-steel-100/60 sm:px-5">
                <NotificationItem n={n} clamp={false} />
              </a>
              {!n.read && <Button variant="ghost" size="sm" className="mt-2.5 shrink-0" onClick={() => readOne(n)}>Marcar como leída</Button>}
            </li>
          ))}
        </ul>
      )}
      {rows && rows.length > 0 && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={SIZE} total={total} onPageChange={(p) => { setPage(p); setRows(null); }} /></div>}
    </Card>
  );
}
