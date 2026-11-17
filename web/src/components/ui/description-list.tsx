import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * Lista de datos clave–valor para detalles (`<dl>` semántico: solo `dt`/`dd` hijos directos).
 *  <DescriptionList cols={2} items={[{ label: 'Peso', value: '2.5 t', mono: true }, { label: 'Recojo', value: '28 nov', hint: 'Av. Argentina 1234' }]} />
 * `mono` = cifra técnica (placa, km, ID); `money` = monto en emerald. `cols` se resuelve por container query (1 columna en contenedores estrechos).
 */
export type DescriptionItem = { label: string; value: ReactNode; hint?: ReactNode; mono?: boolean; money?: boolean; span?: boolean };
export function DescriptionList({ items, cols = 2, className }: { items: DescriptionItem[]; cols?: 1 | 2 | 3; className?: string }) {
  return (
    <div className="@container">
      <dl className={cn('grid grid-cols-1 gap-x-8 gap-y-4', cols >= 2 && '@md:grid-cols-2', cols === 3 && '@2xl:grid-cols-3', className)}>
        {items.filter((i) => i.value != null && i.value !== '').map((i) => (
          <div key={i.label} className={cn('min-w-0 border-l-2 border-steel-200 pl-3', i.span && '@md:col-span-full')}>
            <dt className="text-xs font-medium text-ink-500">{i.label}</dt>
            <dd className={cn('mt-0.5 text-sm text-ink-900', i.mono && 'font-mono tabular-nums', i.money && 'money text-base')}>
              {i.value}
              {i.hint && <span className="mt-0.5 block text-xs font-normal text-ink-500">{i.hint}</span>}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
