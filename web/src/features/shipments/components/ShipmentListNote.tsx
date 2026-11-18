import { Badge } from '@/components/ui/badge';
import { formatDate } from '@/lib/format';
import type { ShipmentView } from '@/lib/api/server';

/**
 * Línea extra de una fila de la lista de envíos: fecha de recojo (F36), "Sin conductor" para el transportista y, si el envío
 * se canceló, el motivo (F06). Se monta dentro de `TrackingSplit` con una línea; no toca el mapa.
 */
export function ShipmentListNote({ shipment: s, perspective }: { shipment: ShipmentView; perspective: 'PYME' | 'CARRIER' }) {
  const open = s.status === 'PENDING' || s.status === 'IN_TRANSIT';
  const noDriver = open && !s.driverId && perspective === 'CARRIER';
  if (!s.pickupDate && !noDriver && !(s.status === 'CANCELLED' && s.cancellationReason)) return null;
  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500">
      {s.pickupDate && s.status !== 'DELIVERED' && <span>Recojo {formatDate(s.pickupDate)}</span>}
      {noDriver && <Badge tone="amber">Sin conductor</Badge>}
      {s.status === 'CANCELLED' && s.cancellationReason && <span className="max-w-full truncate" title={s.cancellationReason}>Motivo: {s.cancellationReason}</span>}
    </div>
  );
}
