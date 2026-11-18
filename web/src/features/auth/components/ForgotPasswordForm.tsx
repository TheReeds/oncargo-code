import { useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { MailCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';

const EMAIL_RE = /^\S+@\S+\.\S+$/;
const emailError = (v: string) => (!v ? 'Ingresa tu correo electrónico.' : !EMAIL_RE.test(v) ? 'Revisa el correo: debe tener el formato nombre@empresa.pe.' : undefined);

/** "¿Olvidaste tu contraseña?": pide el correo y muestra siempre la misma confirmación, exista o no la cuenta (sin enumeración). */
export default function ForgotPasswordForm({ initialEmail }: { initialEmail?: string }) {
  const [email, setEmail] = useState(initialEmail ?? '');
  const [error, setError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const ref = useRef<HTMLInputElement>(null);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    const value = email.trim();
    const err = emailError(value);
    setError(err);
    setFormError(null);
    if (err) { ref.current?.focus(); return; }
    setBusy(true);
    try {
      const { error: apiError } = await actions.password.forgot({ email: value });
      if (apiError) { setFormError(apiError.message); setBusy(false); return; }
    } catch {
      setFormError('No pudimos conectar con OnCargo. Revisa tu conexión e inténtalo de nuevo.');
      setBusy(false);
      return;
    }
    setSentTo(value);
    setBusy(false);
  }

  if (sentTo) {
    return (
      <div className="space-y-4" data-testid="forgot-sent">
        <div role="status" className="rounded-input border border-emerald-500/40 bg-emerald-50 px-4 py-4 text-sm text-emerald-700">
          <p className="flex items-center gap-2 font-semibold"><MailCheck className="size-4 shrink-0" aria-hidden="true" /> Revisa tu correo</p>
          <p className="mt-1.5 text-ink-700">Si <span className="font-medium text-ink-900">{sentTo}</span> tiene una cuenta en OnCargo, te enviamos un enlace para crear una contraseña nueva. Vence en 30 minutos.</p>
        </div>
        <ul className="list-disc space-y-1 pl-5 text-xs text-ink-500">
          <li>Si no lo ves, revisa la carpeta de correo no deseado.</li>
          <li>Cada enlace sirve una sola vez; si pides otro, el anterior deja de funcionar.</li>
        </ul>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setSentTo(null)}>Usar otro correo</Button>
          <Button asChild variant="ghost"><a href="/login">Volver a ingresar</a></Button>
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} method="post" className="space-y-4" noValidate>
      {formError && <Alert tone="error">{formError}</Alert>}
      <Field label="Correo electrónico" htmlFor="forgot-email" error={error} hint="Te enviaremos un enlace para crear una contraseña nueva.">
        <Input
          ref={ref}
          id="forgot-email"
          name="email"
          type="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          enterKeyHint="send"
          required
          placeholder="nombre@empresa.pe"
          value={email}
          aria-invalid={!!error}
          aria-describedby={error ? 'forgot-email-error' : 'forgot-email-hint'}
          onBlur={(e) => { const v = e.currentTarget.value.trim(); if (v) setError(emailError(v)); }}
          onChange={(e) => { setEmail(e.currentTarget.value); setFormError(null); if (error && !emailError(e.currentTarget.value.trim())) setError(undefined); }}
        />
      </Field>
      <Button type="submit" size="lg" className="w-full" loading={busy}>{busy ? 'Enviando enlace…' : 'Enviar enlace'}</Button>
      <p className="sr-only" role="status" aria-live="polite">{busy ? 'Enviando el enlace a tu correo…' : ''}</p>
    </form>
  );
}
