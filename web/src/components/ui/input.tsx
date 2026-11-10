import * as React from 'react';
import { cn } from '@/lib/utils';

/** Clase base compartida por todos los controles de formulario (input, select, combobox, date…). */
export const fieldClass =
  'w-full rounded-input border border-ink-400/60 bg-surface px-3 text-sm text-ink-900 placeholder:text-ink-500 transition-colors hover:border-ink-500 focus-visible:border-petrol-700 focus-visible:outline-2 disabled:cursor-not-allowed disabled:bg-steel-100 disabled:text-ink-500 aria-[invalid=true]:border-red-600';

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(({ className, ...p }, ref) => (
  <input ref={ref} className={cn(fieldClass, 'h-10', className)} {...p} />
));
Input.displayName = 'Input';

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, ...p }, ref) => (
  <textarea ref={ref} className={cn(fieldClass, 'min-h-20 py-2', className)} {...p} />
));
Textarea.displayName = 'Textarea';
export { Field, Label } from './field';
