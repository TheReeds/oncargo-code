import * as React from 'react';
import * as LabelPrimitive from '@radix-ui/react-label';
import { cn } from '@/lib/utils';

export function Label({ className, ...p }: React.ComponentPropsWithoutRef<typeof LabelPrimitive.Root>) {
  return <LabelPrimitive.Root className={cn('mb-1.5 block text-[13px] font-medium text-ink-700', className)} {...p} />;
}

/** Etiqueta + control + ayuda/error. El error se anuncia con role="alert". El control debe usar `id={htmlFor}`. */
export function Field({ label, htmlFor, hint, error, required, children, className }: { label: string; htmlFor: string; hint?: string; error?: string | null; required?: boolean; children: React.ReactNode; className?: string }) {
  return (
    <div className={className}>
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="ml-0.5 text-red-600" aria-hidden="true">*</span>}
      </Label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-ink-500">{hint}</p>}
      {error && (
        <p className="mt-1 text-xs text-red-600" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
