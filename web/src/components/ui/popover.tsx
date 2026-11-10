import * as React from 'react';
import * as P from '@radix-ui/react-popover';
import { cn } from '@/lib/utils';

export const Popover = P.Root;
export const PopoverTrigger = P.Trigger;
export const PopoverAnchor = P.Anchor;
export function PopoverContent({ className, align = 'start', sideOffset = 4, ...p }: React.ComponentPropsWithoutRef<typeof P.Content>) {
  return (
    <P.Portal>
      <P.Content align={align} sideOffset={sideOffset} className={cn('z-[60] rounded-input border border-steel-200 bg-surface shadow-lg outline-none', className)} {...p} />
    </P.Portal>
  );
}
