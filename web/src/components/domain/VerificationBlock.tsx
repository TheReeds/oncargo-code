import { Alert } from '@/components/ui/alert';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { isVerificationFailure, type ActionFailure } from '@/lib/action-error';
import { blockCopy } from '@/features/verification/copy';

/**
 * Bloqueo por verificación (`LOGYX-ORG-003` sin verificar · `LOGYX-ORG-004` suspendida) dentro de un formulario o panel:
 * explica el estado y el siguiente paso en vez de un toast genérico.
 *  {failure && isVerificationFailure(failure) && <VerificationBlock failure={failure} />}
 */
export function VerificationBlock({ failure, className }: { failure: Pick<ActionFailure, 'code' | 'message'>; className?: string }) {
  const c = blockCopy(failure.code);
  return (
    <Alert tone={failure.code === 'LOGYX-ORG-004' ? 'error' : 'warning'} title={c.title} className={className}>
      <p>{failure.message}</p>
      <p className="mt-1">{c.next}</p>
    </Alert>
  );
}

/** Para acciones sin formulario (botón + confirmación): diálogo de aviso con el estado y el siguiente paso. */
export function explainVerificationBlock(failure: Pick<ActionFailure, 'code' | 'message'>): Promise<boolean> {
  const c = blockCopy(failure.code);
  return confirm({ title: c.title, description: `${failure.message} ${c.next}`, confirmLabel: 'Entendido', cancelLabel: 'Cerrar' });
}

/** Muestra un fallo de una acción: bloqueos de verificación con su explicación; el resto como toast. */
export function reportFailure(failure: ActionFailure, fallbackTitle?: string): void {
  if (isVerificationFailure(failure)) { void explainVerificationBlock(failure); return; }
  toast.error(fallbackTitle ?? failure.message, fallbackTitle ? failure.message : undefined);
}
