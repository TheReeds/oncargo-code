import { Eye } from 'lucide-react';
import { cn } from '@/lib/utils';
import { deniedReason, type Permission } from '@/lib/permissions';
import { useCan, useRole } from '@/lib/permissions/react';
import { RequestAccessButton } from '@/features/access/components/RequestAccessDialog';

/**
 * Aviso de solo lectura donde se ocultaron acciones: «Tu rol Lector solo permite consultar. …» + «Pedir acceso» (avisa a
 * los ADMIN). No renderiza nada si el usuario tiene `permission` (por omisión `OPERATE`). `children` agrega contexto
 * («No puedes publicar solicitudes.»). Lo usa `<Can readOnly>` (`lib/permissions/react.tsx`); úsalo directo solo si
 * necesitas el aviso sin envolver nada.
 */
export function ReadOnlyNotice({ className, children, permission = 'OPERATE', requestAccess = true }: { className?: string; children?: React.ReactNode; permission?: Permission; /** false = sin el botón «Pedir acceso». */ requestAccess?: boolean }) {
  const allowed = useCan(permission);
  const role = useRole();
  if (allowed) return null;
  return (
    <div role="note" data-testid="read-only-notice" className={cn('flex flex-wrap items-center gap-x-3 gap-y-2 rounded-input border border-steel-200 bg-steel-100/70 px-3 py-2 text-xs text-ink-700', className)}>
      <p className="flex min-w-0 flex-1 basis-64 items-start gap-2">
        <Eye className="mt-0.5 size-3.5 shrink-0 text-ink-500" aria-hidden="true" />
        <span>{deniedReason(role, permission)}{children ? <> {children}</> : null} Si lo necesitas, pide acceso a un administrador de tu empresa.</span>
      </p>
      {requestAccess && <RequestAccessButton permission={permission} className="h-7 shrink-0 px-2.5 text-xs" />}
    </div>
  );
}
