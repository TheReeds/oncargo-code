import * as React from 'react';
import * as T from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

export const Tabs = T.Root;
export function TabsList({ className, ...p }: React.ComponentPropsWithoutRef<typeof T.List>) {
  return <T.List className={cn('flex gap-1 overflow-x-auto border-b border-steel-200', className)} {...p} />;
}
export function TabsTrigger({ className, ...p }: React.ComponentPropsWithoutRef<typeof T.Trigger>) {
  return <T.Trigger className={cn('-mb-px whitespace-nowrap border-b-2 border-transparent px-3 py-2 text-sm font-medium text-ink-500 transition-colors hover:text-ink-900 data-[state=active]:border-petrol-700 data-[state=active]:text-petrol-700', className)} {...p} />;
}
export function TabsContent({ className, ...p }: React.ComponentPropsWithoutRef<typeof T.Content>) {
  return <T.Content className={cn('pt-4 focus-visible:outline-none', className)} {...p} />;
}
