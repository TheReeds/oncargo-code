// Oferta rápida (roadmap v2 1.10): atajos de monto y lo que el navegador recuerda entre ofertas.
// RN03: ningún atajo baja del piso. RN04: no hay atajo «mejor oferta − paso»: el transportista nunca ve montos ajenos.
export type Shortcut = 'suggested' | 'm5' | 'm10' | 'floor';

export const SHORTCUT_LABEL: Record<Shortcut, string> = { suggested: 'Sugerido', m5: '−5 %', m10: '−10 %', floor: 'Piso' };

/** Monto del atajo en soles, nunca por debajo del piso (RN03). Nulo si falta el dato de la carga. */
export function shortcutAmount(s: Shortcut, suggested?: number | null, floor?: number | null): number | null {
  const fl = floor ?? 0;
  const clamp = (n: number) => Math.max(fl, n);
  if (s === 'floor') return floor ? fl : null;
  if (!suggested) return null;
  if (s === 'suggested') return clamp(suggested);
  return clamp(Math.round(suggested * (s === 'm5' ? 0.95 : 0.9)));
}

/** ¿El atajo quedó recortado al piso? (se avisa en su etiqueta). */
export const clippedToFloor = (s: Shortcut, suggested?: number | null, floor?: number | null) =>
  (s === 'm5' || s === 'm10') && !!suggested && !!floor && Math.round(suggested * (s === 'm5' ? 0.95 : 0.9)) < floor;

const KEY = { vehicle: 'logyx.bid.vehicle', notes: 'logyx.bid.notes', shortcut: 'logyx.bid.shortcut' } as const;

function read(key: string): string | null {
  try { return window.localStorage.getItem(key); } catch { return null; }
}
function write(key: string, value: string | null) {
  try { if (value) window.localStorage.setItem(key, value); else window.localStorage.removeItem(key); } catch { /* sin storage */ }
}

/** Lo último que usó esta persona al ofertar (solo en este navegador). */
export function recalled(): { vehicleId: string | null; notes: string; shortcut: Shortcut | null } {
  const sc = read(KEY.shortcut);
  return { vehicleId: read(KEY.vehicle), notes: read(KEY.notes) ?? '', shortcut: sc === 'suggested' || sc === 'm5' || sc === 'm10' || sc === 'floor' ? sc : null };
}

export function remember(vehicleId: string, notes: string, shortcut: Shortcut | null) {
  write(KEY.vehicle, vehicleId);
  write(KEY.notes, notes.trim() || null);
  write(KEY.shortcut, shortcut);
}
