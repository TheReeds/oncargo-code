import { useCallback, useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { SearchX, ShieldAlert } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Select } from '@/components/ui/select';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonList } from '@/components/ui/skeleton';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toaster';
import { confirm } from '@/components/ui/confirm-dialog';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { useLiveRefresh } from '@/lib/realtime';
import { splitActionError } from '@/lib/action-error';
import { INCIDENT_CATEGORY_LABELS, formatDateTime } from '@/lib/format';
import { SEVERITY_OPTIONS } from '../lib';
import type { OperatorIncidentRow } from '../server';

const SIZE = 20;
type Scope = 'ALL' | 'MINE' | 'NONE' | 'BREACHED';
const SCOPES: { value: Scope; label: string }[] = [
  { value: 'ALL', label: 'Activas' }, { value: 'MINE', label: 'Mías' }, { value: 'NONE', label: 'Sin responsable' }, { value: 'BREACHED', label: 'Plazo vencido' },
];

/**
 * Bandeja de incidencias del operador (roadmap 2.7): activas por plazo (SLA por severidad), filtros por responsable y
 * severidad, y «Tomar» en la fila. Las que vencen antes van primero.
 */
export default function OperatorIncidentInbox() {
  const [scope, setScope] = useState<Scope>('ALL');
  const [severity, setSeverity] = useState('ALL');
  const [page, setPage] = useState(1);
  const [rows, setRows] = useState<OperatorIncidentRow[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const seq = useRef(0);

  const load = useCallback(async () => {
    const mine = ++seq.current;
    setError(null);
    const { data, error: err } = await actions.incidents.operatorInbox({
      assignee: scope === 'MINE' ? 'me' : scope === 'NONE' ? 'none' : undefined, breached: scope === 'BREACHED' || undefined,
      severity: severity === 'ALL' ? undefined : severity, order: 'due', page: page - 1, size: SIZE,
    });
    if (mine !== seq.current) return;
    if (err || !data) { setError(err?.message ?? 'No pudimos cargar las incidencias.'); setRows((r) => r ?? []); return; }
    setRows(data.items);
    setTotal(data.meta.total ?? data.items.length);
  }, [scope, severity, page]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], refresh: load, fallbackMs: 60_000 });
  // Reloj para el plazo: «Vencido» cambia con la hora, sin volver a pedir los datos.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), 60_000); return () => clearInterval(t); }, []);
  const breached = (r: OperatorIncidentRow) => !!r.dueAt && new Date(r.dueAt).getTime() < now;

  async function review(r: OperatorIncidentRow) {
    if (!(await confirm({ title: '¿Pasar la incidencia a revisión?', description: 'Las partes verán que OnCargo ya la está atendiendo.', confirmLabel: 'Pasar a revisión' }))) return;
    setBusy(r.id!);
    const { error: err } = await actions.incidents.changeStatus({ id: r.id!, status: 'IN_REVIEW' });
    setBusy(null);
    if (err) { toast.error(splitActionError(err).message); return; }
    toast.success('Incidencia en revisión');
    load();
  }

  async function take(r: OperatorIncidentRow) {
    setBusy(r.id!);
    const { error: err } = await actions.incidents.assign({ id: r.id! });
    setBusy(null);
    if (err) { toast.error(splitActionError(err).message); return; }
    toast.success('Incidencia asignada a ti');
    load();
  }

  const filtered = scope !== 'ALL' || severity !== 'ALL';
  return (
    <div className="min-w-0 space-y-3">
      <QuickFilters label="Filtrar incidencias" value={scope} onValueChange={(v) => { setScope(v as Scope); setPage(1); }} options={SCOPES} />
      <Card className="min-w-0 overflow-hidden">
        <FilterBar count={rows === null ? undefined : total} noun={['incidencia', 'incidencias']} active={filtered} onClear={() => { setScope('ALL'); setSeverity('ALL'); setPage(1); }}>
          <Field label="Severidad" htmlFor="op-inc-sev">
            <Select id="op-inc-sev" value={severity} onValueChange={(v) => { setSeverity(v); setPage(1); }} options={[{ value: 'ALL', label: 'Todas' }, ...SEVERITY_OPTIONS]} />
          </Field>
        </FilterBar>
        {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar las incidencias" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{error}</Alert></div>}
      {rows === null ? <div className="p-4"><SkeletonList rows={4} /></div> : rows.length === 0 ? (
        <EmptyState icon={filtered ? SearchX : ShieldAlert} title={filtered ? 'No hay incidencias con estos filtros' : 'No hay incidencias activas'} description="Las nuevas aparecen aquí apenas se reportan." compact action={filtered ? <Button variant="outline" size="sm" onClick={() => { setScope('ALL'); setSeverity('ALL'); setPage(1); }}>Limpiar filtros</Button> : undefined} />
      ) : (
        <Table label="Incidencias activas" stack>
          <thead>
            <tr><Th>Incidencia</Th><Th>Partes</Th><Th>Plazo</Th><Th>Responsable</Th><Th>Estado</Th><Th><span className="sr-only">Acciones</span></Th></tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <Tr key={r.id}>
                <Td primary>
                  <a className="font-medium text-petrol-700 hover:underline" href={`/incidencias/${r.id}`}>{INCIDENT_CATEGORY_LABELS[r.category!] ?? r.category}</a>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {r.severity && <StatusPill status={r.severity} kind="severity" />}
                    {r.blocking === false && <Badge tone="neutral">Informativa</Badge>}
                  </div>
                </Td>
                <Td label="Partes"><span className="block text-sm text-ink-900">{r.requesterName}</span><span className="block text-xs text-ink-500">{r.carrierName}</span></Td>
                <Td label="Plazo"><span className={breached(r) ? 'font-semibold text-red-700' : 'text-ink-700'}>{breached(r) ? 'Vencido' : 'Vence'} {formatDateTime(r.dueAt)}</span></Td>
                <Td label="Responsable">{r.assigneeName ?? <span className="text-ink-500">Sin responsable</span>}</Td>
                <Td label="Estado"><StatusPill status={r.status!} /></Td>
                <Td actions>
                  <div className="flex flex-wrap gap-2">
                    {r.status === 'OPEN' && <Button size="sm" variant="outline" disabled={busy === r.id} onClick={() => review(r)}>Pasar a revisión</Button>}
                    {!r.assigneeProfileId && <Button size="sm" variant="ghost" loading={busy === r.id} onClick={() => take(r)}>Tomar</Button>}
                  </div>
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
      {total > SIZE && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={SIZE} total={total} onPageChange={setPage} /></div>}
      </Card>
    </div>
  );
}
