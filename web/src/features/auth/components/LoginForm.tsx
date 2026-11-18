import { useEffect, useRef, useState } from 'react';
import { safeNext } from '@/lib/safe-redirect';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';

type Errors = { email?: string; password?: string };

const EMAIL_RE = /^\S+@\S+\.\S+$/;
const emailError = (v: string) => (!v ? 'Ingresa tu correo electrónico.' : !EMAIL_RE.test(v) ? 'Revisa el correo: debe tener el formato nombre@empresa.pe.' : undefined);

/** Cuentas demo del perfil dev del backend. Solo se muestran en desarrollo (`demo`). */
const DEMO = [
  { label: 'PYME', email: 'pyme@logyx.pe' },
  { label: 'Transportista', email: 'carrier@logyx.pe' },
  { label: 'Operador', email: 'operator@logyx.pe' },
  { label: 'Conductor', email: 'driver@logyx.pe' },
];

export default function LoginForm({ next, expired, demo }: { next?: string; expired?: boolean; demo?: boolean }) {
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  // Mensaje tras restablecer la contraseña o activar la cuenta (`/login?restablecida=1|activada=1`), solo en el cliente.
  const [notice, setNotice] = useState<string | null>(null);
  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    if (q.has('restablecida')) setNotice('Contraseña actualizada. Ingresa con tu contraseña nueva.');
    else if (q.has('activada')) setNotice('Tu cuenta quedó activa. Ingresa con la contraseña que acabas de crear.');
  }, []);

  /** Valida un campo solo cuando el usuario ya lo editó y salió de él (no al enfocar ni mientras escribe). */
  function onBlurEmail(e: React.FocusEvent<HTMLInputElement>) {
    const v = e.currentTarget.value.trim();
    if (v) setErrors((p) => ({ ...p, email: emailError(v) }));
  }
  function clearOnFix(field: keyof Errors, ok: (v: string) => boolean) {
    return (e: React.ChangeEvent<HTMLInputElement>) => {
      setError(null);
      if (errors[field] && ok(e.currentTarget.value.trim())) setErrors((p) => ({ ...p, [field]: undefined }));
    };
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    setError(null);
    const f = new FormData(e.currentTarget);
    const email = String(f.get('email') ?? '').trim();
    const password = String(f.get('password') ?? '');
    const local: Errors = { email: emailError(email), password: password ? undefined : 'Ingresa tu contraseña.' };
    setErrors(local);
    if (local.email || local.password) {
      (local.email ? emailRef : passwordRef).current?.focus();
      return;
    }

    setBusy(true);
    let res;
    try {
      res = await actions.auth.login({ email, password });
    } catch {
      setError('No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    const { data, error } = res;
    if (error) {
      setError(error.message);
      setBusy(false);
      // Deja la contraseña seleccionada: reintentar es escribir encima.
      requestAnimationFrame(() => passwordRef.current?.select());
      return;
    }
    window.location.href = safeNext(next, data.home, window.location.origin);
  }

  function fillDemo(email: string) {
    if (emailRef.current) emailRef.current.value = email;
    if (passwordRef.current) passwordRef.current.value = 'Logyx2026!';
    setErrors({});
    setError(null);
    passwordRef.current?.focus();
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {notice && <Alert tone="success">{notice}</Alert>}
      {expired && <Alert tone="info">Tu sesión se cerró o venció. Ingresa de nuevo para continuar.</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="Correo electrónico" htmlFor="email" error={errors.email}>
        <Input
          ref={emailRef}
          id="email"
          name="email"
          type="email"
          // `username` es lo que reconocen los gestores de contraseñas aunque el campo sea un correo.
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          enterKeyHint="next"
          required
          placeholder="nombre@empresa.pe"
          aria-invalid={!!errors.email}
          aria-describedby={errors.email ? 'email-error' : undefined}
          onBlur={onBlurEmail}
          onChange={clearOnFix('email', (v) => !emailError(v))}
        />
      </Field>
      <Field label="Contraseña" htmlFor="password" error={errors.password}>
        <PasswordInput
          ref={passwordRef}
          id="password"
          name="password"
          autoComplete="current-password"
          enterKeyHint="go"
          required
          capsLockWarning
          aria-invalid={!!errors.password}
          aria-describedby={errors.password ? 'password-error' : undefined}
          onChange={clearOnFix('password', (v) => !!v)}
        />
      </Field>
      <p className="-mt-1 text-right text-sm"><a href="/olvide" className="font-medium text-petrol-700 underline underline-offset-2">¿Olvidaste tu contraseña?</a></p>
      <Button type="submit" size="lg" className="w-full" loading={busy}>
        {busy ? 'Ingresando…' : 'Ingresar'}
      </Button>
      <p className="sr-only" role="status" aria-live="polite">{busy ? 'Ingresando a tu cuenta…' : ''}</p>

      {demo && (
        <fieldset className="rounded-input border border-dashed border-steel-200 bg-steel-100/60 p-3">
          <legend className="px-1.5 text-xs font-medium text-ink-500">Cuentas demo · solo desarrollo</legend>
          <div className="flex flex-wrap gap-1.5">
            {DEMO.map((d) => (
              <button key={d.email} type="button" onClick={() => fillDemo(d.email)} title={d.email} className="rounded-full border border-steel-200 bg-surface px-2.5 py-1 text-xs font-medium text-ink-700 transition-colors hover:border-petrol-700 hover:text-petrol-700">
                {d.label}
              </button>
            ))}
          </div>
        </fieldset>
      )}
    </form>
  );
}
