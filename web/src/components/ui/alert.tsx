import * as React from 'react';
import { AlertCircle, CheckCircle2, Info } from 'lucide-react';
import { cn } from '@/lib/utils';

const tones = {
  error: 'border-red-600/30 bg-red-50 text-red-600',
  success: 'border-emerald-500/40 bg-emerald-50 text-emerald-700',
  info: 'border-petrol-700/20 bg-petrol-50 text-petrol-700',
};
export function Alert({ tone = 'info', className, children }: { tone?: keyof typeof tones; className?: string; children: React.ReactNode }) {
  const Icon = tone === 'error' ? AlertCircle : tone === 'success' ? CheckCircle2 : Info;
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('flex items-start gap-2 rounded-input border px-3 py-2.5 text-sm', tones[tone], className)}>
      <Icon className="mt-0.5 size-4 shrink-0" />
      <div>{children}</div>
    </div>
  );
}
