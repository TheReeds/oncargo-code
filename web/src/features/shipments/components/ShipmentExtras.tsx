import { FileText, Star, TriangleAlert } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Alert } from '@/components/ui/alert';
import DocumentsPanel from '@/features/documents/components/DocumentsPanel';
import ReviewPanel from '@/features/reputation/components/ReviewPanel';
import IncidentsPanel from '@/features/incidents/components/IncidentsPanel';
import type { ShipmentView } from '@/lib/api/server';

/** Documentos, calificación e incidencias de un envío. Cada panel carga sus datos por su cuenta (Astro Actions). */
export default function ShipmentExtras({ shipment, perspective }: { shipment: ShipmentView; perspective: 'PYME' | 'CARRIER' }) {
  const props = { shipmentId: shipment.id!, perspective, status: shipment.status!, hasActiveIncident: !!shipment.hasActiveIncident };
  return (
    <div className="space-y-4">
      {shipment.hasActiveIncident && (
        <Alert tone="error" title="Este envío tiene una incidencia activa">
          El conductor no puede avanzar las paradas hasta que el operador de LOGYX la resuelva. Revisa el detalle en la pestaña Incidencias.
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
