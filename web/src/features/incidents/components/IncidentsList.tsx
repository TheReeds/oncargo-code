import { useCallback, useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { FilterBar } from '@/components/ui/filter-bar';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonRows } from '@/components/ui/skeleton';
import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { INCIDENT_CATEGORY_LABELS, formatDateTime, formatRelative } from '@/lib/format';
import { CATEGORY_OPTIONS, ageTone, formatOpenTime, hoursOpen } from '../lib';
import type { IncidentView, RouteMap } from '../server';

type Meta = { page: number; size: number; total: number };
const SIZE = 20;
const ALL = '__all';
const STATUS_OPTIONS = [{ value: ALL, label: 'Todos los estados' }, { value: 'OPEN', label: 'Abierta' }, { value: 'IN_REVIEW', label: 'En revisión' }, { value: 'RESOLVED', label: 'Resuelta' }, { value: 'CLOSED', label: 'Cerrada' }];

/**
 * Listado de incidencias. `variant="party"` (PYME/transportista): filtro Activas / Todas con paginación del servidor.
 * `variant="operator"`: bandeja global con filtros por estado y categoría, activas primero y más antiguas arriba.
 */
export default function IncidentsList({ variant, routes }: { variant: 'party' | 'operator'; routes: RouteMap }) {
  const operator = variant === 'operator';
  const [tab, setTab] = useState<'active' | 'all'>('active');
  const [status, setStatus] = useState(ALL);
  const [category, setCategory] = useState(ALL);
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<IncidentView[] | null>(null);
  const [meta, setMeta] = useState<Meta>({ page: 0, size: SIZE, total: 0 });
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    if (operator) {
      // La API no filtra por categoría ni ordena por antigüedad: se traen todas las páginas (tope 20 x 100) y se ordena aquí.
      const all: IncidentView[] = [];
      for (let p = 0; p < 20; p++) {
        const { data, error: err } = await actions.incidents.list({ status: status === ALL ? undefined : status, page: p, size: 100 });
        if (err || !data) { setError(err?.message ?? 'No pudimos cargar las incidencias.'); setRows([]); return; }
        all.push(...data.items);
        if (all.length >= data.meta.total || data.items.length === 0) break;
      }
      setRows(all);
      return;
    }
    const { data, error: err } = await actions.incidents.list({ active: tab === 'active' || undefined, page: page - 1, size: SIZE });
    if (err || !data) { setError(err?.message ?? 'No pudimos cargar las incidencias.'); setRows([]); return; }
    setRows(data.items);
    setMeta(data.meta);
  }, [operator, tab, status, page]);
  useEffect(() => { load(); }, [load]);

  const visible = useMemo(() => {
    if (!rows) return null;
    let r = rows;
    if (operator) {
      if (category !== ALL) r = r.filter((i) => i.category === category);
      // activas primero, y dentro de cada grupo las más antiguas arriba
      r = [...r].sort((a, b) => Number(!!b.active) - Number(!!a.active) || (a.active ? 1 : -1) * (new Date(a.createdAt!).getTime() - new Date(b.createdAt!).getTime()));
    }
    return r;
  }, [rows, operator, category]);

  async function review(i: IncidentView) {
    if (!(await confirm({ title: '¿Pasar la incidencia a revisión?', description: 'Las partes verán que LOGYX ya la está atendiendo.', confirmLabel: 'Pasar a revisión' }))) return;
    setBusyId(i.id!);
    const { error: err } = await actions.incidents.changeStatus({ id: i.id!, status: 'IN_REVIEW' });
    setBusyId(null);
    if (err) { toast.error(err.message); return; }
    toast.success('Incidencia en revisión');
    load();
  }

  const filtersActive = operator && (status !== ALL || category !== ALL);
  const pageRows = operator ? (visible ?? []).slice((page - 1) * SIZE, page * SIZE) : (visible ?? []);
  const total = operator ? (visible?.length ?? 0) : meta.total;

  return (
    <Card>
      {operator ? (
        <FilterBar count={visible?.length} noun={['incidencia', 'incidencias']} active={filtersActive} onClear={() => { setStatus(ALL); setCategory(ALL); setPage(1); }}>
          <Field label="Estado" htmlFor="f-status">
            <Select id="f-status" value={status} onValueChange={(v) => { setStatus(v); setPage(1); setRows(null); }} options={STATUS_OPTIONS} />
          </Field>
          <Field label="Categoría" htmlFor="f-category">
            <Select id="f-category" value={category} onValueChange={(v) => { setCategory(v); setPage(1); }} options={[{ value: ALL, label: 'Todas las categorías' }, ...CATEGORY_OPTIONS]} />
          </Field>
        </FilterBar>
      ) : (
        <div className="border-b border-steel-200 px-4 sm:px-5">
          <Tabs value={tab} onValueChange={(v) => { setTab(v as 'active' | 'all'); setPage(1); setRows(null); }}>
            <TabsList aria-label="Filtrar incidencias" className="border-b-0">
              <TabsTrigger value="active" aria-controls={undefined}>Activas</TabsTrigger>
              <TabsTrigger value="all" aria-controls={undefined}>Todas</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
      )}

      {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar las incidencias" action={<Button variant="outline" size="sm" onClick={load}>Reintentar</Button>}>{error}</Alert></div>}
      {visible === null ? <div className="p-4"><SkeletonRows rows={5} /></div> : visible.length === 0 && !error ? (
        <EmptyState
          title={operator ? (filtersActive ? 'No hay incidencias con esos filtros' : 'No hay incidencias') : tab === 'active' ? 'No tienes incidencias activas' : 'Todavía no tienes incidencias'}
          description={operator ? 'Cuando una PYME o un transportista reporte un problema, aparecerá aquí.' : 'Puedes reportar una desde la pestaña Incidencias de cada envío.'}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <Th>Envío</Th>{operator && <Th>Partes</Th>}<Th>Categoría</Th><Th>Estado</Th><Th>Reportada</Th><Th>Tiempo abierta</Th><Th><span className="sr-only">Acciones</span></Th>
            </tr>
          </thead>
          <tbody>
            {pageRows.map((i) => {
              const route = routes[i.shipmentId!];
              const h = hoursOpen(i);
              return (
                <Tr key={i.id}>
                  <Td className="min-w-52">
                    {route ? <RouteLine size="sm" origin={route.origin} destination={route.destination} /> : <span className="font-mono text-xs text-ink-700">Envío {i.shipmentId?.slice(0, 8)}</span>}
                  </Td>
                  {operator && <Td className="min-w-44 text-[13px]"><div className="font-medium">{i.requesterName}</div><div className="text-ink-500">{i.carrierName}</div></Td>}
                  <Td>{INCIDENT_CATEGORY_LABELS[i.category!]}</Td>
                  <Td><StatusPill status={i.status!} /></Td>
                  <Td className="whitespace-nowrap text-[13px]"><div>{formatDateTime(i.createdAt)}</div><div className="text-xs text-ink-500">{formatRelative(i.createdAt)}</div></Td>
                  <Td className={`whitespace-nowrap font-mono text-[13px] tabular-nums ${ageTone(h, i.active)}`}>
                    {formatOpenTime(h)}
                    {i.active && h > 12 && <span className="sr-only">{h > 24 ? ' (más de 24 horas)' : ' (más de 12 horas)'}</span>}
                  </Td>
                  <Td className="whitespace-nowrap text-right">
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
      {visible && visible.length > 0 && (
        <div className="border-t border-steel-200 px-4 py-3 sm:px-5">
          <Pagination page={page} size={SIZE} total={total} onPageChange={(p) => { setPage(p); if (!operator) setRows(null); }} />
        </div>
      )}
    </Card>
  );
}
