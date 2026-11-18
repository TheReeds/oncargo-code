import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';
import { unwrapPage } from '@/lib/api/page';

export type OperatorMetrics = Schemas['OperatorMetricsView'];
export type SlaAlerts = Schemas['OperatorAlertsView'];
export type SlaRequest = Schemas['SlaRequestView'];
export type IncidentAlert = Schemas['IncidentAlertView'];
export type Candidate = Schemas['AssignmentCandidateView'];
export type OperatorRequestRow = Schemas['OperatorRequestRow'];
export type OperatorShipmentRow = Schemas['OperatorShipmentRow'];
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

/** Filtros de los listados globales; `page` base 0 (ver `lib/api/page.ts`). */
export type OperatorListQuery = { status?: string; originCity?: string; destinationCity?: string; fromDate?: string; toDate?: string; page?: number; size?: number };
export const listRequests = (token: string, query: OperatorListQuery = {}) =>
  unwrapPage<OperatorRequestRow>(apiFor(token).GET('/api/v1/operator/requests', { params: { query } }) as never);
export const listShipments = (token: string, query: OperatorListQuery = {}) =>
  unwrapPage<OperatorShipmentRow>(apiFor(token).GET('/api/v1/operator/shipments', { params: { query } }) as never);

// ── Verificación de organizaciones (RN01, F05) ───────────────────────
export type OrganizationReviewRow = Schemas['OrganizationReviewRow'];
export type OrgReviewQuery = { status?: string; type?: string; page?: number; size?: number };
export const listOrganizations = (token: string, query: OrgReviewQuery = {}) =>
  unwrapPage<OrganizationReviewRow>(apiFor(token).GET('/api/v1/operator/organizations', { params: { query } }) as never);
export const verifyOrganization = (token: string, id: string, note?: string) =>
  unwrap(apiFor(token).POST('/api/v1/operator/organizations/{id}/verify', { params: { path: { id } }, body: note ? { note } : {} }));
export const rejectOrganization = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/operator/organizations/{id}/reject', { params: { path: { id } }, body: { reason } }));
export const suspendOrganization = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/operator/organizations/{id}/suspend', { params: { path: { id } }, body: { reason } }));

// ── Auditoría (V15, F45) ─────────────────────────────────────────────
export type AuditRow = Schemas['AuditRow'];
export type AuditQuery = { actorId?: string; organizationId?: string; action?: string[]; entity?: string; entityId?: string; from?: string; to?: string; page?: number; size?: number };
export const listAudit = (token: string, query: AuditQuery = {}) =>
  unwrapPage<AuditRow>(apiFor(token).GET('/api/v1/operator/audit', { params: { query } }) as never);
export const auditActions = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/audit/actions'));
