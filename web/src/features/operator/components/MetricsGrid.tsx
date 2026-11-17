import { FileText, TriangleAlert, Truck, ShieldAlert, PackageCheck, Clock, Building2, Warehouse } from 'lucide-react';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { formatNumber } from '@/lib/format';
import type { OperatorMetrics } from '../server';

const plural = (n: number, one: string, many: string) => `${formatNumber(n)} ${n === 1 ? one : many}`;

/** KPIs de la operación en dos grupos: lo que pide acción ahora (con tono) y el tamaño de la plataforma. */
export function MetricsGrid({ m, slaHours, expiring }: { m: OperatorMetrics; slaHours: number; expiring: number }) {
  const n = (v?: number) => v ?? 0;
  const risk = n(m.requestsAtRisk);
  const incidents = n(m.activeIncidents);
  return (
    <div className="space-y-5">
      <section aria-labelledby="op-k-action">
        <h2 id="op-k-action" className="mb-2 text-[13px] font-semibold uppercase tracking-wide text-ink-500">Requiere acción</h2>
        <KpiGrid>
          <KpiTile label="En riesgo (SLA)" value={formatNumber(risk)} tone={risk ? 'alert' : 'default'} icon={TriangleAlert} href="#sin-ofertas"
            context={risk ? (slaHours ? `Sin ofertas hace más de ${slaHours} h` : 'Sin ofertas · asígnalas ahora') : 'Ninguna solicitud en riesgo'} />
          <KpiTile label="Incidencias activas" value={formatNumber(incidents)} tone={incidents ? 'alert' : 'default'} icon={ShieldAlert} href="/operador/incidencias"
            context={incidents ? 'Congelan el avance del envío' : 'Sin incidencias abiertas'} />
          <KpiTile label="Subastas por vencer" value={formatNumber(expiring)} tone={expiring ? 'warn' : 'default'} icon={Clock} href="#por-vencer"
            context={expiring ? 'Cierran en minutos' : 'Ninguna cierra pronto'} />
          <KpiTile label="Envíos en tránsito" value={formatNumber(n(m.shipmentsInTransit))} tone="petrol" icon={Truck} href="/operador/envios?estado=IN_TRANSIT"
            context={plural(n(m.shipmentsPending), 'por recoger', 'por recoger')} />
        </KpiGrid>
      </section>
      <section aria-labelledby="op-k-platform">
        <h2 id="op-k-platform" className="mb-2 text-[13px] font-semibold uppercase tracking-wide text-ink-500">Plataforma</h2>
        <KpiGrid>
          <KpiTile label="Solicitudes abiertas" value={formatNumber(n(m.openRequests))} icon={FileText} href="/operador/solicitudes"
            context={`${plural(n(m.matchedRequests), 'adjudicada', 'adjudicadas')} · ${plural(n(m.cancelledRequests), 'cancelada', 'canceladas')}`} />
          <KpiTile label="Envíos entregados" value={formatNumber(n(m.shipmentsDelivered))} tone="money" icon={PackageCheck} href="/operador/envios?estado=DELIVERED" context="Acumulado de la plataforma" />
          <KpiTile label="PYMEs registradas" value={formatNumber(n(m.pymes))} icon={Building2} context="Cuentas que publican cargas" />
          <KpiTile label="Transportistas registrados" value={formatNumber(n(m.carriers))} icon={Warehouse} context="Cuentas que ofertan" />
        </KpiGrid>
      </section>
    </div>
  );
}
