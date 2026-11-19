import { useState } from 'react';
import { actions } from 'astro:actions';
import { Ban, Star, StarOff, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { Alert } from '@/components/ui/alert';
import { EmptyState } from '@/components/domain/EmptyState';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { TrustScore } from '@/components/domain/TrustScore';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Can } from '@/lib/permissions/react';
import { formatDate } from '@/lib/format';
import { MarkBadge } from './CarrierMarkMenu';
import type { CarrierPreferenceView } from '../server';

/** «Transportistas»: los que marcaste como preferidos o bloqueados. Se marcan desde las ofertas de cada solicitud. */
export default function CarrierPreferencesManager({ initial }: { initial: CarrierPreferenceView[] }) {
  const [items, setItems] = useState(initial);
  const [view, setView] = useState('PREFERRED');
  const [busy, setBusy] = useState<string | null>(null);

  async function reload() {
    const { data } = await actions.preferences.list({});
    if (data) setItems(data);
  }
  const preferred = items.filter((i) => i.kind === 'PREFERRED').length, blocked = items.length - preferred;
  const rows = items.filter((i) => i.kind === view);

  async function change(p: CarrierPreferenceView, kind: 'PREFERRED' | 'BLOCKED' | null) {
    if (kind === 'BLOCKED' && !(await confirm({
      title: `¿Bloquear a ${p.carrierName}?`,
      description: 'No verá tus solicitudes nuevas ni podrá ofertar en ellas, y no podrás aceptar sus ofertas pendientes. Sus envíos en curso no cambian.',
      confirmLabel: 'Bloquear transportista', tone: 'destructive',
    }))) return;
    setBusy(p.carrierOrgId!);
    const { error } = kind ? await actions.preferences.mark({ carrierOrgId: p.carrierOrgId!, kind }) : await actions.preferences.unmark({ carrierOrgId: p.carrierOrgId! });
    setBusy(null);
    if (error) return void toast.error('No pudimos guardar el cambio', error.message);
    toast.success(kind === 'PREFERRED' ? 'Marcado como preferido' : kind === 'BLOCKED' ? 'Transportista bloqueado' : 'Marca quitada');
    await reload();
  }

  if (items.length === 0)
    return (
      <Card>
        <EmptyState icon={Users} title="Aún no marcaste transportistas"
          description="Marca como preferidos a los que te dan buen servicio: se destacan en sus ofertas y les avisamos cuando publicas. Bloquea a los que no quieres volver a ver. Se marcan desde el botón «Marcar» de cada oferta."
          action={<Button asChild><a href="/pyme/solicitudes">Ver mis solicitudes</a></Button>} />
      </Card>
    );

  return (
    <div className="min-w-0 space-y-5">
      <ReadOnlyNotice>No puedes cambiar tus transportistas marcados.</ReadOnlyNotice>
      <KpiGrid cols={2}>
        <KpiTile label="Preferidos" value={preferred} tone="money" icon={Star} context="se destacan en sus ofertas y les avisamos" />
        <KpiTile label="Bloqueados" value={blocked} tone={blocked ? 'alert' : 'petrol'} icon={Ban} context="no ven tus solicitudes nuevas" />
      </KpiGrid>
      <QuickFilters label="Marca" value={view} onValueChange={setView}
        options={[{ value: 'PREFERRED', label: 'Preferidos', count: preferred }, { value: 'BLOCKED', label: 'Bloqueados', count: blocked }]} />
      <Card className="min-w-0 overflow-hidden">
        {rows.length === 0 ? (
          <EmptyState title={view === 'PREFERRED' ? 'No tienes transportistas preferidos' : 'No tienes transportistas bloqueados'} description="Puedes marcarlos desde las ofertas de una solicitud." />
        ) : (
          <Table label="Transportistas marcados" stack>
            <thead><tr><Th>Transportista</Th><Th>Trust Score</Th><Th>Desde</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {rows.map((p) => (
                <Tr key={p.carrierOrgId}>
                  <Td primary><div className="flex flex-wrap items-center gap-1.5 font-medium"><a href={`/organizaciones/${p.carrierOrgId}`} className="hover:text-petrol-700 hover:underline">{p.carrierName}</a><VerifiedBadge verified={p.carrierRucVerified} variant="icon" /><MarkBadge kind={p.kind} /></div></Td>
                  <Td label="Trust Score"><TrustScore score={p.carrierTrustScore} /></Td>
                  <Td label="Desde" className="whitespace-nowrap text-sm">{formatDate(p.updatedAt?.slice(0, 10))}</Td>
                  <Td actions className="text-right">
                    <Can fallback={null}>
                      {p.kind === 'PREFERRED'
                        ? <Button size="sm" variant="outline" loading={busy === p.carrierOrgId} onClick={() => void change(p, 'BLOCKED')}><Ban />Bloquear<span className="sr-only"> a {p.carrierName}</span></Button>
                        : <Button size="sm" variant="outline" loading={busy === p.carrierOrgId} onClick={() => void change(p, 'PREFERRED')}><Star />Marcar como preferido<span className="sr-only"> a {p.carrierName}</span></Button>}
                      <Button size="sm" variant="ghost" disabled={busy === p.carrierOrgId} onClick={() => void change(p, null)}><StarOff />Quitar marca<span className="sr-only"> de {p.carrierName}</span></Button>
                    </Can>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <Alert tone="info">La marca es privada: el transportista no se entera. Un bloqueado deja de ver tus solicitudes nuevas.</Alert>
    </div>
  );
}
