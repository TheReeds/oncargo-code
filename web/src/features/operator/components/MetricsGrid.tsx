import { FileText, TriangleAlert, Truck, ShieldAlert, PackageCheck, Handshake, Building2, Warehouse } from 'lucide-react';
import { KpiTile } from '@/components/domain/KpiTile';
import { formatNumber } from '@/lib/format';
import type { OperatorMetrics } from '../server';

const plural = (n: number, one: string, many: string) => `${formatNumber(n)} ${n === 1 ? one : many}`;

/** KPIs globales de la operación: dos filas de cuatro. */
export function MetricsGrid({ m, slaHours }: { m: OperatorMetrics; slaHours: number }) {
  const n = (v?: number) => v ?? 0;
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <KpiTile label="Solicitudes abiertas" value={formatNumber(n(m.openRequests))} context={`${plural(n(m.matchedRequests), 'adjudicada', 'adjudicadas')} · ${plural(n(m.cancelledRequests), 'cancelada', 'canceladas')}`} icon={FileText} />
      <KpiTile label="En riesgo (SLA)" value={formatNumber(n(m.requestsAtRisk))} context={n(m.requestsAtRisk) ? (slaHours ? `Sin ofertas hace más de ${slaHours} h` : 'Sin ofertas · asígnalas ahora') : 'Ninguna solicitud en riesgo'} icon={TriangleAlert} href="#sin-ofertas" />
      <KpiTile label="Envíos en tránsito" value={formatNumber(n(m.shipmentsInTransit))} context={`${plural(n(m.shipmentsPending), 'por recoger', 'por recoger')}`} icon={Truck} />
      <KpiTile label="Incidencias activas" value={formatNumber(n(m.activeIncidents))} context={n(m.activeIncidents) ? 'Revisa y resuelve cada una' : 'Sin incidencias abiertas'} icon={ShieldAlert} href="/operador/incidencias" />
      <KpiTile label="Envíos entregados" value={formatNumber(n(m.shipmentsDelivered))} context="Acumulado de la plataforma" icon={PackageCheck} />
      <KpiTile label="Solicitudes adjudicadas" value={formatNumber(n(m.matchedRequests))} context="Con transportista y envío generado" icon={Handshake} />
      <KpiTile label="PYMEs registradas" value={formatNumber(n(m.pymes))} context="Cuentas que publican cargas" icon={Building2} />
      <KpiTile label="Transportistas registrados" value={formatNumber(n(m.carriers))} context="Cuentas que ofertan" icon={Warehouse} />
    </div>
  );
}
