import * as React from 'react';
import { Eye, EyeOff, ArrowBigUpDash } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from './input';

type Props = Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  /** Avisa cuando Bloq Mayús está activado mientras se escribe (útil en ingreso). */
  capsLockWarning?: boolean;
};

/** Input de contraseña con botón mostrar/ocultar. Úsalo dentro de `Field` con `id={htmlFor}`. */
export const PasswordInput = React.forwardRef<HTMLInputElement, Props>(({ className, capsLockWarning, onKeyDown, onKeyUp, onBlur, ...p }, ref) => {
  const [show, setShow] = React.useState(false);
  const [caps, setCaps] = React.useState(false);
  const capsId = React.useId();
  const track = (e: React.KeyboardEvent<HTMLInputElement>) => capsLockWarning && setCaps(e.getModifierState('CapsLock'));
  return (
    <div>
      <div className="relative">
        <Input
          ref={ref}
          type={show ? 'text' : 'password'}
          className={cn('pr-11', className)}
          // Contraseña: sin autocorrección ni mayúscula automática (móvil) y sin corrector ortográfico.
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
          {...p}
          aria-describedby={[p['aria-describedby'], caps ? capsId : null].filter(Boolean).join(' ') || undefined}
          onKeyDown={(e) => { track(e); onKeyDown?.(e); }}
          onKeyUp={(e) => { track(e); onKeyUp?.(e); }}
          onBlur={(e) => { setCaps(false); onBlur?.(e); }}
        />
        <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'} className="absolute right-1 top-1 grid size-8 place-items-center rounded-md text-ink-500 transition-colors hover:bg-steel-100 hover:text-ink-900">
          {show ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
        </button>
      </div>
      {caps && (
        <p id={capsId} className="mt-1 flex items-center gap-1 text-xs text-amber-700">
          <ArrowBigUpDash className="size-3.5" aria-hidden="true" /> Bloq Mayús está activado.
        </p>
      )}
    </div>
  );
});
PasswordInput.displayName = 'PasswordInput';
