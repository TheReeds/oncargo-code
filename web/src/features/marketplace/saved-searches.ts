// Búsquedas guardadas (roadmap 2.3): del filtro de «Cargas disponibles» a la búsqueda de la API y de vuelta a la URL.
import { CARGO_LABELS, CITY_LABELS, formatDate, formatTons } from '@/lib/format';
import type { Schemas } from '@/lib/api/server';
import type { SavedSearch } from './server';

type SavedBody = Schemas['SavedSearchRequest'];

/** Filtros de la pantalla de cargas (mismas claves que `MarketFilters`). */
export type SearchFilters = { origin: string; dest: string; from: string; to: string; cargo: string; maxKg: string };

export const hasSearchFilter = (f: SearchFilters) => !!(f.origin || f.dest || f.from || f.to || f.cargo || Number(f.maxKg) > 0);

/** Cuerpo para guardar: solo lo que tiene valor. */
export function toSavedBody(f: SearchFilters, name: string, vehicleId?: string): SavedBody {
  const kg = Number(f.maxKg);
  return {
    name: name.trim(),
    originCity: (f.origin || undefined) as SavedBody['originCity'], destinationCity: (f.dest || undefined) as SavedBody['destinationCity'], cargoType: (f.cargo || undefined) as SavedBody['cargoType'],
    pickupFrom: f.from || undefined, pickupTo: (f.to || f.from) || undefined,
    maxWeightKg: kg > 0 ? kg : undefined, vehicleId: vehicleId || undefined,
  };
}

type Describable = { originCity?: string; destinationCity?: string; cargoType?: string; pickupFrom?: string; pickupTo?: string; maxWeightKg?: number };

/** Frase corta del filtro: «Lima → Juliaca · Carga general · recojo 5 dic a 9 dic · hasta 8 t». */
export function describeSearch(s: Describable): string {
  const parts: string[] = [];
  if (s.originCity || s.destinationCity) parts.push(`${s.originCity ? CITY_LABELS[s.originCity] ?? s.originCity : 'Cualquier origen'} → ${s.destinationCity ? CITY_LABELS[s.destinationCity] ?? s.destinationCity : 'cualquier destino'}`);
  if (s.cargoType) parts.push(CARGO_LABELS[s.cargoType] ?? s.cargoType);
  if (s.pickupFrom) parts.push(s.pickupTo && s.pickupTo !== s.pickupFrom ? `recojo ${formatDate(s.pickupFrom)} a ${formatDate(s.pickupTo)}` : `recojo ${formatDate(s.pickupFrom)}`);
  if (s.maxWeightKg && s.maxWeightKg > 0) parts.push(`hasta ${formatTons(s.maxWeightKg)}`);
  return parts.join(' · ');
}

/** Lo mismo, desde los filtros de la pantalla. */
export const describeFilters = (f: SearchFilters): string => {
  const b = toSavedBody(f, '');
  return describeSearch(b);
};

/** URL de «Cargas disponibles» con los filtros de la búsqueda guardada. */
export function savedSearchHref(s: SavedSearch): string {
  const q = new URLSearchParams();
  if (s.originCity) q.set('origen', s.originCity);
  if (s.destinationCity) q.set('destino', s.destinationCity);
  if (s.pickupFrom) { q.set('desde', s.pickupFrom); if (s.pickupTo) q.set('hasta', s.pickupTo); }
  else if (s.pickupTo) { q.set('desde', s.pickupTo); q.set('hasta', s.pickupTo); }
  if (s.cargoType) q.set('carga', s.cargoType);
  if (s.maxWeightKg) q.set('peso', String(Math.round(s.maxWeightKg)));
  const qs = q.toString();
  return `/transportista/marketplace${qs ? `?${qs}` : ''}`;
}
