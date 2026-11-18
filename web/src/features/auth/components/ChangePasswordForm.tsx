import { useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { KeyRound } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PasswordInput } from '@/components/ui/password-input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { toFormErrors } from '@/lib/action-error';
import { PasswordRequirements, checkPassword } from './PasswordRequirements';
import { PASSWORD_MAX } from '../password-rules';

type Errors = { currentPassword?: string; newPassword?: string; confirm?: string };

/** "Cambiar contraseña" de Cuenta. La API devuelve un token nuevo (la action reemplaza la cookie): las demás sesiones quedan fuera. */
export default function ChangePasswordForm() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const refs = { currentPassword: useRef<HTMLInputElement>(null), newPassword: useRef<HTMLInputElement>(null), confirm: useRef<HTMLInputElement>(null) };
  const clear = (k: keyof Errors) => () => { setFormError(null); setErrors((p) => (p[k] ? { ...p, [k]: undefined } : p)); };

  async function onSubmit(ev: React.FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    if (busy) return;
    setFormError(null);
    const c = checkPassword(next, confirm);
    const e: Errors = {};
    if (!current) e.currentPassword = 'Ingresa tu contraseña actual.';
    if (!next) e.newPassword = 'Ingresa tu contraseña nueva.';
    else if (!c.length) e.newPassword = 'La contraseña debe tener entre 8 y 72 caracteres.';
    else if (next === current) e.newPassword = 'La contraseña nueva debe ser distinta de la actual.';
    if (!confirm) e.confirm = 'Repite la contraseña nueva.';
    else if (next !== confirm) e.confirm = 'Las contraseñas no coinciden.';
    setErrors(e);
    const first = (['currentPassword', 'newPassword', 'confirm'] as const).find((k) => e[k]);
    if (first) { refs[first].current?.focus(); return; }

    setBusy(true);
    let res;
    try {
      res = await actions.password.change({ currentPassword: current, newPassword: next });
    } catch {
      setFormError('No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    setBusy(false);
    if (res.error) {
      const { errors: fe, formError: other } = toFormErrors(res.error, ['currentPassword', 'newPassword']);
      setErrors(fe);
      setFormError(other);
      if (fe.currentPassword) refs.currentPassword.current?.focus();
      else if (fe.newPassword) refs.newPassword.current?.focus();
      return;
    }
    setCurrent(''); setNext(''); setConfirm(''); setErrors({});
    toast.success('Contraseña actualizada', 'Cerramos tu sesión en los demás dispositivos.');
  }

  return (
    <form onSubmit={onSubmit} method="post" className="max-w-md space-y-4" noValidate>
      {formError && <Alert tone="error">{formError}</Alert>}
      <Field label="Contraseña actual" htmlFor="current-password" error={errors.currentPassword}>
        <PasswordInput ref={refs.currentPassword} id="current-password" name="current-password" autoComplete="current-password" enterKeyHint="next" maxLength={PASSWORD_MAX} value={current}
          aria-invalid={!!errors.currentPassword} aria-describedby={errors.currentPassword ? 'current-password-error' : undefined}
          onChange={(e) => { setCurrent(e.currentTarget.value); clear('currentPassword')(); }} />
      </Field>
      <Field label="Contraseña nueva" htmlFor="change-new-password" error={errors.newPassword}>
        <PasswordInput ref={refs.newPassword} id="change-new-password" name="new-password" autoComplete="new-password" enterKeyHint="next" maxLength={PASSWORD_MAX} capsLockWarning value={next}
          aria-invalid={!!errors.newPassword} aria-describedby={['change-rules', errors.newPassword ? 'change-new-password-error' : null].filter(Boolean).join(' ')}
          onChange={(e) => { setNext(e.currentTarget.value); clear('newPassword')(); }} />
      </Field>
      <Field label="Repite la contraseña nueva" htmlFor="change-confirm-password" error={errors.confirm}>
        <PasswordInput ref={refs.confirm} id="change-confirm-password" name="confirm-password" autoComplete="new-password" enterKeyHint="go" maxLength={PASSWORD_MAX} value={confirm}
          aria-invalid={!!errors.confirm} aria-describedby={['change-rules', errors.confirm ? 'change-confirm-password-error' : null].filter(Boolean).join(' ')}
          onChange={(e) => { setConfirm(e.currentTarget.value); clear('confirm')(); }} />
      </Field>
      <PasswordRequirements id="change-rules" password={next} confirm={confirm} />
      <Button type="submit" loading={busy}><KeyRound aria-hidden="true" /> {busy ? 'Guardando…' : 'Cambiar contraseña'}</Button>
    </form>
  );
}
