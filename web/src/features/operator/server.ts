import { apiFor } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';
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
export type OperatorListQuery = { status?: string; originCity?: string; destinationCity?: string; fromDate?: string; toDate?: string; q?: string; page?: number; size?: number };
export const listRequests = (token: string, query: OperatorListQuery = {}) =>
  unwrapPage<OperatorRequestRow>(apiFor(token).GET('/api/v1/operator/requests', { params: { query } }) as never);
export const listShipments = (token: string, query: OperatorListQuery = {}) =>
  unwrapPage<OperatorShipmentRow>(apiFor(token).GET('/api/v1/operator/shipments', { params: { query } }) as never);

// ── Verificación de organizaciones (RN01, F05) ───────────────────────
export type OrganizationReviewRow = Schemas['OrganizationReviewRow'];
export type OrgReviewQuery = { status?: string; type?: string; q?: string; page?: number; size?: number };
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

// ── Parámetros de sistema (V18) ──────────────────────────────────────
export type ParameterRow = Schemas['ParameterRow'];
export type ParameterChangeRow = Schemas['ParameterChangeRow'];
export const listParameters = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/parameters'));
export const updateParameter = (token: string, key: string, body: Schemas['UpdateParameter']) =>
  unwrap(apiFor(token).PUT('/api/v1/operator/parameters/{key}', { params: { path: { key } }, body }));
export const parameterHistory = (token: string, query: { key?: string; page?: number; size?: number } = {}) =>
  unwrapPage<ParameterChangeRow>(apiFor(token).GET('/api/v1/operator/parameters/history', { params: { query } }) as never);

// ── Ofertas y chat de una solicitud, solo lectura (ítem 1.13; el chat queda auditado) ──
export type OperatorBid = Schemas['OperatorBidRow'];
export type OperatorThread = Schemas['OperatorThreadRow'];
export type OperatorMessage = Schemas['OperatorMessageRow'];
export const requestBids = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/operator/requests/{id}/bids', { params: { path: { id } } }));
export const requestThreads = (token: string, id: string, reason?: string) =>
  unwrap(apiFor(token).GET('/api/v1/operator/requests/{id}/threads', { params: { path: { id }, query: { reason } } }));

/** Mensajes en orden cronológico; `before` = `nextCursor` de la página anterior (esa petición no se vuelve a auditar). */
export async function threadMessages(token: string, id: string, carrierOrgId: string, opts: { before?: string; reason?: string; size?: number } = {}): Promise<{ items: OperatorMessage[]; nextCursor: string | null }> {
  let res;
  try {
    res = await apiFor(token).GET('/api/v1/operator/requests/{id}/threads/{carrierOrgId}/messages', {
      params: { path: { id, carrierOrgId }, query: { before: opts.before, reason: opts.reason, size: opts.size ?? 30 } },
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  if (res.error !== undefined || !res.response.ok) {
    const e = (res.error ?? {}) as { error?: { code?: string; message?: string; field?: string | null } };
    throw new ApiError(res.response.status, e.error?.code ?? `HTTP-${res.response.status}`, e.error?.message ?? 'Ocurrió un error inesperado. Intenta nuevamente.', e.error?.field);
  }
  const body = res.data as { data?: OperatorMessage[]; meta?: { nextCursor?: string } };
  return { items: body.data ?? [], nextCursor: body.meta?.nextCursor ?? null };
}

// ----- Ola 2 zona X: operador 360, señales, cuentas y ajustes escalados (2.15, 2.6)
export type Organization360 = Schemas['Organization360View'];
export type FraudSignal = Schemas['FraudSignalView'];
export type OrgMember = Schemas['OrgMemberView'];
export type EscalatedAdjustment = Schemas['EscalatedAdjustmentRow'];

export const getOrganization360 = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/operator/organizations/{id}/overview', { params: { path: { id } } }));
export const getSignals = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/signals'));
export const suspendUser = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/operator/users/{id}/suspend', { params: { path: { id } }, body: { reason } }));
export const reactivateUser = (token: string, id: string, reason?: string) =>
  unwrap(apiFor(token).POST('/api/v1/operator/users/{id}/reactivate', { params: { path: { id } }, body: reason ? { reason } : {} }));
export const escalatedAdjustments = (token: string) => unwrap(apiFor(token).GET('/api/v1/operator/adjustments'));
