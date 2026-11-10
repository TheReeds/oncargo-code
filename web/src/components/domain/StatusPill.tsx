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
};

export function StatusPill({ status, kind }: { status: string; kind?: 'bid' }) {
  const key = kind === 'bid' && status === 'PENDING' ? 'PENDING_BID' : status;
  const [label, tone] = MAP[key] ?? [status, 'neutral' as Tone];
  return <Badge tone={tone}>{label}</Badge>;
}
