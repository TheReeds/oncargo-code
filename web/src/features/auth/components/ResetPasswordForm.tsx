import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { PasswordInput } from '@/components/ui/password-input';
import { Checkbox } from '@/components/ui/form-controls';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { toFormErrors } from '@/lib/action-error';
import { PasswordRequirements, checkPassword } from './PasswordRequirements';
import { PASSWORD_MAX } from '../password-rules';

type Errors = { newPassword?: string; confirm?: string; accept?: string };

/**
 * Define la contraseña con el enlace del correo. `purpose = RESET`: restablece; `INVITE`: activa la cuenta y exige aceptar la
 * política de privacidad (Ley 29733). El token ya fue validado en el servidor (`inspect`); aquí se quita de la URL para que no
 * quede en el historial ni se filtre por el `Referer`. Éxito: al login (la API no inicia sesión).
 */
export default function ResetPasswordForm({ token, purpose, organizationName }: { token: string; purpose: 'RESET' | 'INVITE'; organizationName?: string }) {
  const invite = purpose === 'INVITE';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [accepted, setAccepted] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const refs = { newPassword: useRef<HTMLInputElement>(null), confirm: useRef<HTMLInputElement>(null) };

  // El token no debe quedar en el historial ni en el `Referer`: se quita de la barra de direcciones en cuanto se leyó.
  useEffect(() => {
    try { window.history.replaceState(window.history.state, '', window.location.pathname); } catch { /* sin historial: no pasa nada */ }
  }, []);

  function validate(): Errors {
    const c = checkPassword(password, confirm);
    const e: Errors = {};
    if (!password) e.newPassword = 'Ingresa tu contraseña nueva.';
    else if (!c.length) e.newPassword = 'La contraseña debe tener entre 8 y 72 caracteres.';
    if (!confirm) e.confirm = 'Repite la contraseña nueva.';
    else if (password !== confirm) e.confirm = 'Las contraseñas no coinciden.';
    if (invite && !accepted) e.accept = 'Acepta la política de privacidad para activar tu cuenta.';
    return e;
  }

  async function onSubmit(ev: React.FormEvent<HTMLFormElement>) {
    ev.preventDefault();
    if (busy) return;
    setFormError(null);
    const e = validate();
    setErrors(e);
    const first = (['newPassword', 'confirm', 'accept'] as const).find((k) => e[k]);
    if (first) { if (first === 'accept') document.getElementById('accept-privacy')?.focus(); else refs[first].current?.focus(); return; }
    setBusy(true);
    let res;
    try {
      res = await actions.password.reset({ token, newPassword: password, acceptPrivacyPolicy: invite ? true : undefined });
    } catch {
      setFormError('No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    if (res.error) {
      const { errors: fieldErrors, formError: other, failure } = toFormErrors(res.error, { newPassword: 'newPassword', acceptPrivacyPolicy: 'accept', token: 'token' });
      // `AUTH-005`: el enlace venció o ya se usó mientras se llenaba el formulario.
      if (failure.code === 'LOGYX-AUTH-005') { window.location.href = '/restablecer'; return; }
      setErrors({ ...fieldErrors } as Errors);
      setFormError(other);
      setBusy(false);
      return;
    }
    window.location.href = `/login?${invite ? 'activada' : 'restablecida'}=1`;
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {formError && <Alert tone="error">{formError}</Alert>}
      <Field label="Contraseña nueva" htmlFor="new-password" error={errors.newPassword}>
        <PasswordInput
          ref={refs.newPassword}
          id="new-password"
          name="new-password"
          autoComplete="new-password"
          enterKeyHint="next"
          maxLength={PASSWORD_MAX}
          capsLockWarning
          value={password}
          aria-invalid={!!errors.newPassword}
          aria-describedby={['password-rules', errors.newPassword ? 'new-password-error' : null].filter(Boolean).join(' ')}
          onChange={(e) => { setPassword(e.currentTarget.value); setFormError(null); if (errors.newPassword) setErrors((p) => ({ ...p, newPassword: undefined })); }}
        />
      </Field>
      <Field label="Repite la contraseña nueva" htmlFor="confirm-password" error={errors.confirm}>
        <PasswordInput
          ref={refs.confirm}
          id="confirm-password"
          name="confirm-password"
          autoComplete="new-password"
          enterKeyHint={invite ? 'next' : 'go'}
          maxLength={PASSWORD_MAX}
          value={confirm}
          aria-invalid={!!errors.confirm}
          aria-describedby={['password-rules', errors.confirm ? 'confirm-password-error' : null].filter(Boolean).join(' ')}
          onChange={(e) => { setConfirm(e.currentTarget.value); setFormError(null); if (errors.confirm) setErrors((p) => ({ ...p, confirm: undefined })); }}
        />
      </Field>
      <PasswordRequirements id="password-rules" password={password} confirm={confirm} />

      {invite && (
        <div className="rounded-input border border-steel-200 bg-steel-100/60 p-3">
          <div className="flex items-start gap-2.5">
            <Checkbox
              id="accept-privacy"
              checked={accepted}
              onCheckedChange={(c) => { setAccepted(c === true); if (c === true) setErrors((p) => ({ ...p, accept: undefined })); }}
              aria-invalid={!!errors.accept}
              aria-describedby={errors.accept ? 'accept-privacy-error' : undefined}
              className="mt-0.5"
            />
            <label htmlFor="accept-privacy" className="cursor-pointer text-[13px] leading-snug text-ink-700">
              Acepto la <a href="/privacidad" target="_blank" rel="noopener" className="font-medium text-petrol-700 underline underline-offset-2">política de privacidad</a> y el tratamiento de mis datos personales (Ley 29733){organizationName ? <> como usuario de {organizationName}</> : null}.
            </label>
          </div>
          {errors.accept && <p id="accept-privacy-error" role="alert" className="mt-2 pl-7 text-xs text-red-700">{errors.accept}</p>}
        </div>
      )}

      <Button type="submit" size="lg" className="w-full" loading={busy}>
        {busy ? (invite ? 'Activando cuenta…' : 'Guardando…') : invite ? 'Activar cuenta' : 'Guardar contraseña'}
      </Button>
      <p className="sr-only" role="status" aria-live="polite">{busy ? 'Guardando tu contraseña…' : ''}</p>
    </form>
  );
}
