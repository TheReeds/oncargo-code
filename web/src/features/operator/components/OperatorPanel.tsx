import { useCallback, useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { RefreshCw } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { formatMoney, formatRelative } from '@/lib/format';
import { useLiveRefresh } from '@/lib/realtime';
import { MetricsGrid } from './MetricsGrid';
import { SlaAlerts } from './SlaAlerts';
import AssignSheet from './AssignSheet';
import type { AssignmentResult, OperatorMetrics, SlaAlerts as Alerts, SlaRequest } from '../server';

const POLL_MS = 60_000; // SLA depende del reloj (no hay evento): se refresca aunque haya stream, más lento

/** Panel de operación: métricas + alertas SLA. Se refresca con `incident.*` en vivo y cada minuto (el SLA depende del reloj). */
export default function OperatorPanel({ metrics: m0, alerts: a0, loadedAt }: { metrics: OperatorMetrics; alerts: Alerts; loadedAt: number }) {
  const [metrics, setMetrics] = useState(m0);
  const [alerts, setAlerts] = useState(a0);
  const [updatedAt, setUpdatedAt] = useState(loadedAt);
  const [now, setNow] = useState(loadedAt);
  const [refreshing, setRefreshing] = useState(false);
  const [stale, setStale] = useState(false);
  const [target, setTarget] = useState<SlaRequest | null>(null);
  const [done, setDone] = useState<AssignmentResult | null>(null);
  const busy = useRef(false);

  const refresh = useCallback(async () => {
    if (busy.current) return;
    busy.current = true;
    setRefreshing(true);
    const { data, error } = await actions.operator.overview({});
    busy.current = false;
    setRefreshing(false);
    if (error || !data) { setStale(true); return; }
    setStale(false);
    setMetrics(data.metrics);
    setAlerts(data.alerts);
    setUpdatedAt(Date.now());
  }, []);

  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], refresh, fallbackMs: 30_000 });

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 15_000);
    const poll = setInterval(() => { if (!document.hidden) refresh(); }, POLL_MS);
    const onVisible = () => { if (!document.hidden && Date.now() - updatedAt > POLL_MS) refresh(); setNow(Date.now()); };
    document.addEventListener('visibilitychange', onVisible);
    return () => { clearInterval(tick); clearInterval(poll); document.removeEventListener('visibilitychange', onVisible); };
  }, [refresh, updatedAt]);

  function assigned(r: AssignmentResult, requestId: string) {
    setTarget(null);
    setDone(r);
    setAlerts((a) => ({ ...a, requestsWithoutBids: (a.requestsWithoutBids ?? []).filter((x) => x.id !== requestId) }));
    refresh();
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-end gap-2 text-xs text-ink-500" role="status">
        <span>{stale ? 'No pudimos actualizar. Reintentaremos en unos segundos.' : `Actualizado ${formatRelative(new Date(updatedAt).toISOString(), now)}`}</span>
        <Button size="sm" variant="outline" onClick={refresh} loading={refreshing}><RefreshCw aria-hidden="true" /> Actualizar</Button>
      </div>

      {done && (
        <Alert tone="success" title="Solicitud asignada" action={<div className="flex gap-2"><Button size="sm" variant="outline" asChild><a href={`/operador/envios/${done.shipmentId}`}>Ver envío</a></Button><Button size="sm" variant="ghost" onClick={() => setDone(null)}>Cerrar</Button></div>}>
          {done.carrierName} llevará la carga con el vehículo <span className="font-mono">{done.vehiclePlate}</span> por <span className="font-mono">{formatMoney(done.amount)}</span>. Se generó el envío y se notificó al transportista y a la PYME.
        </Alert>
      )}

      <MetricsGrid m={metrics} slaHours={alerts.slaHours ?? 24} />
      <SlaAlerts alerts={alerts} onAssign={setTarget} />
      <AssignSheet request={target} onClose={() => setTarget(null)} onAssigned={assigned} />
    </div>
  );
}
