import { cn } from '@/lib/utils';
export const Skeleton = ({ className }: { className?: string }) => <div aria-hidden="true" className={cn('animate-pulse rounded-input bg-steel-200', className)} />;

/** Filas de tabla/lista mientras carga. */
export const SkeletonRows = ({ rows = 4, className }: { rows?: number; className?: string }) => (
  <div className={cn('space-y-2', className)} role="status" aria-label="Cargando">
    {Array.from({ length: rows }, (_, i) => <Skeleton key={i} className="h-12 w-full" />)}
  </div>
);
