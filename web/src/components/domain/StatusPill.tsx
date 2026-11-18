import { Badge } from '@/components/ui/badge';

type Tone = 'neutral' | 'petrol' | 'emerald' | 'amber' | 'red';
const MAP: Record<string, [string, Tone]> = {
  // Solicitud
  PUBLISHED: ['Publicada', 'petrol'],
  MATCHED: ['Adjudicada', 'emerald'],
  CANCELLED: ['Cancelada', 'neutral'],
  // Oferta
  PENDING_BID: ['Pendiente', 'amber'],
  ACCEPTED: ['Aceptada', 'emerald'],
  REJECTED: ['Rechazada', 'red'],
  WITHDRAWN: ['Retirada', 'neutral'],
  LOST: ['Perdida', 'red'],
  BID_CANCELLED: ['Cancelada', 'neutral'],
  // Envío
  PENDING: ['Por recoger', 'amber'],
  IN_TRANSIT: ['En tránsito', 'petrol'],
  DELIVERED: ['Entregado', 'emerald'],
  // Parada
  ARRIVED: ['En el punto', 'petrol'],
  COMPLETED: ['Completada', 'emerald'],
  // Vehículo / conductor
  AVAILABLE: ['Disponible', 'emerald'],
  IN_ROUTE: ['En ruta', 'petrol'],
  MAINTENANCE: ['Mantenimiento', 'amber'],
  ON_DUTY: ['En servicio', 'petrol'],
  OFF_DUTY: ['Fuera de servicio', 'neutral'],
  // Incidencia
  OPEN: ['Abierta', 'red'],
  IN_REVIEW: ['En revisión', 'amber'],
  RESOLVED: ['Resuelta', 'emerald'],
  CLOSED: ['Cerrada', 'neutral'],
  // Contra-oferta
  PENDING_COUNTER: ['Por responder', 'amber'],
  EXPIRED: ['Vencida', 'neutral'],
};

/** Estados de viajes publicados, reservas y sugerencias Smart Load (fase 4). Valores desconocidos caen en gris con el texto crudo. */
const TRIP: Record<string, [string, Tone]> = { OPEN: ['Abierto', 'emerald'], FILLING: ['Con reservas', 'petrol'], CLOSED: ['Cerrado', 'neutral'], COMPLETED: ['Completado', 'neutral'], CANCELLED: ['Cancelado', 'red'] };
const BOOKING: Record<string, [string, Tone]> = { PENDING: ['Por confirmar', 'amber'], CONFIRMED: ['Confirmada', 'emerald'], REJECTED: ['Rechazada', 'red'], CANCELLED: ['Cancelada', 'neutral'] };
const SMART: Record<string, [string, Tone]> = { PENDING: ['Pendiente', 'amber'], ACCEPTED: ['Aceptada', 'emerald'], REJECTED: ['Rechazada', 'neutral'], EXPIRED: ['Vencida', 'neutral'] };
/** Verificación de la organización (RN01). */
const VERIFICATION: Record<string, [string, Tone]> = { PENDING: ['En revisión', 'amber'], VERIFIED: ['Verificada', 'emerald'], REJECTED: ['Rechazada', 'red'], SUSPENDED: ['Suspendida', 'red'] };
/** Estado de una cuenta de usuario o conductor (V14): activa, invitación pendiente o desactivada. */
const ACCOUNT: Record<string, [string, Tone]> = { ACTIVE: ['Activo', 'emerald'], INVITED: ['Invitación pendiente', 'amber'], INACTIVE: ['Desactivado', 'neutral'] };
const BY_KIND = { trip: TRIP, booking: BOOKING, smart: SMART, verification: VERIFICATION, account: ACCOUNT };

export function StatusPill({ status, kind }: { status: string; kind?: 'bid' | 'counter' | 'trip' | 'booking' | 'smart' | 'verification' | 'account' }) {
  if (kind === 'trip' || kind === 'booking' || kind === 'smart' || kind === 'verification' || kind === 'account') {
    const [label, tone] = BY_KIND[kind][status] ?? [status, 'neutral' as Tone];
    return <Badge tone={tone}>{label}</Badge>;
  }
  const key = kind === 'bid' && status === 'PENDING' ? 'PENDING_BID' : kind === 'counter' && status === 'PENDING' ? 'PENDING_COUNTER' : status;
  const [label, tone] = MAP[key] ?? [status, 'neutral' as Tone];
  return <Badge tone={tone}>{label}</Badge>;
}
