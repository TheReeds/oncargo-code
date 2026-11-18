import { Check, Circle } from 'lucide-react';
import { cn } from '@/lib/utils';
import { PASSWORD_MAX, PASSWORD_MIN } from '../password-rules';

export type PasswordChecks = { length: boolean; match: boolean | null };

/** Reglas visibles de la contraseña nueva (el backend exige 8–72; "coinciden" solo se evalúa cuando ya se escribió la confirmación). */
export function checkPassword(password: string, confirm: string): PasswordChecks {
  return { length: password.length >= PASSWORD_MIN && password.length <= PASSWORD_MAX, match: confirm ? password === confirm : null };
}

/** Lista de requisitos con estado (cumplido / pendiente). Va enlazada con `aria-describedby` al campo de la contraseña nueva. */
export function PasswordRequirements({ id, password, confirm }: { id: string; password: string; confirm: string }) {
  const c = checkPassword(password, confirm);
  const rows: { ok: boolean; text: string }[] = [
    { ok: c.length, text: `Entre ${PASSWORD_MIN} y ${PASSWORD_MAX} caracteres` },
    { ok: c.match === true, text: 'Las dos contraseñas coinciden' },
  ];
  return (
    <ul id={id} className="space-y-1 text-xs" aria-label="Requisitos de la contraseña">
      {rows.map((r) => (
        <li key={r.text} className={cn('flex items-center gap-1.5', r.ok ? 'text-emerald-700' : 'text-ink-500')}>
          {r.ok ? <Check className="size-3.5" aria-hidden="true" /> : <Circle className="size-3.5" aria-hidden="true" />}
          <span>{r.text}</span>
          <span className="sr-only">{r.ok ? ': cumplido' : ': pendiente'}</span>
        </li>
      ))}
    </ul>
  );
}
