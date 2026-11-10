import * as S from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { fieldClass } from './input';

export type Option = { value: string; label: string; hint?: string; disabled?: boolean };

/**
 * Select estilizado (Radix). Valor controlado con `value`/`onValueChange`; '' = sin selección (muestra placeholder).
 * Usa `id` con <Field htmlFor>. Para listas de más de ~8 opciones con búsqueda usa <Combobox>.
 */
export function Select({ value, onValueChange, options, placeholder = 'Selecciona…', id, invalid, disabled, className, name, 'aria-label': ariaLabel }: { 'aria-label'?: string; value: string; onValueChange: (v: string) => void; options: Option[]; placeholder?: string; id?: string; invalid?: boolean; disabled?: boolean; className?: string; name?: string }) {
  return (
    <S.Root value={value || undefined} onValueChange={onValueChange} disabled={disabled} name={name}>
      <S.Trigger id={id} aria-label={ariaLabel} aria-invalid={invalid || undefined} className={cn(fieldClass, 'flex h-10 items-center justify-between gap-2 text-left data-[placeholder]:text-ink-500', className)}>
        <span className="truncate"><S.Value placeholder={placeholder} /></span>
        <S.Icon><ChevronDown className="size-4 text-ink-500" aria-hidden="true" /></S.Icon>
      </S.Trigger>
      <S.Portal>
        <S.Content position="popper" sideOffset={4} className="z-[60] max-h-72 min-w-[var(--radix-select-trigger-width)] overflow-hidden rounded-input border border-steel-200 bg-surface shadow-lg">
          <S.Viewport className="p-1">
            {options.map((o) => (
              <S.Item key={o.value} value={o.value} disabled={o.disabled} className="relative flex cursor-pointer select-none items-center gap-2 rounded-md py-2 pl-8 pr-3 text-sm text-ink-900 outline-none data-[disabled]:pointer-events-none data-[disabled]:opacity-50 data-[highlighted]:bg-petrol-50 data-[highlighted]:text-petrol-800">
                <S.ItemIndicator className="absolute left-2"><Check className="size-4 text-petrol-700" aria-hidden="true" /></S.ItemIndicator>
                <S.ItemText>{o.label}</S.ItemText>
                {o.hint && <span className="ml-auto text-xs text-ink-500">{o.hint}</span>}
              </S.Item>
            ))}
          </S.Viewport>
        </S.Content>
      </S.Portal>
    </S.Root>
  );
}
