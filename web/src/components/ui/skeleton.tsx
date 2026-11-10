import { cn } from '@/lib/utils';
export const Skeleton = ({ className }: { className?: string }) => <div className={cn('animate-pulse rounded-input bg-steel-200', className)} />;
