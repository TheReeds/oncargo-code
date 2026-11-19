import { apiFor } from '@/lib/api/server';
import { API_URL, unwrap } from '@/lib/api/client';
import { unwrapPage } from '@/lib/api/page';
import type { Schemas, ShipmentView } from '@/lib/api/server';
import type { ShipmentsQuery } from './filters';

export const listShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/shipments'));
/** Envíos con filtros y paginación en el servidor (roadmap 1.5); `meta.total` para la paginación. */
export const listShipmentsPage = (token: string, query: ShipmentsQuery = {}) =>
  unwrapPage<ShipmentView>(apiFor(token).GET('/api/v1/shipments', { params: { query } }) as never);
export const getShipment = (token: string, id: string) => unwrap(apiFor(token).GET('/api/v1/shipments/{id}', { params: { path: { id } } }));

/**
 * Enlace directo `?id=` (avisos, otras pantallas): si el envío no está en la página filtrada (p. ej. ya entregado y la
 * pestaña es «En curso»), se agrega al principio para poder abrirlo igual.
 */
export async function withSelected(token: string, items: ShipmentView[], id: string | null): Promise<ShipmentView[]> {
  if (!id || items.some((s) => s.id === id) || !/^[0-9a-f-]{36}$/i.test(id)) return items;
  const one = await getShipment(token, id).catch(() => null);
  return one?.id ? [one, ...items] : items;
}
export const assignDriver = (token: string, id: string, driverId: string, reason?: string) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/driver', { params: { path: { id } }, body: reason ? { driverId, reason } : { driverId } }));
export const driverShipments = (token: string) => unwrap(apiFor(token).GET('/api/v1/driver/shipments'));
/** F06: cancela un envío con motivo (PYME y transportista antes del recojo; el operador también en tránsito). */
export const cancelShipment = (token: string, id: string, reason: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/cancel', { params: { path: { id } }, body: { reason } }));
/** F06: cambia el vehículo del envío (RN02, `CAP-*`); el motivo es obligatorio en ruta (`SHP-006`). */
export const changeVehicle = (token: string, id: string, vehicleId: string, reason?: string) =>
  unwrap(apiFor(token).PUT('/api/v1/shipments/{id}/vehicle', { params: { path: { id } }, body: reason ? { vehicleId, reason } : { vehicleId } }));

// ----- Enlaces públicos de seguimiento (roadmap 1.6, ADR-0011)
export type ShareLinkView = Schemas['ShareLinkView'];
export type ShareLinkCreated = Schemas['ShareLinkCreated'];
export type PublicTrackingView = Schemas['PublicTrackingView'];

export const listShareLinks = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipments/{id}/share-links', { params: { path: { id } } }));
/** El token y la URL vienen solo en esta respuesta (en la BD queda su hash). */
export const createShareLink = (token: string, id: string, expiresInDays?: number) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/share-links', { params: { path: { id } }, body: expiresInDays ? { expiresInDays } : {} }));
export const revokeShareLink = (token: string, id: string, linkId: string) =>
  unwrap(apiFor(token).POST('/api/v1/shipments/{id}/share-links/{linkId}/revoke', { params: { path: { id, linkId } } }));

/**
 * Seguimiento público (sin sesión). Reenvía la IP del visitante (`X-Forwarded-For`): el límite del backend es por IP.
 * `null` = el enlace no existe, se revocó o venció (la API responde lo mismo en los tres casos).
 */
export async function publicTracking(token: string, clientIp?: string): Promise<{ data: PublicTrackingView | null; status: number }> {
  if (!/^[A-Za-z0-9_-]{22}$/.test(token)) return { data: null, status: 404 };
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/v1/public/tracking/${encodeURIComponent(token)}`, {
      headers: { accept: 'application/json', ...(clientIp ? { 'x-forwarded-for': clientIp } : {}) },
    });
  } catch {
    return { data: null, status: 502 };
  }
  if (!res.ok) return { data: null, status: res.status };
  const body = (await res.json()) as { data?: PublicTrackingView };
  return { data: body.data ?? null, status: 200 };
}
