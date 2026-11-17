import { cn } from '@/lib/utils';
/** Bloque de carga con brillo suave (sin movimiento con `prefers-reduced-motion`). */
export const Skeleton = ({ className }: { className?: string }) => <div aria-hidden="true" className={cn('lx-skeleton rounded-input', className)} />;

/** Filas de tabla/lista mientras carga. */
export const SkeletonRows = ({ rows = 4, className }: { rows?: number; className?: string }) => (
  <div className={cn('space-y-2', className)} role="status" aria-label="Cargando">
    {Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}
  </div>
);

/** Esqueleto de una fila de listado con 2 líneas + cifra a la derecha; úsalo dentro de una `Card` mientras cargan los datos. */
export const SkeletonList = ({ rows = 5, className }: { rows?: number; className?: string }) => (
  <div className={cn('divide-y divide-steel-200', className)} role="status" aria-label="Cargando">
    {Array.from({ length: rows }, (_, i) => (
      <div key={i} className="flex items-center justify-between gap-4 px-5 py-3.5">
        <div className="min-w-0 flex-1 space-y-2"><Skeleton className="h-4 w-1/2" /><Skeleton className="h-3 w-1/3" /></div>
        <Skeleton className="h-5 w-16 rounded-full" />
      </div>
    ))}
  </div>
);
