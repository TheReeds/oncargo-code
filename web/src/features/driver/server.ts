import { apiFor, type ShipmentView } from '@/lib/api/server';
import { ApiError, unwrap } from '@/lib/api/client';

/** Viaje activo del conductor (`null` si no tiene ninguno asignado). */
export async function activeShipment(token: string): Promise<ShipmentView | null> {
  try {
    const s = await unwrap(apiFor(token).GET('/api/v1/driver/shipments/active'));
    return s && s.id ? s : null;
  } catch (e) {
    if (e instanceof ApiError && (e.status === 404 || e.status === 204)) return null;
    throw e;
  }
}

/** Viajes anteriores del conductor: entregados y cancelados, los más recientes primero (F22, `scope=HISTORY`). */
export async function shipmentHistory(token: string): Promise<ShipmentView[]> {
  return unwrap(apiFor(token).GET('/api/v1/driver/shipments', { params: { query: { scope: 'HISTORY' } } }));
}
