import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type OperatorMetrics = Schemas['OperatorMetricsView'];
export type SlaAlerts = Schemas['OperatorAlertsView'];
export type SlaRequest = Schemas['SlaRequestView'];
export type IncidentAlert = Schemas['IncidentAlertView'];
export type Candidate = Schemas['AssignmentCandidateView'];
export type AssignmentResult = Schemas['ManualAssignmentResult'];

export const getMetrics = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/metrics'));
export const getSlaAlerts = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/sla-alerts'));
export const getCandidates = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/operator/requests/{id}/candidates', { params: { path: { id } } }));
export const assignRequest = (token: string, id: string, body: Schemas['ManualAssignment']) =>
  unwrap(apiFor(token).POST('/api/v1/operator/requests/{id}/assign', { params: { path: { id } }, body }));

/** Lectura de solo consulta (el operador puede leer cualquier solicitud / envío). */
export const getRequest = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipment-requests/{id}', { params: { path: { id } } }));
export const getShipment = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipments/{id}', { params: { path: { id } } }));

/** Métricas y alertas en una sola lectura (carga inicial y polling). */
export async function getOverview(token: string) {
  const [metrics, alerts] = await Promise.all([getMetrics(token), getSlaAlerts(token)]);
  return { metrics, alerts };
}
