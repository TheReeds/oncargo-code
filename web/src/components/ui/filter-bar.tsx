import * as React from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from './button';

/**
 * Barra de filtros de un listado: rejilla de campos (`Field`) + fila inferior con extras, contador y "Limpiar filtros".
 * <FilterBar count={total} noun={['carga','cargas']} active={hasFilters} onClear={clear} extra={<Checkbox…/>} sort={<Select…/>}>
 *   <Field…/> <Field…/>
 * </FilterBar>
 * Con uno o dos campos y sin `extra`, todo va en una sola fila (contador y «Limpiar filtros» a la derecha) para no dejar una franja vacía.
 */
export function FilterBar({ children, extra, sort, count, noun = ['resultado', 'resultados'], active, onClear, className }: {
  children: React.ReactNode;
  /** Controles de la segunda fila (a la izquierda). */
  extra?: React.ReactNode;
  /** Control de orden (a la derecha, junto al contador). */
  sort?: React.ReactNode;
  count?: number;
  noun?: [string, string];
  active?: boolean;
  onClear?: () => void;
  className?: string;
}) {
  const inline = !extra && React.Children.count(children) <= 2;
  const summary = (
    <div className={cn('flex min-h-10 flex-wrap items-center gap-3', !inline && 'ml-auto', inline && 'ml-auto self-end')}>
      {count != null && <span className="text-sm text-ink-500" aria-live="polite"><span className="font-mono font-semibold text-ink-900">{count}</span> {count === 1 ? noun[0] : noun[1]}</span>}
      {sort}
      {active && onClear && <Button variant="ghost" size="sm" onClick={onClear}><X /> Limpiar filtros</Button>}
    </div>
  );
  if (inline) {
    return (
      <div role="search" className={cn('flex flex-wrap items-end gap-x-4 gap-y-3 border-b border-steel-200 p-4 sm:p-5', className)}>
        <div className="grid min-w-0 flex-1 gap-3 sm:max-w-xl sm:grid-cols-2">{children}</div>
        {summary}
      </div>
    );
  }
  return (
    <div role="search" className={cn('space-y-4 border-b border-steel-200 p-4 sm:p-5', className)}>
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{children}</div>
      <div className="flex flex-wrap items-end gap-x-5 gap-y-3">
        {extra}
        {summary}
      </div>
    </div>
  );
}
