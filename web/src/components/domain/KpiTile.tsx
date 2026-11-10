import type { ComponentType, ReactNode } from 'react';
import { Card } from '@/components/ui/card';

export function KpiTile({ label, value, context, icon: Icon, href }: { label: string; value: ReactNode; context?: string; icon?: ComponentType<{ size?: number }>; href?: string }) {
  const body = (
    <Card className="h-full p-4 transition-colors hover:border-petrol-700/40">
      <div className="flex items-center justify-between text-ink-500">
        <span className="text-[13px] font-medium">{label}</span>
        {Icon && <Icon size={16} />}
      </div>
      <div className="mt-2 font-mono text-3xl font-semibold tabular-nums text-ink-900">{value}</div>
      {context && <div className="mt-1 text-xs text-ink-500">{context}</div>}
    </Card>
  );
  return href ? <a href={href} className="block rounded-card">{body}</a> : body;
}
