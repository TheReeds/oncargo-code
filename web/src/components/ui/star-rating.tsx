import * as React from 'react';
import { Star } from 'lucide-react';
import { cn } from '@/lib/utils';

const STAR = 'size-[1em]';

/** Estrella con relleno parcial (0–1) para promedios con decimales. */
function PartialStar({ fill }: { fill: number }) {
  return (
    <span className="relative inline-block size-[1em] shrink-0" aria-hidden="true">
      <Star className={cn(STAR, 'text-ink-400/70')} />
      <span className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: `${Math.round(Math.max(0, Math.min(1, fill)) * 100)}%` }}>
        <Star className={cn(STAR, 'fill-amber-500 text-amber-500')} />
      </span>
    </span>
  );
}

interface BaseProps { className?: string; /** Tamaño de las estrellas (Tailwind `text-*`). */ size?: 'sm' | 'md' | 'lg' }
const SIZE = { sm: 'text-sm', md: 'text-xl', lg: 'text-3xl' };

/**
 * Calificación de 1 a 5 estrellas.
 * - Solo lectura (`readOnly`): muestra promedios con decimales (`value=4.67`), con texto accesible "4.7 de 5".
 * - Entrada: radiogroup con teclado (flechas, Inicio/Fin); `label` es el nombre accesible del grupo.
 */
export function StarRating(props: BaseProps & ({ readOnly: true; value: number | null | undefined; label?: string } | { readOnly?: false; value: number; onValueChange: (v: number) => void; label: string; id?: string; invalid?: boolean; disabled?: boolean })) {
  const { className, size = 'md' } = props;
  if (props.readOnly) {
    const v = props.value ?? 0;
    return (
      <span role="img" aria-label={`${props.label ? props.label + ': ' : ''}${props.value == null ? 'sin calificación' : `${v.toFixed(1)} de 5`}`} className={cn('inline-flex items-center gap-0.5', SIZE[size], className)}>
        {[0, 1, 2, 3, 4].map((i) => <PartialStar key={i} fill={v - i} />)}
      </span>
    );
  }
  return <StarInput {...props} className={className} size={size} />;
}

function StarInput({ value, onValueChange, label, id, invalid, disabled, className, size = 'md' }: BaseProps & { value: number; onValueChange: (v: number) => void; label: string; id?: string; invalid?: boolean; disabled?: boolean }) {
  const [hover, setHover] = React.useState(0);
  const refs = React.useRef<(HTMLButtonElement | null)[]>([]);
  const shown = hover || value;

  function move(to: number) {
    const n = Math.max(1, Math.min(5, to));
    onValueChange(n);
    refs.current[n - 1]?.focus();
  }
  function onKeyDown(e: React.KeyboardEvent) {
    if (disabled) return;
    const cur = value || 0;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') { e.preventDefault(); move(cur + 1); }
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') { e.preventDefault(); move(cur === 0 ? 1 : cur - 1); }
    else if (e.key === 'Home') { e.preventDefault(); move(1); }
    else if (e.key === 'End') { e.preventDefault(); move(5); }
  }

  return (
    <div
      id={id}
      role="radiogroup"
      aria-label={label}
      aria-invalid={invalid || undefined}
      aria-disabled={disabled || undefined}
      onKeyDown={onKeyDown}
      onMouseLeave={() => setHover(0)}
      className={cn('inline-flex items-center gap-0.5 rounded-input p-0.5', SIZE[size], invalid && 'ring-1 ring-red-600', disabled && 'opacity-50', className)}
    >
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          ref={(el) => { refs.current[n - 1] = el; }}
          type="button"
          role="radio"
          aria-checked={value === n}
          aria-label={`${n} de 5`}
          tabIndex={value === n || (value === 0 && n === 1) ? 0 : -1}
          disabled={disabled}
          onClick={() => onValueChange(n)}
          onMouseEnter={() => !disabled && setHover(n)}
          className={cn('rounded p-0.5 transition-transform hover:scale-110 disabled:cursor-not-allowed disabled:hover:scale-100', !disabled && 'cursor-pointer')}
        >
          <Star className={cn(STAR, n <= shown ? 'fill-amber-500 text-amber-500' : 'text-ink-400')} aria-hidden="true" />
        </button>
      ))}
    </div>
  );
}
