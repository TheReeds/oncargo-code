import { useState } from 'react';
import { actions } from 'astro:actions';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';

export default function LoginForm({ next, expired }: { next?: string; expired?: boolean }) {
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [show, setShow] = useState(false);
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
        <div className="relative">
          <Input id="password" name="password" type={show ? 'text' : 'password'} autoComplete="current-password" required className="pr-11" aria-invalid={!!errors.password} />
          <button type="button" onClick={() => setShow((s) => !s)} aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={show} className="absolute right-1 top-1 grid size-8 place-items-center rounded-md text-ink-500 hover:bg-steel-100 hover:text-ink-900">
            {show ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
          </button>
        </div>
      </Field>
      <Button type="submit" size="lg" className="w-full" loading={busy}>
        {busy ? 'Ingresando…' : 'Ingresar'}
      </Button>
    </form>
  );
}
