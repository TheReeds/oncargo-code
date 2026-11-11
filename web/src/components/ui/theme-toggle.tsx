import { useEffect, useState } from 'react';
import { Monitor, Moon, Sun } from 'lucide-react';
import { applyTheme, readPref, savePref, type ThemePref } from '@/lib/theme';
import { cn } from '@/lib/utils';

const OPTIONS: { value: ThemePref; label: string; Icon: typeof Sun }[] = [
  { value: 'light', label: 'Claro', Icon: Sun },
  { value: 'dark', label: 'Oscuro', Icon: Moon },
  { value: 'system', label: 'Sistema', Icon: Monitor },
];

/** Selector de tema Claro / Oscuro / Sistema. Persiste en localStorage (`logyx-theme`). */
export default function ThemeToggle({ className }: { className?: string }) {
  const [pref, setPref] = useState<ThemePref>('system');

  useEffect(() => {
    setPref(readPref());
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onSystem = () => { if (readPref() === 'system') applyTheme('system'); };
    mq.addEventListener('change', onSystem);
    return () => mq.removeEventListener('change', onSystem);
  }, []);

  function choose(v: ThemePref) {
    setPref(v);
    savePref(v);
  }

  return (
    <div role="group" aria-label="Tema" className={cn('inline-flex items-center rounded-input border border-steel-200 bg-surface p-0.5', className)}>
      {OPTIONS.map(({ value, label, Icon }) => (
        <button
          key={value}
          type="button"
          aria-pressed={pref === value}
          aria-label={`Tema ${label.toLowerCase()}`}
          title={`Tema ${label.toLowerCase()}`}
          onClick={() => choose(value)}
          className={cn('grid size-7 place-items-center rounded-[6px] transition-colors [&_svg]:size-4', pref === value ? 'bg-petrol-700 text-on-solid' : 'text-ink-500 hover:bg-steel-100 hover:text-ink-900')}
        >
          <Icon aria-hidden="true" />
        </button>
      ))}
    </div>
  );
}
