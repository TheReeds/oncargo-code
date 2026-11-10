import { cn } from '@/lib/utils';

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join('');

/** Iniciales sobre fondo petrol-50. Para empresas y personas. */
export function Avatar({ name, size = 'md', className }: { name: string; size?: 'sm' | 'md' | 'lg'; className?: string }) {
  return (
    <span aria-hidden="true" className={cn('inline-flex shrink-0 items-center justify-center rounded-full bg-petrol-50 font-semibold text-petrol-700', size === 'sm' ? 'size-7 text-[11px]' : size === 'lg' ? 'size-12 text-base' : 'size-9 text-xs', className)}>{initials(name)}</span>
  );
}
