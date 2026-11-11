import { Bell, Gavel, MessageSquare, Truck, TriangleAlert, CircleCheck, CircleX, Star, Handshake } from 'lucide-react';
import type { ComponentType } from 'react';

export type NotifOrg = 'PYME' | 'CARRIER' | 'OPERATOR' | null | undefined;
export type NotifLike = { type?: string; relatedType?: string; relatedId?: string };

/** Destino al hacer clic en una notificación, según el recurso relacionado y el tipo de organización. */
export function notificationHref(n: NotifLike, orgType: NotifOrg): string {
  const id = n.relatedId;
  const carrier = orgType === 'CARRIER';
  switch (n.relatedType) {
    case 'REQUEST':
      return id ? (carrier ? `/transportista/ofertas?solicitud=${id}` : `/pyme/solicitudes/${id}`) : carrier ? '/transportista/ofertas' : '/pyme/solicitudes';
    case 'SHIPMENT':
    case 'REVIEW':
      return id ? (carrier ? `/transportista/envios?id=${id}` : `/pyme/envios?id=${id}`) : carrier ? '/transportista/envios' : '/pyme/envios';
    case 'INCIDENT':
      return id ? `/incidencias/${id}` : orgType === 'OPERATOR' ? '/operador/incidencias' : '/incidencias';
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
  NEW_REVIEW: { label: 'Nueva calificación', icon: Star, tone: 'bg-amber-50 text-amber-700' },
};
export const notificationMeta = (type?: string) => NOTIFICATION_META[type ?? ''] ?? { label: 'Aviso', icon: Bell, tone: 'bg-steel-100 text-ink-700' };
