import { Eye } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useCan } from '@/lib/use-can';

/**
 * Aviso discreto para quien solo puede consultar (rol Lector): "Tu rol Lector solo permite consultar…". No renderiza nada si el
 * usuario puede operar. Úsalo donde se ocultaron acciones de escritura (`useCan('OPERATE')`); `children` agrega contexto
 * ("…no puedes publicar solicitudes").
 */
export function ReadOnlyNotice({ className, children }: { className?: string; children?: React.ReactNode }) {
  const canOperate = useCan('OPERATE');
  if (canOperate) return null;
  return (
    <p role="note" data-testid="read-only-notice" className={cn('flex items-start gap-2 rounded-input border border-steel-200 bg-steel-100/70 px-3 py-2 text-xs text-ink-700', className)}>
      <Eye className="mt-0.5 size-3.5 shrink-0 text-ink-500" aria-hidden="true" />
      <span>Tu rol Lector solo permite consultar.{children ? <> {children}</> : null} Si necesitas operar, pídele a un administrador de tu empresa que cambie tu rol.</span>
    </p>
  );
}
