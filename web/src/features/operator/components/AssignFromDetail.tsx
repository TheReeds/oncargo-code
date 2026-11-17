import { useState } from 'react';
import { UserRoundCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import AssignSheet from './AssignSheet';
import type { AssignmentResult, SlaRequest } from '../server';

/** Botón "Asignar transportista" del detalle de una solicitud con SLA vencido (RN10). Tras asignar, lleva al envío generado. */
export default function AssignFromDetail({ request }: { request: SlaRequest }) {
  const [open, setOpen] = useState(false);
  function assigned(r: AssignmentResult) {
    window.location.href = r.shipmentId ? `/operador/envios/${r.shipmentId}` : '/operador';
  }
  return (
    <>
      <Button onClick={() => setOpen(true)}><UserRoundCheck /> Asignar transportista</Button>
      <AssignSheet request={open ? request : null} onClose={() => setOpen(false)} onAssigned={assigned} />
    </>
  );
}
