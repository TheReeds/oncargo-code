import type { SettlementsQuery } from './server';

/** `estado` de la URL: un estado del cobro o los atajos `VENCIDOS` (sin pagar y pasados de fecha) y `COMISION` (comisión por cobrar). */
export type ListFilters = { estado: string; desde: string; hasta: string; pagina: number };
export const PAGE_SIZE = 20;
const STATES = ['PENDING_INVOICE', 'INVOICED', 'PAID', 'CONFIRMED', 'DISPUTED'];

export function filtersFromUrl(p: URLSearchParams): ListFilters {
  const estado = p.get('estado') ?? '';
  return {
    estado: estado === 'VENCIDOS' || estado === 'COMISION' || STATES.includes(estado) ? estado : '',
    desde: /^\d{4}-\d{2}-\d{2}$/.test(p.get('desde') ?? '') ? p.get('desde')! : '',
    hasta: /^\d{4}-\d{2}-\d{2}$/.test(p.get('hasta') ?? '') ? p.get('hasta')! : '',
    pagina: Math.max(1, Number(p.get('pagina')) || 1),
  };
}

export const apiQuery = (f: ListFilters): SettlementsQuery => ({
  status: STATES.includes(f.estado) ? f.estado : undefined,
  overdue: f.estado === 'VENCIDOS' || undefined,
  commissionPending: f.estado === 'COMISION' || undefined,
  from: f.desde || undefined,
  to: (f.hasta || f.desde) || undefined,
  page: f.pagina - 1,
  size: PAGE_SIZE,
});
