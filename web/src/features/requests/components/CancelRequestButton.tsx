import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';

export default function CancelRequestButton({ id }: { id: string }) {
  const [busy, setBusy] = useState(false);

  async function cancel() {
    const ok = await confirm({
      title: '¿Cancelar esta solicitud?',
      description: 'Se cerrará la subasta y los transportistas dejarán de ver tu carga. Las ofertas recibidas se descartarán. No se puede deshacer.',
      confirmLabel: 'Cancelar solicitud',
      cancelLabel: 'Volver',
      tone: 'destructive',
    });
    if (!ok) return;
    setBusy(true);
    const { error } = await actions.requests.cancel({ id });
    if (error) {
      toast.error(error.message);
      setBusy(false);
      return;
    }
    flash('Solicitud cancelada');
    window.location.reload();
  }

  return <Button variant="destructive" loading={busy} onClick={cancel}>Cancelar solicitud</Button>;
}
