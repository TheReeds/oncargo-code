import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { CalendarClock, FileText, Star, TriangleAlert } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import DocumentsPanel from '@/features/documents/components/DocumentsPanel';
import ReviewPanel from '@/features/reputation/components/ReviewPanel';
import IncidentsPanel from '@/features/incidents/components/IncidentsPanel';
import { formatDate, formatDateTime } from '@/lib/format';
import type { ShipmentView } from '@/lib/api/server';
import ShipmentActions from './ShipmentActions';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';

/** Quién canceló (F06): se compara `cancelledByOrgId` con mi organización; la API no distingue operador de contraparte. */
export function useCancelledBy(shipment: ShipmentView, perspective: Perspective) {
  const [orgId, setOrgId] = useState<string | null>(null);
  const cancelled = shipment.status === 'CANCELLED';
  useEffect(() => {
    if (!cancelled) return;
    actions.negotiation.myOrg({}).then(({ data }) => { if (data) setOrgId(data.orgId); });
  }, [cancelled]);
  if (!cancelled || !shipment.cancelledByOrgId || !orgId) return null;
  if (shipment.cancelledByOrgId === orgId) return perspective === 'OPERATOR' ? 'Lo canceló el equipo de OnCargo' : 'Lo cancelaste tú';
  return perspective === 'OPERATOR' ? 'Lo canceló una de las partes' : perspective === 'PYME' ? 'Lo canceló el transportista o el equipo de OnCargo' : 'Lo canceló la PYME o el equipo de OnCargo';
}

/** Fecha de recojo y marca "Sin conductor" (F36) + motivo, fecha y autor de la cancelación (F06). */
export function ShipmentFacts({ shipment, perspective }: { shipment: ShipmentView; perspective: Perspective }) {
  const by = useCancelledBy(shipment, perspective);
  const open = shipment.status === 'PENDING' || shipment.status === 'IN_TRANSIT';
  return (
    <div className="space-y-3">
      {shipment.status === 'CANCELLED' && (
        <Alert tone="warning" title="Este envío fue cancelado">
          <p>
            {by ?? 'Fue cancelado'}{shipment.cancelledAt ? <> el {formatDateTime(shipment.cancelledAt)}</> : null}.
            {shipment.cancellationReason ? <> <span className="font-medium">Motivo:</span> {shipment.cancellationReason}</> : null}
          </p>
          <p className="mt-1">La solicitud también quedó cancelada y la flota quedó libre.</p>
        </Alert>
      )}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm">
        {shipment.pickupDate && (
          <span className="inline-flex items-center gap-1.5 text-ink-700"><CalendarClock className="size-4 text-ink-500" aria-hidden="true" />Recojo <span className="font-medium text-ink-900">{formatDate(shipment.pickupDate)}</span></span>
        )}
        {open && !shipment.driverId && perspective !== 'PYME' && <Badge tone="amber">Sin conductor</Badge>}
      </div>
    </div>
  );
}

/** Documentos, calificación e incidencias de un envío, con sus acciones. Cada panel carga sus datos por su cuenta (Astro Actions). */
export default function ShipmentExtras({ shipment, perspective }: { shipment: ShipmentView; perspective: 'PYME' | 'CARRIER' }) {
  const props = { shipmentId: shipment.id!, perspective, status: shipment.status!, hasActiveIncident: !!shipment.hasActiveIncident };
  return (
    <div className="space-y-4">
      <ShipmentFacts shipment={shipment} perspective={perspective} />
      <ShipmentActions shipment={shipment} perspective={perspective} />
      {shipment.hasActiveIncident && (
        <Alert tone="error" title="Este envío tiene una incidencia activa">
          El conductor no puede avanzar las paradas hasta que el operador de OnCargo la resuelva. Revisa el detalle en la pestaña Incidencias.
        </Alert>
      )}
      <Tabs defaultValue="documentos">
        <TabsList aria-label="Información del envío">
          <TabsTrigger value="documentos"><FileText className="mr-1.5 inline size-4 align-text-bottom max-sm:hidden" aria-hidden="true" />Documentos</TabsTrigger>
          <TabsTrigger value="calificacion"><Star className="mr-1.5 inline size-4 align-text-bottom max-sm:hidden" aria-hidden="true" />Calificación</TabsTrigger>
          <TabsTrigger value="incidencias"><TriangleAlert className="mr-1.5 inline size-4 align-text-bottom max-sm:hidden" aria-hidden="true" />Incidencias</TabsTrigger>
        </TabsList>
        <TabsContent value="documentos"><DocumentsPanel {...props} /></TabsContent>
        <TabsContent value="calificacion"><ReviewPanel {...props} /></TabsContent>
        <TabsContent value="incidencias"><IncidentsPanel {...props} /></TabsContent>
      </Tabs>
    </div>
  );
}
