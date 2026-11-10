import { cn } from '@/lib/utils';
export const Kbd = ({ className, children }: { className?: string; children: React.ReactNode }) => (
  <kbd className={cn('rounded border border-steel-200 bg-steel-100 px-1.5 py-0.5 font-mono text-[11px] text-ink-700', className)}>{children}</kbd>
);
