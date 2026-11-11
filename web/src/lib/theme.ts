import { useEffect, useState } from 'react';

export type ThemePref = 'light' | 'dark' | 'system';
export type Theme = 'light' | 'dark';
export const THEME_KEY = 'logyx-theme';

export function readPref(): ThemePref {
  try {
    const v = localStorage.getItem(THEME_KEY);
    if (v === 'light' || v === 'dark' || v === 'system') return v;
  } catch { /* sin storage */ }
  return 'system';
}

export function systemTheme(): Theme {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/** Aplica el tema resuelto al <html>. El script anti-parpadeo de Base.astro hace lo mismo antes del primer pintado. */
export function applyTheme(pref: ThemePref): Theme {
  const t: Theme = pref === 'system' ? systemTheme() : pref;
  document.documentElement.dataset.theme = t;
  document.documentElement.style.colorScheme = t;
  return t;
}

export function savePref(pref: ThemePref) {
  try { localStorage.setItem(THEME_KEY, pref); } catch { /* sin storage */ }
  applyTheme(pref);
}

/** Tema efectivo (light|dark) del documento; reacciona a cambios del toggle o del sistema. */
export function useResolvedTheme(): Theme {
  const [t, setT] = useState<Theme>('light');
  useEffect(() => {
    const read = () => setT(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light');
    read();
    const mo = new MutationObserver(read);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => mo.disconnect();
  }, []);
  return t;
}
