import * as React from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import { cn } from '@/lib/utils';

const tones = {
  error: 'border-red-600/30 bg-red-50 text-red-700',
  success: 'border-emerald-500/40 bg-emerald-50 text-emerald-700',
  info: 'border-petrol-700/20 bg-petrol-50 text-petrol-700',
  warning: 'border-amber-500/40 bg-amber-50 text-amber-700',
};
const icons = { error: AlertCircle, success: CheckCircle2, info: Info, warning: TriangleAlert };

/** Mensaje persistente en la página (banner). Para feedback efímero tras una acción usa `toast`. */
export function Alert({ tone = 'info', title, className, children, action }: { tone?: keyof typeof tones; title?: string; className?: string; children?: React.ReactNode; action?: React.ReactNode }) {
  const Icon = icons[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('flex items-start gap-2 rounded-input border px-3 py-2.5 text-sm', tones[tone], className)}>
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={title ? 'mt-0.5' : ''}>{children}</div>}
      </div>
      {action}
    </div>
  );
}
