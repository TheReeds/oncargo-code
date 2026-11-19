import { useEffect, useId, useState } from 'react';
import { actions } from 'astro:actions';
import { KeyRound } from 'lucide-react';
import { Dialog } from '@/components/ui/dialog';
import { Button, type ButtonProps } from '@/components/ui/button';
import { Field, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { PERMISSION_LABEL, REQUEST_ACCESS_EVENT, roleFor, roleLabel, type Permission } from '@/lib/permissions';
import { useRole } from '@/lib/permissions/react';

/**
 * Diálogo «Pedir acceso»: quien no tiene un permiso (típicamente un Lector) se lo pide a los administradores de su
 * empresa. Envía `POST /organizations/me/access-requests`; cada ADMIN recibe un aviso para cambiarle el rol.
 */
export function RequestAccessDialog({ open, onOpenChange, permission = 'OPERATE' }: { open: boolean; onOpenChange: (o: boolean) => void; permission?: Permission }) {
  const role = useRole();
  const id = useId();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (open) { setError(null); } }, [open]);

  async function submit() {
    setBusy(true);
    setError(null);
    const { data, error: err } = await actions.access.request({ permission, reason: reason.trim() || undefined });
    setBusy(false);
    if (err) { setError(splitActionError(err).message); return; }
    const n = data?.notifiedAdmins ?? 0;
    toast.success('Pedido enviado', n === 1 ? 'Avisamos al administrador de tu empresa.' : `Avisamos a los ${n} administradores de tu empresa.`);
    setReason('');
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title="Pedir acceso"
      description={`Tu rol es ${roleLabel(role ?? 'VIEWER')}. Avisaremos a los administradores de tu empresa para que te asignen el rol ${roleLabel(roleFor(permission))}.`}
      footer={<><Button variant="ghost" onClick={() => onOpenChange(false)}>Cancelar</Button><Button onClick={submit} loading={busy}><KeyRound /> Enviar pedido</Button></>}>
      <div className="space-y-4">
        <p className="rounded-input bg-steel-100 px-3 py-2 text-sm text-ink-700"><span className="font-medium text-ink-900">Permiso que pides:</span> {PERMISSION_LABEL[permission]}</p>
        <Field label="Motivo (opcional)" htmlFor={`${id}-reason`} hint="Lo verán los administradores en su aviso.">
          <Textarea id={`${id}-reason`} value={reason} maxLength={300} rows={3} onChange={(e) => setReason(e.target.value)} placeholder="Ej. Desde esta semana publico las cargas de la planta de Arequipa." />
        </Field>
        {error && <Alert tone="error">{error}</Alert>}
      </div>
    </Dialog>
  );
}

/** Botón que abre el diálogo. */
export function RequestAccessButton({ permission = 'OPERATE', label = 'Pedir acceso', ...props }: { permission?: Permission; label?: string } & Omit<ButtonProps, 'onClick'>) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="outline" size="sm" {...props} onClick={() => setOpen(true)}><KeyRound /> {label}</Button>
      <RequestAccessDialog open={open} onOpenChange={setOpen} permission={permission} />
    </>
  );
}

/** Montado una vez en `AppShell`: abre el diálogo al recibir `openRequestAccess()` de `lib/permissions` (p. ej. desde el toast de un 403). */
export default function RequestAccessHost() {
  const [state, setState] = useState<{ open: boolean; permission: Permission }>({ open: false, permission: 'OPERATE' });
  useEffect(() => {
    const on = (e: Event) => setState({ open: true, permission: ((e as CustomEvent).detail?.permission as Permission) ?? 'OPERATE' });
    window.addEventListener(REQUEST_ACCESS_EVENT, on);
    return () => window.removeEventListener(REQUEST_ACCESS_EVENT, on);
  }, []);
  return <RequestAccessDialog open={state.open} onOpenChange={(o) => setState((s) => ({ ...s, open: o }))} permission={state.permission} />;
}
