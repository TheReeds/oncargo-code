import * as React from 'react';
import * as C from '@radix-ui/react-checkbox';
import * as R from '@radix-ui/react-radio-group';
import * as S from '@radix-ui/react-switch';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Checkbox({ className, ...p }: React.ComponentPropsWithoutRef<typeof C.Root>) {
  return (
    <C.Root className={cn('flex size-[18px] shrink-0 items-center justify-center rounded border border-ink-400 bg-surface transition-colors data-[state=checked]:border-petrol-700 data-[state=checked]:bg-petrol-700 disabled:opacity-50 aria-[invalid=true]:border-red-600', className)} {...p}>
      <C.Indicator><Check className="size-3.5 text-white" strokeWidth={3} aria-hidden="true" /></C.Indicator>
    </C.Root>
  );
}

export const RadioGroup = ({ className, ...p }: React.ComponentPropsWithoutRef<typeof R.Root>) => <R.Root className={cn('grid gap-2', className)} {...p} />;
export function RadioGroupItem({ className, ...p }: React.ComponentPropsWithoutRef<typeof R.Item>) {
  return (
    <R.Item className={cn('flex size-[18px] shrink-0 items-center justify-center rounded-full border border-ink-400 bg-surface data-[state=checked]:border-petrol-700 disabled:opacity-50', className)} {...p}>
      <R.Indicator className="size-2.5 rounded-full bg-petrol-700" />
    </R.Item>
  );
}

export function Switch({ className, ...p }: React.ComponentPropsWithoutRef<typeof S.Root>) {
  return (
    <S.Root className={cn('relative h-5 w-9 shrink-0 rounded-full bg-ink-400 transition-colors data-[state=checked]:bg-petrol-700 disabled:opacity-50', className)} {...p}>
      <S.Thumb className="block size-4 translate-x-0.5 rounded-full bg-white shadow transition-transform data-[state=checked]:translate-x-[18px]" />
    </S.Root>
  );
}

/** Etiqueta en línea para checkbox/radio/switch (a la derecha del control). */
export function InlineLabel({ htmlFor, children }: { htmlFor: string; children: React.ReactNode }) {
  return <label htmlFor={htmlFor} className="cursor-pointer text-sm text-ink-900">{children}</label>;
}
