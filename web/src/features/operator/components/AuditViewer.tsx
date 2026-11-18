import { useMemo, useState } from 'react';
import { ExternalLink, ScrollText, SearchX, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Combobox, MultiSelect } from '@/components/ui/combobox';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Pagination } from '@/components/ui/pagination';
import { Sheet } from '@/components/ui/sheet';
import { Badge } from '@/components/ui/badge';
import { DescriptionList } from '@/components/ui/description-list';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { cn } from '@/lib/utils';
import { formatDateTime } from '@/lib/format';
import type { AuditRow } from '../server';
import { AUDIT_ENTITY_LABELS, auditActionLabel, auditEntityHref, auditEntityLabel } from '../audit-labels';

export type AuditFilters = { actions: string[]; entity: string; actor: string; org: string; entityId: string; from: string; to: string };
const ORG_TYPE: Record<string, string> = { PYME: 'PYME', CARRIER: 'Transportista', OPERATOR: 'Operador' };
const short = (id?: string) => (id ? id.slice(0, 8) : '');

/**
 * Visor de auditoría del operador (V15): tabla paginada en el servidor, filtros en la URL y detalle en un panel lateral.
 * El actor, la organización y la entidad se filtran desde el detalle de una fila (la API los pide por id).
 */
export default function AuditViewer({ rows, actionOptions, filters, page, size, total, loadError }: {
  rows: AuditRow[]; actionOptions: string[]; filters: AuditFilters; page: number; size: number; total: number; loadError?: string | null;
}) {
  const [f, setF] = useState(filters);
  const [navigating, setNavigating] = useState(false);
  const [detail, setDetail] = useState<AuditRow | null>(null);
  const hasFilters = !!(f.actions.length || f.entity || f.actor || f.org || f.entityId || f.from);
  const options = useMemo(() => actionOptions.map((a) => ({ value: a, label: auditActionLabel(a) })), [actionOptions]);
  const entityOptions = useMemo(() => Object.entries(AUDIT_ENTITY_LABELS).map(([value, label]) => ({ value, label })), []);

  function go(next: AuditFilters, p = 1) {
    setF(next);
    setNavigating(true);
    setDetail(null);
    const q = new URLSearchParams();
    if (next.actions.length) q.set('accion', next.actions.join(','));
    if (next.entity) q.set('entidad', next.entity);
    if (next.actor) q.set('actor', next.actor);
    if (next.org) q.set('organizacion', next.org);
    if (next.entityId) q.set('entidadId', next.entityId);
    if (next.from) { q.set('desde', next.from); if (next.to) q.set('hasta', next.to); }
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<AuditFilters>) => go({ ...f, ...patch });
  const clear = () => go({ actions: [], entity: '', actor: '', org: '', entityId: '', from: '', to: '' });

  // Nombre legible para los chips de actor/organización: se toma de una fila ya cargada.
  const actorName = rows.find((r) => r.actorProfileId === f.actor)?.actorName;
  const orgName = rows.find((r) => r.organizationId === f.org)?.organizationName;
  const chips = [
    f.actor && { key: 'actor', label: `Actor: ${actorName ?? short(f.actor)}`, clear: () => set({ actor: '' }) },
    f.org && { key: 'org', label: `Organización: ${orgName ?? short(f.org)}`, clear: () => set({ org: '' }) },
    f.entityId && { key: 'entityId', label: `Entidad: ${short(f.entityId)}`, clear: () => set({ entityId: '' }) },
  ].filter(Boolean) as { key: string; label: string; clear: () => void }[];

  return (
    <div className="min-w-0 space-y-3" aria-busy={navigating || undefined}>
      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar count={total} noun={['registro', 'registros']} active={hasFilters} onClear={clear}
          extra={chips.length > 0 ? (
            <ul className="flex flex-wrap items-center gap-2" aria-label="Filtros activos">
              {chips.map((c) => (
                <li key={c.key}><span className="inline-flex items-center gap-1 rounded-full bg-petrol-50 py-0.5 pl-2.5 pr-1 text-xs font-medium text-petrol-700">{c.label}<button type="button" onClick={c.clear} className="grid size-5 place-items-center rounded-full hover:bg-petrol-700/10" aria-label={`Quitar filtro «${c.label}»`}><X className="size-3" aria-hidden="true" /></button></span></li>
              ))}
            </ul>
          ) : undefined}>
          <Field label="Acción" htmlFor="au-action" className="sm:col-span-2 xl:col-span-2"><MultiSelect id="au-action" value={f.actions} onValueChange={(v) => set({ actions: v })} options={options} placeholder="Todas las acciones" searchPlaceholder="Buscar acción…" emptyText="No hay acciones con ese nombre" /></Field>
          <Field label="Entidad" htmlFor="au-entity"><Combobox id="au-entity" value={f.entity} onValueChange={(v) => set({ entity: v })} options={entityOptions} placeholder="Todas las entidades" searchPlaceholder="Buscar entidad…" allowClear /></Field>
          <Field label="Fechas" htmlFor="au-dates"><DateRangePicker id="au-dates" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
        </FilterBar>
        {loadError ? (
          <EmptyState icon={ScrollText} title="No pudimos cargar la auditoría" description={`${loadError} Recarga la página para intentarlo de nuevo.`} />
        ) : rows.length === 0 ? (
          hasFilters
            ? <EmptyState icon={SearchX} title="No hay registros con estos filtros" description="Prueba con otra acción o amplía el rango de fechas." action={<Button variant="outline" onClick={clear}><SearchX /> Limpiar filtros</Button>} />
            : <EmptyState icon={ScrollText} title="Aún no hay registros" description="Las acciones importantes de la plataforma quedarán anotadas aquí." />
        ) : (
          <Table label="Registros de auditoría" stack dense>
            <thead><tr><Th>Fecha</Th><Th>Acción</Th><Th>Actor</Th><Th>Organización</Th><Th>Entidad</Th><Th className="relative"><span className="sr-only">Detalle</span></Th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <Tr key={r.id} data-testid="audit-row">
                  <Td primary className="whitespace-nowrap"><span className="font-mono text-[13px] tabular-nums">{formatDateTime(r.occurredAt)}</span></Td>
                  <Td label="Acción"><div className="font-medium text-ink-900">{auditActionLabel(r.action)}</div><div className="font-mono text-[11px] text-ink-500">{r.action}</div></Td>
                  <Td label="Actor"><span className="block max-w-44 truncate">{r.actorName ?? '—'}</span><span className="block max-w-44 truncate text-xs text-ink-500">{r.actorEmail}</span></Td>
                  <Td label="Organización"><span className="block max-w-48 truncate">{r.organizationName ?? '—'}</span>{r.organizationType && <span className="text-xs text-ink-500">{ORG_TYPE[r.organizationType] ?? r.organizationType}</span>}</Td>
                  <Td label="Entidad">{auditEntityLabel(r.entity)}<div className="font-mono text-[11px] text-ink-500">{short(r.entityId)}</div></Td>
                  <Td actions className="text-right"><Button variant="outline" size="sm" onClick={() => setDetail(r)}>Ver detalle<span className="sr-only"> del registro {r.id}: {auditActionLabel(r.action)}</span></Button></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
        {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>

      <Sheet open={!!detail} onOpenChange={(o) => !o && setDetail(null)} title={detail ? auditActionLabel(detail.action) : 'Detalle'} description={detail ? `Registro #${detail.id} · ${formatDateTime(detail.occurredAt)}` : undefined}>
        {detail && <AuditDetail row={detail} onFilter={(patch) => set(patch)} />}
      </Sheet>
    </div>
  );
}

function AuditDetail({ row: r, onFilter }: { row: AuditRow; onFilter: (patch: Partial<AuditFilters>) => void }) {
  const href = auditEntityHref(r.entity, r.entityId);
  return (
    <div className="space-y-5 p-5" data-testid="audit-detail">
      <DescriptionList cols={1} items={[
        { label: 'Fecha y hora', value: formatDateTime(r.occurredAt), mono: true },
        { label: 'Acción', value: auditActionLabel(r.action), hint: <span className="font-mono">{r.action}</span> },
        { label: 'Actor', value: r.actorName ?? '—', hint: r.actorEmail },
        { label: 'Organización', value: r.organizationName ?? '—', hint: r.organizationType ? ORG_TYPE[r.organizationType] : undefined },
        { label: 'Entidad', value: auditEntityLabel(r.entity), hint: <span className="font-mono break-all">{r.entityId}</span> },
        { label: 'Detalle', value: r.detail ? <span className="whitespace-pre-wrap break-words">{r.detail}</span> : <span className="text-ink-500">Sin detalle</span> },
      ]} />
      <div className="space-y-2 border-t border-steel-200 pt-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Seguir la pista</p>
        <div className="flex flex-col gap-2">
          {r.actorProfileId && <Button variant="outline" onClick={() => onFilter({ actor: r.actorProfileId! })}>Ver más de este actor</Button>}
          {r.organizationId && <Button variant="outline" onClick={() => onFilter({ org: r.organizationId! })}>Ver más de esta organización</Button>}
          {r.entityId && <Button variant="outline" onClick={() => onFilter({ entity: r.entity ?? '', entityId: r.entityId! })}>Ver el historial de esta entidad</Button>}
          {href && <Button asChild variant="ghost"><a href={href}><ExternalLink aria-hidden="true" /> Abrir {auditEntityLabel(r.entity).toLowerCase()}</a></Button>}
          {r.organizationId && r.organizationType !== 'OPERATOR' && <Button asChild variant="ghost"><a href={`/organizaciones/${r.organizationId}`}><ExternalLink aria-hidden="true" /> Ver perfil de la organización</a></Button>}
        </div>
      </div>
      {r.entity && <Badge tone="neutral">{r.entity}</Badge>}
    </div>
  );
}
