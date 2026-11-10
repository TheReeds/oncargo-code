import * as React from 'react';
import * as M from '@radix-ui/react-dropdown-menu';
import { cn } from '@/lib/utils';

export const DropdownMenu = M.Root;
export const DropdownMenuTrigger = M.Trigger;
export function DropdownMenuContent({ className, align = 'end', sideOffset = 4, ...p }: React.ComponentPropsWithoutRef<typeof M.Content>) {
  return (
    <M.Portal>
      <M.Content align={align} sideOffset={sideOffset} className={cn('z-[60] min-w-44 rounded-input border border-steel-200 bg-surface p-1 shadow-lg', className)} {...p} />
    </M.Portal>
  );
}
export function DropdownMenuItem({ className, destructive, ...p }: React.ComponentPropsWithoutRef<typeof M.Item> & { destructive?: boolean }) {
  return <M.Item className={cn('flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm outline-none data-[disabled]:opacity-50 [&_svg]:size-4', destructive ? 'text-red-600 data-[highlighted]:bg-red-50' : 'text-ink-900 data-[highlighted]:bg-petrol-50 data-[highlighted]:text-petrol-800', className)} {...p} />;
}
export function DropdownMenuLabel({ className, ...p }: React.ComponentPropsWithoutRef<typeof M.Label>) {
  return <M.Label className={cn('px-2.5 py-1.5 text-xs font-semibold uppercase tracking-wide text-ink-500', className)} {...p} />;
}
export function DropdownMenuSeparator({ className, ...p }: React.ComponentPropsWithoutRef<typeof M.Separator>) {
  return <M.Separator className={cn('my-1 h-px bg-steel-200', className)} {...p} />;
}
