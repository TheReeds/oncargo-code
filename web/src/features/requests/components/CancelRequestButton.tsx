import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';

/** Confirma y cancela una solicitud. Devuelve `true` si se canceló (la página debe recargarse). Lo usan el detalle y el listado. */
export async function confirmAndCancelRequest(id: string): Promise<boolean> {
  const ok = await confirm({
    title: '¿Cancelar esta solicitud?',
    description: 'Se cerrará la subasta y los transportistas dejarán de ver tu carga. Las ofertas recibidas se descartarán. No se puede deshacer.',
    confirmLabel: 'Cancelar solicitud',
    cancelLabel: 'Volver',
    tone: 'destructive',
  });
  if (!ok) return false;
  const { error } = await actions.requests.cancel({ id });
  if (error) {
    toast.error(error.message);
    return false;
  }
  flash('Solicitud cancelada');
  return true;
}

export default function CancelRequestButton({ id }: { id: string }) {
  const [busy, setBusy] = useState(false);

  async function cancel() {
    setBusy(true);
    if (await confirmAndCancelRequest(id)) window.location.reload();
    else setBusy(false);
  }

  return <Button variant="destructive" loading={busy} onClick={cancel}>Cancelar solicitud</Button>;
}
