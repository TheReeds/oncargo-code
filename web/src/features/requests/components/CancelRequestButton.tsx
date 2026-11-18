import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';

/** Diálogo para cancelar una solicitud con motivo opcional (F16: se avisa a los transportistas con oferta pendiente). Recarga la página al cancelar. Lo usan el detalle y el listado. */
export function CancelRequestDialog({ id, open, onOpenChange }: { id: string; open: boolean; onOpenChange: (o: boolean) => void }) {
  return (
    <ReasonDialog
      open={open}
      onOpenChange={onOpenChange}
      title="¿Cancelar esta solicitud?"
      description="Se cerrará la subasta y los transportistas dejarán de ver tu carga. Las ofertas recibidas se descartarán y se les avisará. No se puede deshacer."
      label="Motivo de la cancelación"
      hint="Opcional. Los transportistas con ofertas lo verán."
      placeholder="Ej. Cambió la fecha de despacho."
      maxLength={LIMITS.requestCancelReason}
      confirmLabel="Cancelar solicitud"
      tone="destructive"
      onSubmit={async (reason) => {
        const { error } = await actions.requests.cancel({ id, reason: reason || undefined });
        if (error) return splitActionError(error);
        flash('Solicitud cancelada');
        window.location.reload();
        return null;
      }}
    />
  );
}

export default function CancelRequestButton({ id }: { id: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="destructive" onClick={() => setOpen(true)}>Cancelar solicitud</Button>
      <CancelRequestDialog id={id} open={open} onOpenChange={setOpen} />
    </>
  );
}
