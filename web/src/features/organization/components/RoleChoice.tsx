import { RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { cn } from '@/lib/utils';
import { ROLE_DESCRIPTION, ROLE_LABEL } from '@/lib/permissions';

export type AssignableRole = 'ADMIN' | 'OPS' | 'VIEWER';
const ORDER: AssignableRole[] = ['ADMIN', 'OPS', 'VIEWER'];

/** Elegir el rol de un usuario: cada opción dice qué puede hacer (RadioGroup accesible, una tarjeta por rol). */
export function RoleChoice({ value, onValueChange, idPrefix, legend = 'Rol' }: { value: AssignableRole; onValueChange: (r: AssignableRole) => void; idPrefix: string; legend?: string }) {
  return (
    <fieldset>
      <legend className="mb-1.5 text-[13px] font-medium text-ink-700">{legend}</legend>
      <RadioGroup value={value} onValueChange={(v) => onValueChange(v as AssignableRole)} aria-label={legend} className="gap-2">
        {ORDER.map((r) => {
          const id = `${idPrefix}-${r}`;
          return (
            <label key={r} htmlFor={id} className={cn('flex cursor-pointer items-start gap-3 rounded-input border p-3 transition-colors has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-petrol-700', value === r ? 'border-petrol-700 bg-petrol-50' : 'border-steel-200 hover:border-ink-400')}>
              <RadioGroupItem id={id} value={r} className="mt-0.5" />
              <span className="min-w-0">
                <span className="block text-sm font-medium text-ink-900">{ROLE_LABEL[r]}</span>
                <span className="mt-0.5 block text-xs text-ink-700">{ROLE_DESCRIPTION[r]}</span>
              </span>
            </label>
          );
        })}
      </RadioGroup>
    </fieldset>
  );
}
