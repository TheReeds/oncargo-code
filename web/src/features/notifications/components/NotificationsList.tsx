import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { BellOff, CheckCheck } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonList } from '@/components/ui/skeleton';
import { Alert } from '@/components/ui/alert';
import { EmptyState } from '@/components/domain/EmptyState';
import { toast } from '@/components/ui/toaster';
import { useLiveRefresh } from '@/lib/realtime';
import { notificationHref, type NotifOrg } from '@/lib/notification-links';
import { cn } from '@/lib/utils';
import { NotificationItem, type NotificationRow } from './NotificationItem';

type Row = NotificationRow & { relatedType?: string; relatedId?: string };
type Filter = 'all' | 'unread' | 'mine';
const SIZE = 20;
const TZ = 'America/Lima';

const dayKey = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ }).format(d);
const hourLabel = (iso?: string) => (iso ? new Intl.DateTimeFormat('es-PE', { timeZone: TZ, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(iso)) : '');
const dateLabel = (iso?: string) => {
  if (!iso) return '';
  const parts = new Intl.DateTimeFormat('es-PE', { timeZone: TZ, day: 'numeric', month: 'short' }).format(new Date(iso)).replace(/\./g, '');
  return parts;
};

/** Agrupa filas (ya ordenadas de la más reciente a la más antigua) en Hoy / Ayer / Anteriores, por día en hora de Lima. */
function groupByDay(rows: Row[], now = Date.now()) {
  const today = dayKey(new Date(now));
  const yesterday = dayKey(new Date(now - 86_400_000));
  const groups: { key: string; label: string; rows: Row[] }[] = [];
  for (const r of rows) {
    const k = r.createdAt ? dayKey(new Date(r.createdAt)) : '';
    const g = k === today ? 'today' : k === yesterday ? 'yesterday' : 'older';
    let grp = groups.find((x) => x.key === g);
    if (!grp) { grp = { key: g, label: g === 'today' ? 'Hoy' : g === 'yesterday' ? 'Ayer' : 'Anteriores', rows: [] }; groups.push(grp); }
    grp.rows.push(r);
  }
  return groups;
}

/**
 * Página /notificaciones: Todas / No leídas / Solo míos (V16: avisos por persona; «Tuyo» = de algo que creaste), agrupadas
 * por día, con marcar leídas (solo para ti) y enlace directo al objeto.
 */
export default function NotificationsList({ orgType }: { orgType: NotifOrg }) {
  const [filter, setFilter] = useState<Filter>('all');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<Row[] | null>(null);
  const [total, setTotal] = useState(0);
  const [unread, setUnread] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const seq = useRef(0);
  const load = useCallback(async (f: Filter, p: number) => {
    setError(null);
    const mine = ++seq.current;
    const [list, count] = await Promise.all([
      actions.notifications.list({ unread: f === 'unread', mine: f === 'mine', page: p - 1, size: SIZE }),
      actions.notifications.unreadCount(),
    ]);
    if (mine !== seq.current) return; // respuesta obsoleta (cambió el filtro o la página)
    if (count.data) setUnread(count.data.unread);
    if (list.error || !list.data) { setError(list.error?.message ?? 'No pudimos cargar tus notificaciones.'); setRows([]); return; }
    setRows(list.data.items as Row[]);
    setTotal(list.data.meta.total);
  }, []);

  useEffect(() => { load(filter, page); }, [filter, page, load]);
  useLiveRefresh({ types: ['notification.created'], refresh: () => load(filter, page), fallbackMs: 60_000 });

  const groups = useMemo(() => (rows ? groupByDay(rows) : []), [rows]);

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
    <Card className="min-w-0 overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-steel-200 px-4 py-2 sm:px-5">
        <Tabs value={filter} onValueChange={(v) => { setFilter(v as Filter); setPage(1); setRows(null); }}>
          <TabsList aria-label="Filtrar notificaciones" className="border-b-0">
            <TabsTrigger value="all" aria-controls={undefined}>Todas</TabsTrigger>
            <TabsTrigger value="unread" aria-controls={undefined}>No leídas{unread ? <span className="ml-1.5 rounded-full bg-petrol-700 px-1.5 font-mono text-[11px] tabular-nums text-on-solid">{unread}</span> : null}</TabsTrigger>
            <TabsTrigger value="mine" aria-controls={undefined}>Solo míos</TabsTrigger>
          </TabsList>
        </Tabs>
        <Button variant="outline" size="sm" onClick={readAll} loading={busy} disabled={unread === 0}><CheckCheck /> Marcar todas como leídas</Button>
      </div>
      {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar tus notificaciones" action={<Button variant="outline" size="sm" onClick={() => load(filter, page)}>Reintentar</Button>}>{error}</Alert></div>}
      {rows === null ? (
        <SkeletonList rows={5} />
      ) : rows.length === 0 && !error ? (
        <EmptyState icon={filter === 'unread' ? CheckCheck : BellOff}
          title={filter === 'unread' ? 'No tienes notificaciones sin leer' : filter === 'mine' ? 'Nada tuyo por ahora' : 'Todavía no tienes notificaciones'}
          description={filter === 'unread' ? 'Estás al día. Las nuevas aparecerán aquí al instante.' : filter === 'mine' ? 'Aquí verás los avisos de las solicitudes y ofertas que tú creaste.' : 'Aquí verás ofertas, mensajes, cambios en tus envíos e incidencias.'} />
      ) : (
        <div>
          {groups.map((g) => (
            <section key={g.key} aria-labelledby={`n-${g.key}`}>
              <h2 id={`n-${g.key}`} className="border-b border-steel-200 bg-steel-100 px-4 py-1.5 text-xs font-semibold uppercase tracking-wide text-ink-500 sm:px-5">{g.label}</h2>
              <ul className="divide-y divide-steel-200 border-b border-steel-200 last:border-b-0">
                {g.rows.map((n) => (
                  <li key={n.id} className={cn('flex flex-col items-stretch sm:flex-row sm:items-start sm:gap-2 sm:pr-3', n.read ? '' : 'border-l-2 border-petrol-700 bg-petrol-50/50')}>
                    <a href={notificationHref(n, orgType)} onClick={(e) => { e.preventDefault(); open(n); }} className="block min-w-0 flex-1 px-4 py-3.5 hover:bg-steel-100/60 sm:px-5">
                      <NotificationItem n={n} clamp={false} showKind time={g.key === 'older' ? `${dateLabel(n.createdAt)} · ${hourLabel(n.createdAt)}` : hourLabel(n.createdAt)} />
                    </a>
                    {!n.read && <Button variant="ghost" size="sm" className="mx-4 mb-2 self-start sm:mx-0 sm:mb-0 sm:mt-2.5 sm:shrink-0" onClick={() => readOne(n)}>Marcar como leída<span className="sr-only">: {n.title}</span></Button>}
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
      {rows && rows.length > 0 && total > SIZE && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={SIZE} total={total} onPageChange={(p) => { setPage(p); setRows(null); }} /></div>}
    </Card>
  );
}
