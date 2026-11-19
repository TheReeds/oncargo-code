import { Bell, Route, Sparkles, Gavel, MessageSquare, Truck, TriangleAlert, CircleCheck, CircleX, Star, Handshake, ShieldCheck, Undo2, Ban, Hourglass, KeyRound, CalendarClock, Send, BellRing, FileClock, Receipt } from 'lucide-react';
import type { ComponentType } from 'react';

export type NotifOrg = 'PYME' | 'CARRIER' | 'OPERATOR' | null | undefined;
export type NotifLike = { type?: string; relatedType?: string; relatedId?: string };

/** Destino al hacer clic en una notificación, según el recurso relacionado y el tipo de organización. */
export function notificationHref(n: NotifLike, orgType: NotifOrg): string {
  const id = n.relatedId;
  const carrier = orgType === 'CARRIER';
  // El operador recibe, p. ej., `AUCTION_CLOSED` de una subasta sin ofertas: va a sus pantallas, no a las de la PYME.
  if (orgType === 'OPERATOR') {
    if (n.relatedType === 'REQUEST') return id ? `/operador/solicitudes/${id}` : '/operador/solicitudes';
    if (n.type === 'ADJUSTMENT_ESCALATED') return '/operador/ajustes';
    if (n.relatedType === 'SHIPMENT') return id ? `/operador/envios/${id}` : '/operador/envios';
    if (n.relatedType === 'ORGANIZATION') return '/operador/organizaciones';
  }
  // 2.9: la PYME pide confirmar la segunda oferta → pantalla de confirmaciones del transportista.
  if (n.type === 'BACKUP_REQUESTED' && carrier) return '/transportista/confirmaciones';
  // 2.3: una carga nueva que coincide con una búsqueda guardada abre la carga en «Cargas disponibles» (aún no hay oferta propia).
  if (n.type === 'NEW_LOAD_MATCH' && carrier) return id ? `/transportista/marketplace?solicitud=${id}` : '/transportista/marketplace';
  switch (n.relatedType) {
    case 'TEMPLATE':
      // 2.1: borrador listo o publicación fallida de una plantilla programada.
      return '/pyme/plantillas';
    case 'REQUEST':
      return id ? (carrier ? `/transportista/ofertas?solicitud=${id}` : `/pyme/solicitudes/${id}`) : carrier ? '/transportista/ofertas' : '/pyme/solicitudes';
    case 'SHIPMENT':
    case 'REVIEW':
      return id ? (carrier ? `/transportista/envios?id=${id}` : `/pyme/envios?id=${id}`) : carrier ? '/transportista/envios' : '/pyme/envios';
    case 'TRIP':
      return carrier ? (id ? `/transportista/viajes/${id}` : '/transportista/viajes') : '/pyme/reservas';
    case 'SMART_LOAD':
      return '/transportista/smart-load';
    case 'VEHICLE':
      // 2.4: documento de flota por vencer; abre los documentos del vehículo.
      return id ? `/transportista/flota?vehiculo=${id}` : '/transportista/flota';
    case 'DRIVER':
      return id ? `/transportista/flota?conductor=${id}` : '/transportista/flota';
    case 'INCIDENT':
      return id ? `/incidencias/${id}` : orgType === 'OPERATOR' ? '/operador/incidencias' : '/incidencias';
    case 'ORGANIZATION':
      return id ? `/organizaciones/${id}` : '/notificaciones';
    case 'USER':
      // V16: pedido de acceso; el ADMIN cambia el rol en Usuarios y roles.
      return id ? `/organizacion/usuarios?usuario=${id}` : '/organizacion/usuarios';
    case 'BID':
    case 'COUNTER_OFFER':
      return carrier ? '/transportista/ofertas' : '/pyme/solicitudes';
    default:
      return orgType === 'OPERATOR' ? '/operador' : '/notificaciones';
  }
}

export const NOTIFICATION_META: Record<string, { label: string; icon: ComponentType<{ className?: string }>; tone: string }> = {
  NEW_BID: { label: 'Nueva oferta', icon: Gavel, tone: 'bg-emerald-50 text-emerald-700' },
  BID_ACCEPTED: { label: 'Oferta aceptada', icon: CircleCheck, tone: 'bg-emerald-50 text-emerald-700' },
  BID_REJECTED: { label: 'Oferta no elegida', icon: CircleX, tone: 'bg-steel-100 text-ink-700' },
  COUNTER_OFFER: { label: 'Contraoferta', icon: Handshake, tone: 'bg-amber-50 text-amber-700' },
  NEW_MESSAGE: { label: 'Mensaje nuevo', icon: MessageSquare, tone: 'bg-petrol-50 text-petrol-700' },
  SHIPMENT_STATUS_CHANGE: { label: 'Estado del envío', icon: Truck, tone: 'bg-petrol-50 text-petrol-700' },
  INCIDENT_REPORTED: { label: 'Incidencia reportada', icon: TriangleAlert, tone: 'bg-red-50 text-red-600' },
  INCIDENT_RESOLVED: { label: 'Incidencia resuelta', icon: CircleCheck, tone: 'bg-emerald-50 text-emerald-700' },
  TRIP_BOOKING_REQUESTED: { label: 'Nueva reserva', icon: Route, tone: 'bg-amber-50 text-amber-700' },
  TRIP_BOOKING_CONFIRMED: { label: 'Reserva confirmada', icon: CircleCheck, tone: 'bg-emerald-50 text-emerald-700' },
  TRIP_BOOKING_REJECTED: { label: 'Reserva rechazada', icon: CircleX, tone: 'bg-red-50 text-red-600' },
  TRIP_BOOKING_CANCELLED: { label: 'Reserva cancelada', icon: CircleX, tone: 'bg-steel-100 text-ink-700' },
  TRIP_CANCELLED: { label: 'Viaje cancelado', icon: TriangleAlert, tone: 'bg-red-50 text-red-600' },
  SMART_LOAD_SUGGESTED: { label: 'Sugerencia Smart Load', icon: Sparkles, tone: 'bg-emerald-50 text-emerald-700' },
  SMART_LOAD_ACCEPTED: { label: 'Cargas agrupadas', icon: Route, tone: 'bg-petrol-50 text-petrol-700' },
  AUCTION_CLOSED: { label: 'Subasta cerrada', icon: Hourglass, tone: 'bg-amber-50 text-amber-700' },
  REQUEST_CANCELLED: { label: 'Solicitud cancelada', icon: Ban, tone: 'bg-steel-100 text-ink-700' },
  SHIPMENT_CANCELLED: { label: 'Envío cancelado', icon: Ban, tone: 'bg-red-50 text-red-600' },
  BID_WITHDRAWN: { label: 'Oferta retirada', icon: Undo2, tone: 'bg-steel-100 text-ink-700' },
  ORGANIZATION_VERIFICATION: { label: 'Verificación de empresa', icon: ShieldCheck, tone: 'bg-petrol-50 text-petrol-700' },
  NEW_REVIEW: { label: 'Nueva calificación', icon: Star, tone: 'bg-amber-50 text-amber-700' },
  ACCESS_REQUESTED: { label: 'Pedido de acceso', icon: KeyRound, tone: 'bg-amber-50 text-amber-700' },
  TEMPLATE_RUN: { label: 'Plantilla programada', icon: CalendarClock, tone: 'bg-petrol-50 text-petrol-700' },
  REQUEST_INVITATION: { label: 'Invitación a ofertar', icon: Send, tone: 'bg-emerald-50 text-emerald-700' },
  BACKUP_REQUESTED: { label: 'Confirma una carga', icon: Handshake, tone: 'bg-amber-50 text-amber-700' },
  NEW_LOAD_MATCH: { label: 'Carga que te interesa', icon: BellRing, tone: 'bg-emerald-50 text-emerald-700' },
  FLEET_DOCUMENT_EXPIRING: { label: 'Documento de flota', icon: FileClock, tone: 'bg-amber-50 text-amber-700' },
  BID_IMPROVED: { label: 'Oferta mejorada', icon: Gavel, tone: 'bg-emerald-50 text-emerald-700' },
  BACKUP_RESPONDED: { label: 'Segunda oferta', icon: Handshake, tone: 'bg-petrol-50 text-petrol-700' },
  // Ola 2 zona X (2.6, 2.7)
  ADJUSTMENT_PROPOSED: { label: 'Ajuste por revisar', icon: Receipt, tone: 'bg-amber-50 text-amber-700' },
  ADJUSTMENT_DECIDED: { label: 'Ajuste resuelto', icon: Receipt, tone: 'bg-petrol-50 text-petrol-700' },
  ADJUSTMENT_ESCALATED: { label: 'Ajuste para OnCargo', icon: Receipt, tone: 'bg-amber-50 text-amber-700' },
  INCIDENT_ASSIGNED: { label: 'Incidencia asignada', icon: TriangleAlert, tone: 'bg-amber-50 text-amber-700' },
};
export const notificationMeta = (type?: string) => NOTIFICATION_META[type ?? ''] ?? { label: 'Aviso', icon: Bell, tone: 'bg-steel-100 text-ink-700' };
