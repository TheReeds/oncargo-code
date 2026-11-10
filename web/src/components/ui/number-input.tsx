import * as React from 'react';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';

type Props = { value: string; onValueChange: (v: string) => void; id?: string; invalid?: boolean; disabled?: boolean; placeholder?: string; className?: string; name?: string; decimals?: number; suffix?: string; prefix?: string; 'aria-describedby'?: string };

const group = (int: string) => int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
/** '1850.5' → '1,850.5' (formato es-PE al mostrar; el valor interno es siempre un string numérico sin comas). */
const display = (raw: string) => { if (!raw) return ''; const [i, d] = raw.split('.'); return d !== undefined ? `${group(i)}.${d}` : group(i); };

/**
 * Entrada numérica con miles es-PE y prefijo/sufijo. `value` es un string "crudo" ("1850.5"); conviértelo con Number().
 * Teclado numérico en móvil (inputMode decimal). Úsalo para peso (suffix="kg"), volumen, etc.
 */
export const NumberInput = React.forwardRef<HTMLInputElement, Props>(({ value, onValueChange, decimals = 0, suffix, prefix, className, invalid, ...p }, ref) => {
  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let t = e.target.value.replace(/,/g, '').replace(/[^\d.]/g, '');
    const [i, ...rest] = t.split('.');
    t = decimals > 0 && rest.length ? `${i}.${rest.join('').slice(0, decimals)}` : i;
    onValueChange(t);
  };
  return (
    <div className="relative">
      {prefix && <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 font-mono text-sm text-ink-500">{prefix}</span>}
      <input ref={ref} inputMode={decimals > 0 ? 'decimal' : 'numeric'} autoComplete="off" value={display(value)} onChange={onChange} aria-invalid={invalid || undefined} className={cn(fieldClass, 'h-10 font-mono tabular-nums', prefix && 'pl-10', suffix && 'pr-10', className)} {...p} />
      {suffix && <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 font-mono text-sm text-ink-500">{suffix}</span>}
    </div>
  );
});
NumberInput.displayName = 'NumberInput';

/** Monto en soles: prefijo `S/`, 2 decimales máx., formato 1,850.00. */
export const MoneyInput = React.forwardRef<HTMLInputElement, Omit<Props, 'prefix' | 'decimals' | 'suffix'>>((p, ref) => (
  <NumberInput ref={ref} prefix="S/" decimals={2} placeholder="0.00" {...p} />
));
MoneyInput.displayName = 'MoneyInput';
