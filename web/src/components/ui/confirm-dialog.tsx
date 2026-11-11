import * as React from 'react';
import * as AD from '@radix-ui/react-alert-dialog';
import { AlertTriangle, HelpCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import { buttonVariants } from './button';

export type ConfirmOptions = {
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** `destructive` para acciones irreversibles (cancelar, rechazar, eliminar). */
  tone?: 'default' | 'destructive';
};

/** Versión declarativa (controlada). Casi siempre es más cómodo usar `confirm()`. */
export function ConfirmDialog({ open, onOpenChange, onConfirm, title, description, confirmLabel = 'Confirmar', cancelLabel = 'Volver', tone = 'default' }: ConfirmOptions & { open: boolean; onOpenChange: (o: boolean) => void; onConfirm: () => void }) {
  const destructive = tone === 'destructive';
  const Icon = destructive ? AlertTriangle : HelpCircle;
  return (
    <AD.Root open={open} onOpenChange={onOpenChange}>
      <AD.Portal>
        <AD.Overlay className="fixed inset-0 z-[70] bg-scrim" />
        <AD.Content className="fixed left-1/2 top-1/2 z-[70] w-[calc(100vw-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-card border border-steel-200 bg-surface p-5 shadow-xl focus:outline-none">
          <div className="flex gap-3">
            <span className={cn('flex size-10 shrink-0 items-center justify-center rounded-full', destructive ? 'bg-red-50 text-red-600' : 'bg-petrol-50 text-petrol-700')}><Icon className="size-5" aria-hidden="true" /></span>
            <div>
              <AD.Title className="text-base font-semibold text-ink-900">{title}</AD.Title>
              <AD.Description className={cn('mt-1 text-sm text-ink-700', !description && 'sr-only')}>{description ?? title}</AD.Description>
            </div>
          </div>
          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <AD.Cancel className={buttonVariants({ variant: 'outline' })}>{cancelLabel}</AD.Cancel>
            <AD.Action onClick={onConfirm} className={buttonVariants({ variant: destructive ? 'destructive-solid' : 'default' })}>{confirmLabel}</AD.Action>
          </div>
        </AD.Content>
      </AD.Portal>
    </AD.Root>
  );
}

type Pending = ConfirmOptions & { resolve: (ok: boolean) => void };
let push: ((p: Pending) => void) | null = null;

/**
 * API imperativa: `if (!(await confirm({ title: '¿Cancelar la solicitud?', tone: 'destructive', confirmLabel: 'Cancelar solicitud' }))) return;`
 * Requiere que <ConfirmHost /> esté montado (ya lo está dentro de <Overlays/> en Base.astro).
 */
export function confirm(opts: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    if (!push) return resolve(window.confirm(opts.title));
    push({ ...opts, resolve });
  });
}

export function ConfirmHost() {
  const [cur, setCur] = React.useState<Pending | null>(null);
  const [open, setOpen] = React.useState(false);
  React.useEffect(() => {
    push = (p) => { setCur(p); setOpen(true); };
    return () => { push = null; };
  }, []);
  const close = (ok: boolean) => { cur?.resolve(ok); setOpen(false); };
  if (!cur) return null;
  return <ConfirmDialog {...cur} open={open} onOpenChange={(o) => { if (!o && open) close(false); }} onConfirm={() => close(true)} />;
}
