import * as React from 'react';
import { cn } from '@/lib/utils';

/** Tarjeta de pantallas públicas de acceso (login, registro): título, subtítulo y contenido. Los `<a>` del pie van en `footer`. */
export function AuthCard({ title, subtitle, children, footer, className }: { title: string; subtitle?: string; children: React.ReactNode; footer?: React.ReactNode; className?: string }) {
  return (
    <div className={cn('mx-auto w-full px-4 py-8 sm:py-12', className ?? 'max-w-md')}>
      <div className="rounded-card border border-steel-200 bg-surface p-6 shadow-sm sm:p-8">
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">{title}</h1>
        {subtitle && <p className="mb-6 mt-1.5 text-sm text-ink-500">{subtitle}</p>}
        {children}
      </div>
      {footer && <p className="mt-5 text-center text-sm text-ink-500">{footer}</p>}
    </div>
  );
}
