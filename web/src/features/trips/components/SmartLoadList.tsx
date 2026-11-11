import { useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Check, Sparkles, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { Countdown } from '@/components/domain/Countdown';
import { StatusPill } from '@/components/domain/StatusPill';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { EmptyState } from '@/components/domain/EmptyState';
import { CITY_LABELS, formatDate, formatMoney, formatNumber, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { SmartLoadView } from '../server';

type Tab = 'PENDING' | 'ACCEPTED' | 'CLOSED';
const TABS: { id: Tab; label: string }[] = [{ id: 'PENDING', label: 'Pendientes' }, { id: 'ACCEPTED', label: 'Aceptadas' }, { id: 'CLOSED', label: 'Rechazadas y vencidas' }];
const tabOf = (s?: string): Tab => (s === 'PENDING' ? 'PENDING' : s === 'ACCEPTED' ? 'ACCEPTED' : 'CLOSED');

export default function SmartLoadList({ items }: { items: SmartLoadView[] }) {
  const [tab, setTab] = useState<Tab>('PENDING');
  const [busy, setBusy] = useState<string | null>(null);
  const shown = items.filter((s) => tabOf(s.status) === tab);
  const count = (t: Tab) => items.filter((s) => tabOf(s.status) === t).length;

  async function generate() {
    setBusy('generate');
    const { data, error } = await actions.trips.generateSmartLoads();
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    const n = (data as unknown[] | undefined)?.length ?? 0;
    if (n === 0) { toast.info('No encontramos combinaciones nuevas', 'Vuelve a intentarlo cuando se publiquen más cargas.'); setBusy(null); return; }
    flash(n === 1 ? '1 sugerencia nueva' : `${n} sugerencias nuevas`, 'success');
    window.location.reload();
  }
  async function accept(s: SmartLoadView) {
    const ok = await confirm({ title: '¿Aceptar esta combinación?', description: `Avisaremos a las ${s.items?.length ?? 0} empresas de que planeas llevar sus cargas juntas. Esto no te adjudica nada: sigues ofertando en cada subasta.`, confirmLabel: 'Aceptar sugerencia' });
    if (!ok) return;
    setBusy(`a-${s.id}`);
    const { error } = await actions.trips.acceptSmartLoad({ id: s.id! });
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash('Sugerencia aceptada', 'success');
    window.location.reload();
  }
  async function reject(s: SmartLoadView) {
    const ok = await confirm({ title: '¿Rechazar esta sugerencia?', description: 'Dejará de aparecer en tu lista. No se puede deshacer.', confirmLabel: 'Rechazar sugerencia', tone: 'destructive' });
    if (!ok) return;
    setBusy(`r-${s.id}`);
    const { error } = await actions.trips.rejectSmartLoad({ id: s.id! });
    if (error) { toast.error(splitActionError(error).message); setBusy(null); return; }
    flash('Sugerencia rechazada');
    window.location.reload();
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
          <TabsList aria-label="Estado de las sugerencias">
            {TABS.map((t) => <TabsTrigger key={t.id} value={t.id} aria-controls={undefined}>{t.label} <span className="ml-1 font-mono text-xs">{count(t.id)}</span></TabsTrigger>)}
          </TabsList>
        </Tabs>
        <Button variant="outline" loading={busy === 'generate'} disabled={!!busy} onClick={generate}><Sparkles />{busy === 'generate' ? 'Generando…' : 'Generar sugerencias'}</Button>
      </div>

      {shown.length === 0 ? (
        <Card>
          <EmptyState
            title={tab === 'PENDING' ? 'No tienes sugerencias pendientes' : tab === 'ACCEPTED' ? 'Aún no aceptaste ninguna sugerencia' : 'Nada por aquí'}
            description={tab === 'PENDING' ? 'Combinamos cargas compatibles con tu flota para llenar un vehículo. Genera nuevas sugerencias cuando quieras.' : 'Las sugerencias que aceptes aparecerán en esta pestaña.'}
          />
        </Card>
      ) : (
        <ul className="grid gap-4 xl:grid-cols-2" aria-label="Sugerencias Smart Load">
          {shown.map((s) => {
            const pending = s.status === 'PENDING';
            const route = s.route ?? [];
            return (
              <li key={s.id}>
                <Card className="flex h-full flex-col gap-4 p-5" data-testid="smart-load">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2"><span className="font-mono text-base font-semibold">{s.vehiclePlate}</span><span className="text-sm text-ink-700">{VEHICLE_LABELS[s.vehicleType!] ?? s.vehicleType}</span><StatusPill kind="smart" status={s.status!} /></div>
                      <div className="mt-0.5 text-xs text-ink-500">{s.items?.length ?? 0} cargas combinadas · <span className="font-mono">{formatTons(s.totalWeightKg)}</span></div>
                    </div>
                    <div className="text-right"><div className="text-xs text-ink-500">Ingreso combinado</div><div className="money text-xl">{formatMoney(s.combinedIncome)}</div></div>
                  </div>

                  <div>
                    <div className="mb-1 flex items-baseline justify-between text-xs text-ink-500"><span>Ocupación del vehículo</span><span className="font-mono font-semibold text-ink-900">{formatNumber(s.occupancyPct, 1)} %</span></div>
                    <CapacityBar used={s.totalWeightKg ?? 0} total={s.capacityKg ?? 0} label="Ocupación del vehículo con esta combinación" />
                  </div>

                  <div>
                    <h3 className="mb-1.5 text-[13px] font-medium text-ink-900">Cargas</h3>
                    <ul className="divide-y divide-steel-200 rounded-input border border-steel-200">
                      {(s.items ?? []).map((it) => (
                        <li key={it.requestId} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                          <div className="min-w-0"><span className="font-medium">{CITY_LABELS[it.originCity!]} → {CITY_LABELS[it.destinationCity!]}</span><div className="text-xs text-ink-500"><span className="font-mono">{formatTons(it.weightKg)}</span> · recojo {formatDate(it.pickupDate)}</div></div>
                          <span className="money shrink-0">{formatMoney(it.income)}</span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  {route.length > 0 && (
                    <div>
                      <h3 className="mb-1.5 text-[13px] font-medium text-ink-900">Ruta sugerida</h3>
                      <ol className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-ink-700">
                        {route.map((p, i) => (
                          <li key={i} className="flex items-center gap-1.5">
                            {i > 0 && <span aria-hidden="true" className="text-ink-400">→</span>}
                            <span className={`rounded-full px-2 py-0.5 ${p.type === 'PICKUP' ? 'bg-petrol-50 text-petrol-700' : 'bg-emerald-50 text-emerald-700'}`}>{p.type === 'PICKUP' ? 'Recojo' : 'Entrega'} · {CITY_LABELS[p.city!] ?? p.city}</span>
                          </li>
                        ))}
                      </ol>
                    </div>
                  )}

                  <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-steel-200 pt-4">
                    {pending ? <span className="flex items-center gap-1.5 text-xs text-ink-500">Vence en <Countdown closesAt={s.expiresAt} /></span> : <span className="text-xs text-ink-500">Vigente hasta {s.expiresAt ? new Date(s.expiresAt).toLocaleDateString('es-PE', { timeZone: 'America/Lima', day: 'numeric', month: 'short' }) : '—'}</span>}
                    {pending && (
                      <div className="flex gap-2">
                        <Button variant="destructive" size="sm" disabled={!!busy} loading={busy === `r-${s.id}`} onClick={() => reject(s)}><X />Rechazar sugerencia<span className="sr-only"> del vehículo {s.vehiclePlate}</span></Button>
                        <Button variant="money" size="sm" disabled={!!busy} loading={busy === `a-${s.id}`} onClick={() => accept(s)}><Check />Aceptar sugerencia<span className="sr-only"> del vehículo {s.vehiclePlate}</span></Button>
                      </div>
                    )}
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
      {tab === 'ACCEPTED' && shown.length > 0 && <Alert tone="info" title="Aceptar no es adjudicar">Para llevar estas cargas, ofértalas en <a className="font-medium underline" href="/transportista/marketplace">Cargas disponibles</a>. La PYME elige entre todas las ofertas.</Alert>}
    </div>
  );
}
