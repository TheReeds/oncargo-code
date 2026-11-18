import { useState } from 'react';
import { actions } from 'astro:actions';
import { Building2, Check, Mail, Phone, SearchX, ShieldAlert, ShieldCheck, ShieldX } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { FilterBar } from '@/components/ui/filter-bar';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Pagination } from '@/components/ui/pagination';
import { Sheet } from '@/components/ui/sheet';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { DescriptionList } from '@/components/ui/description-list';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { flash } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS, formatDateTime, formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { OrganizationReviewRow } from '../server';

export type OrgFilters = { status: string; type: string };
type Decision = 'verify' | 'reject' | 'suspend';

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pendientes' },
  { value: 'REJECTED', label: 'Rechazadas' },
  { value: 'SUSPENDED', label: 'Suspendidas' },
  { value: 'VERIFIED', label: 'Verificadas' },
  { value: 'ALL', label: 'Todas' },
];
const TYPE_OPTIONS = [{ value: 'ALL', label: 'Todos los tipos' }, { value: 'PYME', label: 'PYME' }, { value: 'CARRIER', label: 'Transportista' }];
const TYPE_LABEL: Record<string, string> = { PYME: 'PYME', CARRIER: 'Transportista' };

const DECISION: Record<Decision, { title: (o: OrganizationReviewRow) => string; description: string; confirm: string; required: boolean; max: number; label: string; hint?: string; placeholder: string; tone: 'default' | 'destructive'; done: string }> = {
  verify: {
    title: (o) => `Verificar ${o.legalName}`,
    description: 'La empresa recibe el sello "Verificado por OnCargo" y podrá publicar, ofertar y reservar. Se le avisará de inmediato.',
    confirm: 'Verificar empresa', required: false, max: LIMITS.organizationNote, label: 'Nota para la empresa', hint: 'Opcional. La empresa la verá en su aviso.', placeholder: 'Ej. RUC y razón social validados en SUNAT.', tone: 'default', done: 'Empresa verificada',
  },
  reject: {
    title: (o) => `Rechazar la verificación de ${o.legalName}`,
    description: 'La empresa seguirá sin poder publicar ni ofertar. Verá el motivo y podrá corregir sus datos y volver a pedir la revisión.',
    confirm: 'Rechazar verificación', required: true, max: LIMITS.organizationReason, label: 'Motivo', hint: 'La empresa lo verá. Sé específico para que pueda corregirlo.', placeholder: 'Ej. La razón social no coincide con el RUC.', tone: 'destructive', done: 'Verificación rechazada',
  },
  suspend: {
    title: (o) => `Suspender a ${o.legalName}`,
    description: 'No podrá publicar, ofertar, reservar ni aceptar ofertas hasta que la reactives. Se le avisará con el motivo.',
    confirm: 'Suspender empresa', required: true, max: LIMITS.organizationReason, label: 'Motivo', hint: 'La empresa lo verá.', placeholder: 'Ej. Reportes reiterados de incumplimiento.', tone: 'destructive', done: 'Empresa suspendida',
  },
};

/** Bandeja de verificación de organizaciones (RN01, F05): tabla paginada en el servidor, filtros en la URL y decisiones con motivo. */
export default function OrganizationsInbox({ rows, filters, page, size, total, counts }: {
  rows: OrganizationReviewRow[]; filters: OrgFilters; page: number; size: number; total: number; counts: Record<string, number>;
}) {
  const [f, setF] = useState(filters);
  const [navigating, setNavigating] = useState(false);
  const [detail, setDetail] = useState<OrganizationReviewRow | null>(null);
  const [decision, setDecision] = useState<Decision | null>(null);
  const hasFilters = f.status !== 'PENDING' || !!f.type;

  function go(next: OrgFilters, p = 1) {
    setF(next);
    setNavigating(true);
    const q = new URLSearchParams();
    if (next.status && next.status !== 'PENDING') q.set('estado', next.status);
    if (next.type) q.set('tipo', next.type);
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<OrgFilters>) => go({ ...f, ...patch });

  async function decide(reason: string) {
    if (!detail || !decision) return null;
    const id = detail.id!;
    const { error } = decision === 'verify'
      ? await actions.operator.verifyOrganization({ id, note: reason || undefined })
      : decision === 'reject'
        ? await actions.operator.rejectOrganization({ id, reason })
        : await actions.operator.suspendOrganization({ id, reason });
    if (error) return splitActionError(error);
    flash(DECISION[decision].done, 'success');
    window.location.reload();
    return null;
  }

  const emptyTitle = f.status === 'PENDING' ? 'No hay empresas por verificar' : 'No hay empresas con estos filtros';
  return (
    <div className="min-w-0 space-y-4" aria-busy={navigating || undefined}>
      <KpiGrid>
        <KpiTile label="Por verificar" value={counts.PENDING ?? 0} context={counts.PENDING ? 'esperan una decisión' : 'bandeja al día'} icon={ShieldAlert} tone={counts.PENDING ? 'warn' : 'default'} href="?estado=PENDING" />
        <KpiTile label="Rechazadas" value={counts.REJECTED ?? 0} context="pueden corregir y volver a pedirla" icon={ShieldX} tone={counts.REJECTED ? 'alert' : 'default'} href="?estado=REJECTED" />
        <KpiTile label="Suspendidas" value={counts.SUSPENDED ?? 0} context="no pueden asumir compromisos" icon={ShieldX} tone={counts.SUSPENDED ? 'alert' : 'default'} href="?estado=SUSPENDED" />
        <KpiTile label="Verificadas" value={counts.VERIFIED ?? 0} context="con sello de OnCargo" icon={ShieldCheck} tone="money" href="?estado=VERIFIED" />
      </KpiGrid>

      <QuickFilters label="Estado de verificación" value={f.status} onValueChange={(v) => set({ status: v })} options={STATUS_OPTIONS.map((o) => ({ ...o, count: o.value === 'ALL' ? Object.values(counts).reduce((a, b) => a + b, 0) : counts[o.value] }))} />

      <Card className={cn('min-w-0 overflow-hidden transition-opacity', navigating && 'pointer-events-none opacity-60')}>
        <FilterBar count={total} noun={['empresa', 'empresas']} active={hasFilters} onClear={() => go({ status: 'PENDING', type: '' })}>
          <Field label="Tipo de empresa" htmlFor="o-type"><Select id="o-type" value={f.type || 'ALL'} onValueChange={(v) => set({ type: v === 'ALL' ? '' : v })} options={TYPE_OPTIONS} /></Field>
        </FilterBar>
        {rows.length === 0 ? (
          hasFilters || f.status !== 'PENDING'
            ? <EmptyState icon={SearchX} title={emptyTitle} description="Prueba con otro estado o tipo de empresa." action={<Button variant="outline" onClick={() => go({ status: 'PENDING', type: '' })}><SearchX /> Ver pendientes</Button>} />
            : <EmptyState icon={Check} title={emptyTitle} description="Cuando una empresa se registre y espere la revisión, aparecerá aquí." />
        ) : (
          <Table label="Empresas por verificar" stack dense>
            <thead><tr><Th>Empresa</Th><Th>Tipo</Th><Th>Ciudad</Th><Th>Registro</Th><Th>Estado</Th><Th className="relative"><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {rows.map((o) => (
                <Tr key={o.id} data-testid="org-row">
                  <Td primary>
                    <button type="button" onClick={() => setDetail(o)} className="max-w-64 truncate text-left font-medium text-petrol-700 hover:underline">{o.legalName}</button>
                    <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-ink-500"><span className="font-mono">RUC {o.ruc}</span>{o.rucValid ? <span className="text-emerald-700">Dígito verificador correcto</span> : <span className="font-medium text-red-700">Dígito verificador inválido</span>}</div>
                  </Td>
                  <Td label="Tipo"><Badge tone={o.type === 'CARRIER' ? 'petrol' : 'neutral'}>{TYPE_LABEL[o.type ?? ''] ?? o.type}</Badge></Td>
                  <Td label="Ciudad">{CITY_LABELS[(o.city ?? '').toUpperCase()] ?? o.city ?? '—'}</Td>
                  <Td label="Registro" className="whitespace-nowrap">{formatRelative(o.createdAt)}</Td>
                  <Td label="Estado"><div className="flex flex-col items-start gap-1"><StatusPill kind="verification" status={o.verificationStatus ?? 'PENDING'} />{o.verificationNote && <span className="max-w-56 truncate text-xs text-ink-500" title={o.verificationNote}>{o.verificationNote}</span>}</div></Td>
                  <Td actions className="relative text-right"><Button variant="outline" size="sm" onClick={() => setDetail(o)}>Revisar<span className="sr-only"> a {o.legalName}</span></Button></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
        {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>

      <Sheet open={!!detail && !decision} onOpenChange={(o) => !o && setDetail(null)} title={detail?.legalName ?? 'Empresa'} description="Datos de registro y decisión de verificación">
        {detail && (
          <div className="space-y-5 p-5">
            <div className="flex flex-wrap items-center gap-2">
              <StatusPill kind="verification" status={detail.verificationStatus ?? 'PENDING'} />
              <Badge tone={detail.type === 'CARRIER' ? 'petrol' : 'neutral'}>{TYPE_LABEL[detail.type ?? ''] ?? detail.type}</Badge>
              <Badge tone={detail.rucValid ? 'emerald' : 'red'}>{detail.rucValid ? 'RUC con dígito correcto' : 'RUC con dígito inválido'}</Badge>
            </div>
            <DescriptionList cols={1} items={[
              { label: 'Razón social', value: detail.legalName },
              { label: 'RUC', value: detail.ruc, mono: true },
              { label: 'Ciudad', value: CITY_LABELS[(detail.city ?? '').toUpperCase()] ?? detail.city },
              { label: 'Teléfono', value: detail.phone ? <span className="inline-flex items-center gap-1.5"><Phone className="size-3.5 text-ink-500" aria-hidden="true" />{detail.phone}</span> : undefined },
              { label: 'Registrada', value: formatDateTime(detail.createdAt) },
              { label: detail.verificationStatus === 'PENDING' ? 'Nota' : 'Última decisión', value: detail.verificationUpdatedAt ? formatDateTime(detail.verificationUpdatedAt) : undefined, hint: detail.verificationNote },
            ]} />
            <p className="rounded-input bg-steel-100 px-3 py-2.5 text-xs text-ink-700"><Building2 className="mr-1 inline size-3.5 align-text-bottom" aria-hidden="true" />El dígito verificador solo confirma el formato del RUC. Antes de verificar, contrasta la razón social y el RUC con la ficha de SUNAT.</p>
            <div className="flex flex-col gap-2 border-t border-steel-200 pt-4">
              {detail.verificationStatus !== 'VERIFIED' && <Button variant="money" onClick={() => setDecision('verify')}><ShieldCheck />{detail.verificationStatus === 'PENDING' ? 'Verificar empresa' : 'Reactivar y verificar'}</Button>}
              {detail.verificationStatus === 'PENDING' && <Button variant="destructive" onClick={() => setDecision('reject')}><ShieldX />Rechazar verificación</Button>}
              {detail.verificationStatus !== 'SUSPENDED' && <Button variant="destructive" onClick={() => setDecision('suspend')}><ShieldAlert />Suspender empresa</Button>}
              <p className="flex items-center gap-1.5 text-xs text-ink-500"><Mail className="size-3.5" aria-hidden="true" />La empresa recibe un aviso con tu nota o motivo.</p>
            </div>
          </div>
        )}
      </Sheet>
      {detail && decision && (() => {
        const d = DECISION[decision];
        return (
          <ReasonDialog
            open onOpenChange={(o) => !o && setDecision(null)} title={d.title(detail)} description={d.description} label={d.label} hint={d.hint}
            placeholder={d.placeholder} required={d.required} maxLength={d.max} confirmLabel={d.confirm} tone={d.tone} onSubmit={decide}
          />
        );
      })()}
    </div>
  );
}
