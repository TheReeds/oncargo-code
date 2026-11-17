import * as React from 'react';
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Tabla de datos. Siempre dentro de una región enfocable con teclado (guía `responsive-table`).
 *  - `label`: nombre accesible de la región (recomendado en cada listado).
 *  - `stack`: bajo ~720 px de ANCHO DEL CONTENEDOR (container query) cada fila pasa a tarjeta; las celdas muestran su `label`
 *    (`<Td label="Recojo">`). `<Td primary>` = título de la tarjeta (sin etiqueta) y `<Td actions>` = pie con botones a todo el ancho.
 *  - `sticky`: cabecera fija dentro de un contenedor con scroll (máx. ~70vh) y sombra al desplazarse.
 *  - `dense`: filas más compactas.
 * Retrocompatible: sin props se comporta como antes (scroll horizontal).
 */
export const Table = ({ className, label, stack, sticky, dense, ...p }: React.TableHTMLAttributes<HTMLTableElement> & { label?: string; stack?: boolean; sticky?: boolean; dense?: boolean }) => {
  const ref = React.useRef<HTMLDivElement>(null);
  React.useEffect(() => {
    const el = ref.current;
    if (!el || !sticky) return;
    const sync = () => (el.scrollTop > 0 ? el.removeAttribute('data-at-top') : el.setAttribute('data-at-top', ''));
    sync();
    el.addEventListener('scroll', sync, { passive: true });
    return () => el.removeEventListener('scroll', sync);
  }, [sticky]);
  return (
    <div
      ref={ref}
      className="lx-table w-full"
      data-stack={stack ? '' : undefined}
      data-sticky={sticky ? '' : undefined}
      data-dense={dense ? '' : undefined}
      {...(label ? { role: 'region', 'aria-label': label, tabIndex: 0 } : {})}
    >
      <table className={cn('w-full text-sm', className)} {...p} />
    </div>
  );
};

type SortDir = 'asc' | 'desc' | null;
/** Encabezado de columna. Con `sort` + `onSort` es un botón con `aria-sort` (ordenar por columna; el orden lo aplica quien lista). */
export const Th = ({ className, sort, onSort, children, ...p }: React.ThHTMLAttributes<HTMLTableCellElement> & { sort?: SortDir; onSort?: () => void }) => (
  <th
    scope="col"
    aria-sort={onSort ? (sort === 'asc' ? 'ascending' : sort === 'desc' ? 'descending' : 'none') : undefined}
    className={cn('whitespace-nowrap border-b border-steel-200 bg-steel-100 px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-ink-500', className)}
    {...p}
  >
    {onSort ? (
      <button type="button" onClick={onSort} className="-mx-1 inline-flex items-center gap-1 rounded px-1 uppercase tracking-wide hover:text-ink-900">
        {children}
        {sort === 'asc' ? <ArrowUp className="size-3" aria-hidden="true" /> : sort === 'desc' ? <ArrowDown className="size-3" aria-hidden="true" /> : <ChevronsUpDown className="size-3 opacity-60" aria-hidden="true" />}
      </button>
    ) : children}
  </th>
);

export const Td = ({ className, label, primary, actions, ...p }: React.TdHTMLAttributes<HTMLTableCellElement> & { label?: string; primary?: boolean; actions?: boolean }) => (
  <td
    data-label={label}
    data-primary={primary ? '' : undefined}
    data-actions={actions ? '' : undefined}
    className={cn('border-b border-steel-200 px-4 py-3 align-middle', className)}
    {...p}
  />
);
export const Tr = ({ className, ...p }: React.HTMLAttributes<HTMLTableRowElement>) => (
  <tr className={cn('transition-colors hover:bg-steel-100/60 [&:last-child>td]:border-b-0', className)} {...p} />
);
