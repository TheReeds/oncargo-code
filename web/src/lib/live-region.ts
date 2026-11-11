import { useLiveRefresh } from '@/lib/realtime';

let inflight = false;

/** Vuelve a pedir la página actual y reemplaza `[data-live-region=region]` (las islas nuevas se hidratan solas). */
export async function softRefreshRegion(region: string): Promise<boolean> {
  if (inflight || document.visibilityState === 'hidden') return false;
  const cur = document.querySelector<HTMLElement>(`[data-live-region="${region}"]`);
  if (!cur) return false;
  // No pisar lo que el usuario está haciendo.
  if (document.querySelector('[role="dialog"][data-state="open"], [role="alertdialog"]')) return false;
  const ae = document.activeElement;
  if (ae && cur.contains(ae) && /^(INPUT|TEXTAREA|SELECT)$/.test(ae.tagName)) return false;
  inflight = true;
  try {
    const url = new URL(window.location.href);
    ['solicitud', 'creada'].forEach((k) => url.searchParams.delete(k)); // parámetros de un solo uso (abrir hoja, aviso)
    const res = await fetch(url, { headers: { Accept: 'text/html' }, credentials: 'same-origin' });
    if (!res.ok || res.redirected) return false; // sesión vencida o redirección: no tocar
    const doc = new DOMParser().parseFromString(await res.text(), 'text/html');
    const next = doc.querySelector(`[data-live-region="${region}"]`);
    if (!next) return false;
    const nodes = Array.from(next.childNodes).map((n) => document.adoptNode(n));
    cur.replaceChildren(...nodes);
    // Astro desmonta las islas retiradas del DOM al recibir este evento.
    document.dispatchEvent(new Event('astro:after-swap'));
    return true;
  } catch {
    return false;
  } finally {
    inflight = false;
  }
}

export function useRealtimeRefresh({ types, region, matchKey, matchValue, fallbackMs }: { types: string[]; region: string; matchKey?: string; matchValue?: string; fallbackMs: number }) {
  useLiveRefresh({
    types,
    match: matchKey ? (e) => e.data[matchKey] === matchValue : undefined,
    refresh: () => softRefreshRegion(region),
    fallbackMs,
    debounceMs: 400,
  });
}
