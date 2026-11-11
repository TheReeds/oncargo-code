import * as React from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

/** Panel lateral (drawer) basado en Radix Dialog. */
export function Sheet({ open, onOpenChange, title, description, children }: { open: boolean; onOpenChange: (o: boolean) => void; title: string; description?: string; children: React.ReactNode }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-scrim" />
        <Dialog.Content className={cn('fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-surface shadow-xl focus:outline-none')}>
          <div className="flex items-start justify-between gap-4 border-b border-steel-200 px-5 py-4">
            <div>
              <Dialog.Title className="text-base font-semibold text-ink-900">{title}</Dialog.Title>
              <Dialog.Description className={cn('mt-0.5 text-sm text-ink-500', !description && 'sr-only')}>{description ?? title}</Dialog.Description>
            </div>
            <Dialog.Close className="rounded-input p-1.5 text-ink-500 hover:bg-steel-100" aria-label="Cerrar">
              <X className="size-4" />
            </Dialog.Close>
          </div>
          <div className="flex-1 overflow-y-auto">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
