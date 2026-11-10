import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';
import { formatCountdown } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Cuenta regresiva de subasta. Ámbar cuando faltan < 5 min. */
export function Countdown({ closesAt, className }: { closesAt: string | null | undefined; className?: string }) {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    setNow(Date.now());
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);
  if (!closesAt) return <span className="text-ink-500">—</span>;
  const left = new Date(closesAt).getTime() - (now ?? Date.now());
  const urgent = left > 0 && left < 5 * 60_000;
  return (
    <span suppressHydrationWarning className={cn('inline-flex items-center gap-1 font-mono text-sm tabular-nums', left <= 0 ? 'text-ink-500' : urgent ? 'font-semibold text-amber-700' : 'text-ink-700', className)}>
      <Clock className="size-3.5" aria-hidden="true" />
      {now === null ? '…' : formatCountdown(left)}
    </span>
  );
}
