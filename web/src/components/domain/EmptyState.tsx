import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';

export function TruckIllustration() {
  return (
    <svg width="96" height="64" viewBox="0 0 96 64" fill="none" aria-hidden="true">
      <rect x="4" y="12" width="52" height="34" rx="4" style={{ fill: "var(--color-petrol-50)", stroke: "var(--color-petrol-700)" }} strokeWidth="2" />
      <path d="M56 22h18l12 12v12H56V22Z" style={{ fill: "var(--color-surface)", stroke: "var(--color-petrol-700)" }} strokeWidth="2" strokeLinejoin="round" />
      <path d="M62 27h9l7 8H62v-8Z" style={{ fill: "var(--color-petrol-50)" }} />
      <circle cx="24" cy="48" r="7" style={{ fill: "var(--color-ink-900)" }} /><circle cx="24" cy="48" r="2.5" style={{ fill: "var(--color-surface)" }} />
      <circle cx="70" cy="48" r="7" style={{ fill: "var(--color-ink-900)" }} /><circle cx="70" cy="48" r="2.5" style={{ fill: "var(--color-surface)" }} />
      <path d="M12 20h20M12 27h12" style={{ stroke: "var(--color-emerald-500)" }} strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

/**
 * Estado vacío: título (qué falta) + frase + acción siguiente.
 *  - `icon`: ícono lucide en lugar del camión (p. ej. `SearchX` para "sin resultados de filtros", `Inbox`).
 *  - `compact`: menos aire, sin ilustración grande; para tarjetas pequeñas o paneles laterales.
 */
export function EmptyState({ title, description, action, actionHref, actionLabel, icon: Icon, compact }: { title: string; description?: string; action?: ReactNode; /** Alternativa serializable para usar desde .astro: enlace con estilo de botón. */ actionHref?: string; actionLabel?: string; icon?: ComponentType<{ size?: number; className?: string }>; compact?: boolean }) {
  return (
    <div className={cn('flex flex-col items-center text-center', compact ? 'gap-2 px-4 py-8' : 'gap-3 px-6 py-12')}>
      {Icon ? <span className="grid size-12 place-items-center rounded-full bg-petrol-50 text-petrol-700"><Icon size={22} /></span> : compact ? null : <TruckIllustration />}
      <h3 className="text-base font-semibold text-ink-900">{title}</h3>
      {description && <p className="max-w-sm text-sm text-ink-500">{description}</p>}
      {action && <div className="mt-1">{action}</div>}
      {!action && actionHref && actionLabel && <a href={actionHref} className="mt-1 inline-flex h-9 items-center rounded-input bg-petrol-700 px-4 text-sm font-medium text-on-solid hover:bg-petrol-800">{actionLabel}</a>}
    </div>
  );
}
