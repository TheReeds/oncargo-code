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
export const reportIncident = (token: string, shipmentId: string, category: IncidentCategory, description: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/incidents', { params: { path: { id: shipmentId } }, body: { category, description } }));
export const addComment = (token: string, id: string, body: string) =>
  unwrap(apiFor(token).POST('/api/v1/incidents/{id}/comments', { params: { path: { id } }, body: { body } }));
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
