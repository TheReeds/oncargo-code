import { apiFor, type Schemas } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import { unwrapPage } from '@/lib/api/page';

export type IncidentView = Schemas['IncidentView'];
export type IncidentStatus = Schemas['IncidentStatus'];
export type IncidentCategory = Schemas['IncidentCategory'];

export type IncidentQuery = { status?: string; active?: boolean; page?: number; size?: number };

export const listIncidents = (token: string, q: IncidentQuery = {}) =>
  unwrapPage<IncidentView>(apiFor(token).GET('/api/v1/incidents', { params: { query: { status: q.status || undefined, active: q.active || undefined, page: q.page ?? 0, size: q.size ?? 20 } } }) as never);
export const shipmentIncidents = (token: string, shipmentId: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipments/{id}/incidents', { params: { path: { id: shipmentId } } }));
export const getIncident = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/incidents/{id}', { params: { path: { id } } }));
export type ReportIncidentBody = Schemas['ReportIncident'];
/** Alta v2 (2.7): severidad, monto reclamado, adjuntos y clave de idempotencia (un doble clic no crea dos). */
export const reportIncident = (token: string, shipmentId: string, body: ReportIncidentBody, idempotencyKey?: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/incidents', {
    params: { path: { id: shipmentId }, header: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined } as never, body,
  }));
export const addComment = (token: string, id: string, body: string, internal = false) =>
  unwrap(apiFor(token).POST('/api/v1/incidents/{id}/comments', { params: { path: { id } }, body: internal ? { body, internal } : { body } }));
export const withdrawIncident = (token: string, id: string) =>
  unwrap(apiFor(token).POST('/api/v1/incidents/{id}/withdraw', { params: { path: { id } } }));
export const reopenIncident = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/incidents/{id}/reopen', { params: { path: { id } }, body: { reason } }));
/** Operador: tomar (sin `assigneeProfileId`), asignar a otra persona del operador o dejar sin responsable. */
export const assignIncident = (token: string, id: string, unassign = false) =>
  unwrap(apiFor(token).PUT('/api/v1/incidents/{id}/assignee', { params: { path: { id } }, body: unassign ? { unassign: true } : {} }));

export type OperatorIncidentRow = Schemas['OperatorIncidentRow'];
export type OperatorIncidentQuery = { status?: string; severity?: string; assignee?: 'me' | 'none'; breached?: boolean; order?: 'due' | 'recent'; page?: number; size?: number };
export const operatorIncidents = (token: string, q: OperatorIncidentQuery = {}) =>
  unwrapPage<OperatorIncidentRow>(apiFor(token).GET('/api/v1/operator/incidents', { params: { query: {
    status: q.status || undefined, severity: q.severity || undefined, assignee: q.assignee, breached: q.breached || undefined,
    order: q.order, page: q.page ?? 0, size: q.size ?? 20,
  } } }) as never);
/** Estado final del envío al resolver o cerrar (RF55). */
export type ShipmentOutcome = 'RESUME' | 'DELIVERED' | 'CANCELLED';
export const changeStatus = (token: string, id: string, status: IncidentStatus, resolutionNotes?: string, shipmentOutcome?: ShipmentOutcome) =>
  // `shipmentOutcome` entró al contrato después de generar `schema.d.ts`; el cast se puede quitar tras `npm run gen:api`.
  unwrap(apiFor(token).POST('/api/v1/incidents/{id}/status', { params: { path: { id } }, body: { status, resolutionNotes: resolutionNotes || undefined, shipmentOutcome } }));

export type RouteMap = Record<string, { origin: string; destination: string }>;

/** Ruta (origen → destino) por id de envío: la API de incidencias no la incluye. Mejor esfuerzo: si falla, queda vacío. */
export async function routesFor(token: string, orgType: string | null): Promise<RouteMap> {
  const map: RouteMap = {};
  try {
    if (orgType === 'OPERATOR') {
      const alerts = await unwrap(apiFor(token).GET('/api/v1/operator/sla-alerts'));
      for (const a of alerts?.shipmentsWithIncidents ?? []) if (a.shipmentId && a.originCity && a.destinationCity) map[a.shipmentId] = { origin: a.originCity, destination: a.destinationCity };
    } else {
      for (const s of (await unwrap(apiFor(token).GET('/api/v1/shipments'))) ?? []) if (s.id && s.originCity && s.destinationCity) map[s.id] = { origin: s.originCity, destination: s.destinationCity };
    }
  } catch { /* sin rutas */ }
  return map;
}
