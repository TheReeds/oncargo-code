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
/** Ajuste del precio de un envío (2.6). `ESCALATED` = la PYME no respondió a tiempo y decide OnCargo. */
const ADJUSTMENT: Record<string, [string, Tone]> = { PENDING: ['Por responder', 'amber'], ACCEPTED: ['Aceptado', 'emerald'], REJECTED: ['Rechazado', 'red'], WITHDRAWN: ['Retirado', 'neutral'], ESCALATED: ['Lo decide OnCargo', 'petrol'] };
/** Severidad de una incidencia (2.7). */
const SEVERITY: Record<string, [string, Tone]> = { LOW: ['Baja', 'neutral'], MEDIUM: ['Media', 'petrol'], HIGH: ['Alta', 'amber'], CRITICAL: ['Crítica', 'red'] };
/** Cobro de un envío entregado (2.12). */
const SETTLEMENT: Record<string, [string, Tone]> = { PENDING_INVOICE: ['Falta comprobante', 'amber'], INVOICED: ['Facturado', 'petrol'], PAID: ['Pagado', 'petrol'], CONFIRMED: ['Cobrado', 'emerald'], DISPUTED: ['En disputa', 'red'] };
/** Comisión de OnCargo sobre un envío (solo transportista y operador). */
const COMMISSION: Record<string, [string, Tone]> = { PENDING: ['Por cobrar', 'amber'], COLLECTED: ['Cobrada', 'emerald'], NOT_APPLICABLE: ['No aplica', 'neutral'] };
const BY_KIND = { trip: TRIP, booking: BOOKING, smart: SMART, verification: VERIFICATION, account: ACCOUNT, adjustment: ADJUSTMENT, severity: SEVERITY, settlement: SETTLEMENT, commission: COMMISSION };

export function StatusPill({ status, kind }: { status: string; kind?: 'bid' | 'counter' | 'trip' | 'booking' | 'smart' | 'verification' | 'account' | 'adjustment' | 'severity' | 'settlement' | 'commission' }) {
  if (kind === 'trip' || kind === 'booking' || kind === 'smart' || kind === 'verification' || kind === 'account' || kind === 'adjustment' || kind === 'severity' || kind === 'settlement' || kind === 'commission') {
    const [label, tone] = BY_KIND[kind][status] ?? [status, 'neutral' as Tone];
    return <Badge tone={tone}>{label}</Badge>;
  }
  const key = kind === 'bid' && status === 'PENDING' ? 'PENDING_BID' : kind === 'counter' && status === 'PENDING' ? 'PENDING_COUNTER' : status;
  const [label, tone] = MAP[key] ?? [status, 'neutral' as Tone];
  return <Badge tone={tone}>{label}</Badge>;
}
