import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Lock, SearchX, ShieldCheck, X } from 'lucide-react';
import { useLiveRefresh } from '@/lib/realtime';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonList } from '@/components/ui/skeleton';
import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { INCIDENT_CATEGORY_LABELS, formatDateTime, formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';
import { CATEGORY_OPTIONS, ageTone, formatOpenTime, hoursOpen } from '../lib';
import type { IncidentView, RouteMap } from '../server';

const SIZE = 20;
const ALL = '__all';
type Filter = 'ACTIVE' | 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'CLOSED' | 'ALL';

/**
 * Listado de incidencias. `variant="operator"`: bandeja global (activas primero, las más antiguas arriba: ámbar > 12 h, rojo > 24 h).
 * `variant="party"` (PYME/transportista): las de tu organización, activas primero y las más recientes arriba.
 * La API no filtra por categoría ni ordena por antigüedad: se traen todas las páginas (tope 20 × 100) y se filtra y ordena aquí.
 */
export default function IncidentsList({ variant, routes }: { variant: 'party' | 'operator'; routes: RouteMap }) {
  const operator = variant === 'operator';
  const [filter, setFilter] = useState<Filter>('ACTIVE');
  const [category, setCategory] = useState(ALL);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<IncidentView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const seq = useRef(0);

  const load = useCallback(async () => {
    setError(null);
    const mine = ++seq.current;
    const all: IncidentView[] = [];
    for (let p = 0; p < 20; p++) {
      const { data, error: err } = await actions.incidents.list({ page: p, size: 100 });
      if (mine !== seq.current) return; // respuesta obsoleta
      if (err || !data) { setError(err?.message ?? 'No pudimos cargar las incidencias.'); setRows((r) => r ?? []); return; }
      all.push(...data.items);
      if (all.length >= data.meta.total || data.items.length === 0) break;
    }
    setRows(all);
  }, []);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], refresh: load, fallbackMs: 60_000 });

  const counts = useMemo(() => {
    const c: Record<string, number> = { ACTIVE: 0, OPEN: 0, IN_REVIEW: 0, RESOLVED: 0, CLOSED: 0, ALL: rows?.length ?? 0 };
    (rows ?? []).forEach((i) => { if (i.status) c[i.status] = (c[i.status] ?? 0) + 1; if (i.active) c.ACTIVE++; });
    return c;
  }, [rows]);

  const visible = useMemo(() => {
    if (!rows) return null;
    const t = (i: IncidentView) => new Date(i.createdAt!).getTime();
    return rows
      .filter((i) => (filter === 'ALL' ? true : filter === 'ACTIVE' ? i.active : i.status === filter))
      .filter((i) => category === ALL || i.category === category)
      // activas primero; en la bandeja del operador las más antiguas arriba (cola), para las partes las más recientes
      .sort((a, b) => Number(!!b.active) - Number(!!a.active) || (operator && a.active ? t(a) - t(b) : t(b) - t(a)));
  }, [rows, filter, category, operator]);

  async function review(i: IncidentView) {
    if (!(await confirm({ title: '¿Pasar la incidencia a revisión?', description: 'Las partes verán que LOGYX ya la está atendiendo. El envío sigue detenido.', confirmLabel: 'Pasar a revisión' }))) return;
    setBusyId(i.id!);
    const { error: err } = await actions.incidents.changeStatus({ id: i.id!, status: 'IN_REVIEW' });
    setBusyId(null);
    if (err) { toast.error(err.message); return; }
    toast.success('Incidencia en revisión');
    load();
  }

  const total = visible?.length ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / SIZE));
  const current = Math.min(page, pageCount);
  const pageRows = (visible ?? []).slice((current - 1) * SIZE, current * SIZE);
  const frozen = counts.ACTIVE;
  const options = [
    { value: 'ACTIVE', label: 'Activas', count: rows ? counts.ACTIVE : undefined },
    ...(operator ? [{ value: 'OPEN', label: 'Abiertas', count: rows ? counts.OPEN : undefined }, { value: 'IN_REVIEW', label: 'En revisión', count: rows ? counts.IN_REVIEW : undefined }] : []),
    { value: 'RESOLVED', label: 'Resueltas', count: rows ? counts.RESOLVED : undefined },
    { value: 'CLOSED', label: 'Cerradas', count: rows ? counts.CLOSED : undefined },
    { value: 'ALL', label: 'Todas', count: rows ? counts.ALL : undefined },
  ];
  const change = (v: string) => { setFilter(v as Filter); setPage(1); };
  const reset = () => { setFilter('ACTIVE'); setCategory(ALL); setPage(1); };

  return (
    <div className="min-w-0 space-y-3">
      <QuickFilters label="Estado de la incidencia" value={filter} onValueChange={change} options={options} />
      {frozen > 0 && (
        <p className="flex items-start gap-2 rounded-card border border-red-600/25 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">
          <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span><span className="font-mono font-semibold">{frozen}</span> {frozen === 1 ? 'envío está detenido' : 'envíos están detenidos'} por una incidencia activa: el conductor no puede avanzar las paradas hasta que se resuelva o se cierre (RN11).</span>
        </p>
      )}
      <Card className="min-w-0 overflow-hidden">
        <div role="search" className="flex flex-wrap items-end justify-between gap-x-5 gap-y-3 border-b border-steel-200 p-4 sm:px-5">
          <Field label="Categoría" htmlFor="f-category" className="w-full sm:w-64">
            <Select id="f-category" value={category} onValueChange={(v) => { setCategory(v); setPage(1); }} options={[{ value: ALL, label: 'Todas las categorías' }, ...CATEGORY_OPTIONS]} />
          </Field>
          <div className="flex min-h-10 items-center gap-3">
            {rows && <span className="text-sm text-ink-500" aria-live="polite"><span className="font-mono font-semibold text-ink-900">{total}</span> {total === 1 ? 'incidencia' : 'incidencias'}</span>}
            {category !== ALL && <Button variant="ghost" size="sm" onClick={() => { setCategory(ALL); setPage(1); }}><X /> Limpiar filtros</Button>}
          </div>
        </div>

        {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar las incidencias" action={<Button variant="outline" size="sm" onClick={load}>Reintentar</Button>}>{error}</Alert></div>}
        {visible === null ? <SkeletonList rows={5} /> : visible.length === 0 && !error ? (
          category !== ALL || filter !== 'ACTIVE' ? (
            <EmptyState icon={SearchX} title="No hay incidencias con estos filtros" description="Prueba con otro estado o categoría." action={<Button variant="outline" onClick={reset}>Ver incidencias activas</Button>} />
          ) : (
            <EmptyState icon={ShieldCheck} title={operator ? 'Sin incidencias activas' : 'No tienes incidencias activas'}
              description={operator ? 'Cuando una PYME o un transportista reporte un problema, aparecerá aquí y el envío quedará detenido hasta resolverlo.' : 'Si algo sale mal en un envío, repórtalo desde la pestaña Incidencias de ese envío.'} />
          )
        ) : visible.length > 0 && (
          <Table label="Incidencias" stack>
            <thead>
              <tr>
                <Th>Envío</Th>{operator && <Th>Partes</Th>}<Th>Categoría</Th><Th>Estado</Th><Th>Reportada</Th><Th className="text-right">Tiempo abierta</Th><Th className="relative"><span className="sr-only">Acciones</span></Th>
              </tr>
            </thead>
            <tbody>
              {pageRows.map((i) => {
                const route = routes[i.shipmentId!];
                const h = hoursOpen(i);
                return (
                  <Tr key={i.id} className={cn(i.active && h > 24 && 'bg-red-50/40')}>
                    <Td primary className="min-w-52">
                      {route ? <RouteLine size="sm" origin={route.origin} destination={route.destination} /> : <span className="font-mono text-xs text-ink-700">Envío {i.shipmentId?.slice(0, 8)}</span>}
                    </Td>
                    {operator && <Td label="Partes" className="min-w-44 text-[13px]"><div><div className="font-medium">{i.requesterName}</div><div className="text-ink-500">{i.carrierName}</div></div></Td>}
                    <Td label="Categoría">{INCIDENT_CATEGORY_LABELS[i.category!]}</Td>
                    <Td label="Estado"><StatusPill status={i.status!} /></Td>
                    <Td label="Reportada" className="whitespace-nowrap text-[13px]"><div><div>{formatDateTime(i.createdAt)}</div><div className="text-xs text-ink-500">{formatRelative(i.createdAt)}</div></div></Td>
                    <Td label="Tiempo abierta" className={cn('whitespace-nowrap text-right font-mono text-[13px] tabular-nums', ageTone(h, i.active))}>
                      {formatOpenTime(h)}
                      {i.active && h > 12 && <span className="sr-only">{h > 24 ? ' (más de 24 horas)' : ' (más de 12 horas)'}</span>}
                    </Td>
                    <Td actions className="relative whitespace-nowrap text-right">
                      <div className="flex justify-end gap-2">
                        {operator && i.status === 'OPEN' && <Button variant="outline" size="sm" onClick={() => review(i)} loading={busyId === i.id}>Pasar a revisión</Button>}
                        <Button asChild variant={operator && i.active ? 'default' : 'outline'} size="sm"><a href={`/incidencias/${i.id}`}>Ver<span className="sr-only"> incidencia</span></a></Button>
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
        {visible && total > SIZE && (
          <div className="border-t border-steel-200 px-4 py-3 sm:px-5">
            <Pagination page={current} size={SIZE} total={total} onPageChange={setPage} />
          </div>
        )}
      </Card>
    </div>
  );
}
