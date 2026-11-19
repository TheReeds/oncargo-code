import { AUDIT_ACTION_LABELS } from '@/features/operator/audit-labels';
import { roleLabel } from '@/lib/permissions';

/** Etiquetas del rastro: las de la auditoría del operador más las que solo existen aquí. */
const EXTRA: Record<string, string> = {
  BID_PLACED: 'Oferta enviada',
  ACCESS_REQUESTED: 'Pedido de acceso',
  AUCTION_REOPENED: 'Subasta reabierta',
  SHIPMENT_DELIVERED_BY_OPERATOR: 'Entrega confirmada por el operador',
  TRIP_PUBLISHED: 'Viaje publicado',
  TRIP_CLOSED: 'Viaje cerrado',
  TRIP_CANCELLED: 'Viaje cancelado',
  TRIP_BOOKING_REQUESTED: 'Reserva pedida',
  TRIP_BOOKING_CONFIRMED: 'Reserva confirmada',
  TRIP_BOOKING_REJECTED: 'Reserva rechazada',
  TRIP_BOOKING_CANCELLED: 'Reserva cancelada',
};

export const activityLabel = (action: string) => EXTRA[action] ?? AUDIT_ACTION_LABELS[action] ?? action;

/** «Ana Rojas (Operaciones)» o, si no ves a la persona (otra empresa / sistema), la empresa o «OnCargo». */
export function actorText(e: { actor?: { name?: string; role?: string } | null; organizationName?: string | null; own?: boolean }) {
  if (e.actor?.name) return e.actor.role ? `${e.actor.name} (${roleLabel(e.actor.role)})` : e.actor.name;
  if (e.organizationName && !e.own) return e.organizationName;
  return e.own ? 'Alguien de tu empresa' : 'OnCargo';
}
