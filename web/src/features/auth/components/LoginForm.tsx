import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';

export default function LoginForm({ next, expired }: { next?: string; expired?: boolean }) {
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    const f = new FormData(e.currentTarget);
    const email = String(f.get('email') ?? '').trim();
    const password = String(f.get('password') ?? '');
    const local: typeof errors = {};
    if (!email) local.email = 'Ingresa tu correo electrónico.';
    else if (!/^\S+@\S+\.\S+$/.test(email)) local.email = 'Revisa el correo: debe tener el formato nombre@empresa.pe.';
    if (!password) local.password = 'Ingresa tu contraseña.';
    setErrors(local);
    if (Object.keys(local).length) return;

    setBusy(true);
    let res;
    try {
      res = await actions.auth.login({ email, password });
    } catch {
      toast.error('No pudimos conectar con LOGYX. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    const { data, error } = res;
    if (error) {
      setError(error.message);
      setBusy(false);
      return;
    }
    window.location.href = next && next.startsWith('/') && !next.startsWith('//') ? next : data.home;
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {expired && <Alert tone="info">Tu sesión venció. Ingresa de nuevo para continuar.</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="Correo electrónico" htmlFor="email" error={errors.email}>
        <Input id="email" name="email" type="email" autoComplete="email" required placeholder="nombre@empresa.pe" aria-invalid={!!errors.email} />
      </Field>
      <Field label="Contraseña" htmlFor="password" error={errors.password}>
        <PasswordInput id="password" name="password" autoComplete="current-password" required aria-invalid={!!errors.password} />
      </Field>
      <Button type="submit" size="lg" className="w-full" loading={busy}>
        {busy ? 'Ingresando…' : 'Ingresar'}
      </Button>
    </form>
  );
}
