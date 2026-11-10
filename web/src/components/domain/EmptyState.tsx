import type { ReactNode } from 'react';

export function TruckIllustration() {
  return (
    <svg width="96" height="64" viewBox="0 0 96 64" fill="none" aria-hidden="true">
      <rect x="4" y="12" width="52" height="34" rx="4" fill="#E6F1F5" stroke="#0B4F6C" strokeWidth="2" />
      <path d="M56 22h18l12 12v12H56V22Z" fill="#fff" stroke="#0B4F6C" strokeWidth="2" strokeLinejoin="round" />
      <path d="M62 27h9l7 8H62v-8Z" fill="#E6F1F5" />
      <circle cx="24" cy="48" r="7" fill="#0E1726" /><circle cx="24" cy="48" r="2.5" fill="#fff" />
      <circle cx="70" cy="48" r="7" fill="#0E1726" /><circle cx="70" cy="48" r="2.5" fill="#fff" />
      <path d="M12 20h20M12 27h12" stroke="#10B981" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-12 text-center">
      <TruckIllustration />
      <h3 className="text-base font-semibold text-ink-900">{title}</h3>
      {description && <p className="max-w-sm text-sm text-ink-500">{description}</p>}
      {action}
    </div>
  );
}
