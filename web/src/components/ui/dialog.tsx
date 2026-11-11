import * as React from 'react';
import * as D from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Diálogo modal para formularios o detalle corto. Para confirmar acciones usa ConfirmDialog/confirm(). */
export function Dialog({ open, onOpenChange, title, description, children, footer, className }: { open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: string; children?: React.ReactNode; footer?: React.ReactNode; className?: string }) {
  return (
    <D.Root open={open} onOpenChange={onOpenChange}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-50 bg-scrim" />
        <D.Content className={cn('fixed left-1/2 top-1/2 z-50 w-[calc(100vw-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 rounded-card border border-steel-200 bg-surface shadow-xl focus:outline-none', className)}>
          <div className="flex items-start justify-between gap-4 px-5 pt-5">
            <div>
              <D.Title className="text-base font-semibold text-ink-900">{title}</D.Title>
              <D.Description className={cn('mt-1 text-sm text-ink-500', !description && 'sr-only')}>{description ?? title}</D.Description>
            </div>
            <D.Close className="rounded-input p-1.5 text-ink-500 hover:bg-steel-100" aria-label="Cerrar"><X className="size-4" /></D.Close>
          </div>
          {children && <div className="px-5 py-4">{children}</div>}
          {footer && <div className="flex justify-end gap-2 border-t border-steel-200 px-5 py-3.5">{footer}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}
