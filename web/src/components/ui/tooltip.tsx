import * as React from 'react';
import * as T from '@radix-ui/react-tooltip';

/** Ayuda breve al pasar el mouse o enfocar. No pongas información esencial solo aquí (en móvil no hay hover). */
export function Tooltip({ content, children, side = 'top' }: { content: React.ReactNode; children: React.ReactNode; side?: 'top' | 'bottom' | 'left' | 'right' }) {
  return (
    <T.Provider delayDuration={200}>
      <T.Root>
        <T.Trigger asChild>{children}</T.Trigger>
        <T.Portal>
          <T.Content side={side} sideOffset={6} className="z-[80] max-w-xs rounded-md bg-ink-900 px-2.5 py-1.5 text-xs text-white shadow-lg">
            {content}
            <T.Arrow className="fill-ink-900" />
          </T.Content>
        </T.Portal>
      </T.Root>
    </T.Provider>
  );
}
