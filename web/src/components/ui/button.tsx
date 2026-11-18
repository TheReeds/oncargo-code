import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-input text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 aria-busy:cursor-progress [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-petrol-700 text-on-solid hover:bg-petrol-800',
        money: 'bg-emerald-700 text-on-solid hover:bg-emerald-700/90',
        outline: 'border border-steel-200 bg-surface text-ink-900 hover:bg-steel-100',
        ghost: 'text-ink-700 hover:bg-steel-100',
        destructive: 'border border-red-600/30 bg-surface text-red-600 hover:bg-red-50',
        'destructive-solid': 'bg-red-600 text-on-solid hover:bg-red-700',
        link: 'text-petrol-700 underline-offset-4 hover:underline',
      },
      size: { default: 'h-10 px-4', sm: 'h-8 px-3 text-[13px]', lg: 'h-11 px-6 text-base', icon: 'size-9' },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  /** Muestra spinner y bloquea el botón mientras se ejecuta una acción. */
  loading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, asChild, loading, disabled, children, ...props }, ref) => {
  if (asChild) return <Slot ref={ref} className={cn(buttonVariants({ variant, size }), className)} {...props}>{children}</Slot>;
  return (
    <button ref={ref} className={cn(buttonVariants({ variant, size }), className)} disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      {loading && <Loader2 className="animate-spin" aria-hidden="true" />}
      {children}
    </button>
  );
});
Button.displayName = 'Button';
