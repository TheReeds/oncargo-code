import * as React from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from './input';

/** Input de contraseña con botón mostrar/ocultar. Úsalo dentro de `Field` con `id={htmlFor}`. */
export const PasswordInput = React.forwardRef<HTMLInputElement, Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'>>(({ className, ...p }, ref) => {
  const [show, setShow] = React.useState(false);
  return (
    <div className="relative">
      <Input ref={ref} type={show ? 'text' : 'password'} className={cn('pr-11', className)} {...p} />
      <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={show} className="absolute right-1 top-1 grid size-8 place-items-center rounded-md text-ink-500 hover:bg-steel-100 hover:text-ink-900">
        {show ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
      </button>
    </div>
  );
});
PasswordInput.displayName = 'PasswordInput';
