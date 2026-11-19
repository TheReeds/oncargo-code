import { useState } from 'react';
import { actions } from 'astro:actions';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { toFormErrors } from '@/lib/action-error';

/**
 * Tras «Ya existe una organización con ese RUC» (ORG-002): pedir unirse. El correo va al administrador de esa empresa,
 * que decide si te invita. Toma el nombre y el correo del formulario de registro.
 */
export default function RequestOrgAccess({ ruc }: { ruc: string }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  function start() {
    setName((document.getElementById('fullName') as HTMLInputElement | null)?.value ?? '');
    setEmail((document.getElementById('email') as HTMLInputElement | null)?.value ?? '');
    setErrors({}); setFormError(null); setOpen(true);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    const { error } = await actions.orgAccess.request({ ruc, fullName: name, email });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, { fullName: 'fullName', email: 'email' });
      setErrors(r.errors);
      setFormError(r.formError ?? null);
      return;
    }
    setOpen(false);
    setSent(true);
  }

  if (sent) {
    return <Alert tone="success" className="mt-2" title="Pedido enviado">Le avisamos al administrador de esa empresa. Si te conoce, te enviará una invitación.</Alert>;
  }
  return (
    <>
      <p className="mt-2 text-sm text-ink-700">¿Trabajas en esa empresa? <Button type="button" variant="link" className="h-auto p-0 align-baseline" onClick={start}>Pedir acceso a la empresa</Button></p>
      <Dialog open={open} onOpenChange={(o) => !busy && setOpen(o)} title="Pedir acceso a la empresa" description="Le enviamos tu nombre y correo a su administrador. Él decide si te invita.">
        <form onSubmit={submit} method="post" noValidate className="space-y-4">
          <Field label="Tu nombre" htmlFor="ora-name" error={errors.fullName} required>
            <Input id="ora-name" value={name} maxLength={150} autoComplete="name" onChange={(e) => setName(e.target.value)} aria-invalid={!!errors.fullName} />
          </Field>
          <Field label="Tu correo" htmlFor="ora-email" error={errors.email} required>
            <Input id="ora-email" type="email" value={email} maxLength={254} autoComplete="email" onChange={(e) => setEmail(e.target.value)} aria-invalid={!!errors.email} />
          </Field>
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={busy}>Volver</Button>
            <Button type="submit" loading={busy}>{busy ? 'Enviando…' : 'Enviar pedido'}</Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}
