import { useState } from 'react';
import * as AD from '@radix-ui/react-alert-dialog';
import { actions } from 'astro:actions';
import { FileText } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';

/**
 * Pide aceptar la versión vigente de los Términos y condiciones (V24, roadmap 2.17) cuando `GET /auth/me` trae
 * `termsAccepted = false` (la versión cambió o la cuenta nunca los aceptó). Diálogo modal que no se cierra con Esc ni
 * clic fuera: solo aceptando. La API no se bloquea mientras tanto (el diálogo es de la web). No se monta para
 * conductores ni operadores (`AppShell`). Si la versión cambió otra vez mientras estaba abierto (409 `AUTH-012`),
 * recarga para mostrar la nueva.
 */
export default function TermsGate({ version }: { version: string }) {
  const [open, setOpen] = useState(true);
  const [checked, setChecked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function accept() {
    if (!checked) { setError('Marca la casilla para aceptar los términos.'); return; }
    setBusy(true);
    setError(null);
    const { error: err } = await actions.auth.acceptTerms({ termsVersion: version });
    setBusy(false);
    if (err) {
      const { message, code } = splitActionError(err);
      if (code === 'LOGYX-AUTH-012') { window.location.reload(); return; }
      setError(message);
      return;
    }
    setOpen(false);
    toast.success('Términos aceptados');
  }

  return (
    <AD.Root open={open}>
      <AD.Portal>
        <AD.Overlay className="fixed inset-0 z-[70] bg-scrim" />
        <AD.Content
          onEscapeKeyDown={(e) => e.preventDefault()}
          className="fixed left-1/2 top-1/2 z-[70] max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 overflow-y-auto overscroll-contain rounded-card border border-steel-200 bg-surface p-5 shadow-xl focus:outline-none"
          data-testid="terms-gate"
        >
          <div className="flex gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-petrol-50 text-petrol-700"><FileText className="size-5" aria-hidden="true" /></span>
            <div>
              <AD.Title className="text-base font-semibold text-ink-900">Actualizamos los Términos y condiciones</AD.Title>
              <AD.Description className="mt-1 text-sm text-ink-700">
                Para seguir usando OnCargo, léelos y acéptalos. Es la versión {version}.
              </AD.Description>
            </div>
          </div>
          <p className="mt-4 text-sm">
            <a href="/terminos" target="_blank" rel="noopener" className="font-medium text-petrol-700 underline underline-offset-2">Leer los Términos y condiciones</a>
            <span className="text-ink-500"> (se abre en otra pestaña)</span>
          </p>
          <div className="mt-4 flex items-start gap-2.5 rounded-input border border-steel-200 bg-steel-100/60 p-3">
            <Checkbox id="terms-gate-accept" checked={checked} onCheckedChange={(c) => { setChecked(c === true); setError(null); }} aria-invalid={!!error} aria-describedby={error ? 'terms-gate-error' : undefined} className="mt-0.5" />
            <label htmlFor="terms-gate-accept" className="cursor-pointer text-[13px] leading-snug text-ink-700">Leí y acepto los Términos y condiciones</label>
          </div>
          {error && <div className="mt-3" id="terms-gate-error"><Alert tone="error">{error}</Alert></div>}
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <form method="post" action="/logout" className="contents"><Button type="submit" variant="ghost">Cerrar sesión</Button></form>
            <Button onClick={accept} loading={busy}>{busy ? 'Aceptando…' : 'Aceptar términos'}</Button>
          </div>
        </AD.Content>
      </AD.Portal>
    </AD.Root>
  );
}
