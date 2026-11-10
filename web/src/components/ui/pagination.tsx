import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from './button';

function pages(page: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const s = new Set([1, total, page - 1, page, page + 1]);
  const arr = [...s].filter((n) => n >= 1 && n <= total).sort((a, b) => a - b);
  const out: (number | '…')[] = [];
  arr.forEach((n, i) => { if (i && n - arr[i - 1] > 1) out.push('…'); out.push(n); });
  return out;
}

/** Lista para `meta.page` (base 1), `meta.size`, `meta.total` de la API. */
export function Pagination({ page, size, total, onPageChange, className }: { page: number; size: number; total: number; onPageChange: (p: number) => void; className?: string }) {
  const count = Math.max(1, Math.ceil(total / size));
  if (total <= size) return null;
  const from = (page - 1) * size + 1;
  const to = Math.min(total, page * size);
  return (
    <nav aria-label="Paginación" className={cn('flex flex-wrap items-center justify-between gap-3 text-sm', className)}>
      <p className="tabular-nums text-ink-500">Mostrando {from}–{to} de {total}</p>
      <ul className="flex items-center gap-1">
        <li><Button variant="ghost" size="icon" aria-label="Página anterior" disabled={page <= 1} onClick={() => onPageChange(page - 1)}><ChevronLeft /></Button></li>
        {pages(page, count).map((n, i) => (
          <li key={i}>
            {n === '…' ? <span className="px-2 text-ink-500">…</span> : (
              <Button variant={n === page ? 'default' : 'ghost'} size="icon" aria-label={`Página ${n}`} aria-current={n === page ? 'page' : undefined} onClick={() => onPageChange(n)} className="tabular-nums">{n}</Button>
            )}
          </li>
        ))}
        <li><Button variant="ghost" size="icon" aria-label="Página siguiente" disabled={page >= count} onClick={() => onPageChange(page + 1)}><ChevronRight /></Button></li>
      </ul>
    </nav>
  );
}
