import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';

export default function CancelRequestButton({ id }: { id: string }) {
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function cancel() {
    setBusy(true);
    const { error } = await actions.requests.cancel({ id });
    if (error) {
      setError(error.message);
      setBusy(false);
      return;
    }
    window.location.reload();
  }

  if (!confirm) return <Button variant="destructive" onClick={() => setConfirm(true)}>Cancelar solicitud</Button>;
  return (
    <div className="space-y-2">
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex items-center gap-2">
        <span className="text-sm text-ink-700">¿Cancelar esta solicitud? Se cerrará la subasta.</span>
        <Button variant="destructive" size="sm" disabled={busy} onClick={cancel}>{busy ? 'Cancelando…' : 'Sí, cancelar'}</Button>
        <Button variant="ghost" size="sm" onClick={() => setConfirm(false)}>Volver</Button>
      </div>
    </div>
  );
}
