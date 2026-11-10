import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';

export default function LoginForm({ next, expired }: { next?: string; expired?: boolean }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    const f = new FormData(e.currentTarget);
    const { data, error } = await actions.auth.login({ email: String(f.get('email')), password: String(f.get('password')) });
    if (error) {
      setError(error.message);
      setBusy(false);
      return;
    }
    window.location.href = next && next.startsWith('/') && !next.startsWith('//') ? next : data.home;
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {expired && <Alert tone="info">Tu sesión expiró. Inicia sesión nuevamente.</Alert>}
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="Correo electrónico" htmlFor="email">
        <Input id="email" name="email" type="email" autoComplete="email" required placeholder="nombre@empresa.pe" />
      </Field>
      <Field label="Contraseña" htmlFor="password">
        <Input id="password" name="password" type="password" autoComplete="current-password" required />
      </Field>
      <Button type="submit" className="w-full" disabled={busy}>
        {busy ? 'Ingresando…' : 'Ingresar'}
      </Button>
    </form>
  );
}
