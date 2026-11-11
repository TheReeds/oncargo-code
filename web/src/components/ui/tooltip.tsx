import * as React from 'react';
import * as T from '@radix-ui/react-tooltip';

/** Ayuda breve al pasar el mouse o enfocar. No pongas información esencial solo aquí (en móvil no hay hover). */
export function Tooltip({ content, children, side = 'top' }: { content: React.ReactNode; children: React.ReactNode; side?: 'top' | 'bottom' | 'left' | 'right' }) {
  return (
    <T.Provider delayDuration={200}>
      <T.Root>
        <T.Trigger asChild>{children}</T.Trigger>
        <T.Portal>
          <T.Content side={side} sideOffset={6} className="z-[80] max-w-xs rounded-md bg-chrome px-2.5 py-1.5 text-xs text-white shadow-lg ring-1 ring-white/10">
            {content}
            <T.Arrow className="fill-chrome" />
          </T.Content>
        </T.Portal>
      </T.Root>
    </T.Provider>
  );
}
