import { CODES, DIST_KM, DRIVE_MIN, type CityCode } from './corridor-data';

/**
 * Réplica en cliente de las reglas de cotización del backend (`TariffCalculator`, OE04):
 *   combustible = km × 0.65 · transporte = km × t × 0.80 × multiplicador(carga) · peajes = ceil(km / 120) × 12 · manipuleo = 5 % del transporte
 *   sugerido = suma · piso (RN03) = 75 % del sugerido.
 * Es una estimación ilustrativa para la landing: la cotización real la calcula la API al publicar la solicitud.
 */
export const CARGO_MULT = { GENERAL: 1, FRAGILE: 1.3, PERISHABLE: 1.25, HAZARDOUS: 1.5, BULK: 0.95, OVERSIZED: 1.4 } as const;
export type CargoKey = keyof typeof CARGO_MULT;
export const FLOOR_RATIO = 0.75;

const r2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;

export function km(a: CityCode, b: CityCode): number {
  return DIST_KM[CODES.indexOf(a)][CODES.indexOf(b)];
}
export function driveMinutes(a: CityCode, b: CityCode): number {
  return DRIVE_MIN[CODES.indexOf(a)][CODES.indexOf(b)];
}
export function formatDrive(min: number): string {
  const h = Math.floor(min / 60), m = Math.round(min % 60);
  return `${h} h ${String(m).padStart(2, '0')} min`;
}

export function quote(distanceKm: number, tons: number, cargo: CargoKey = 'GENERAL') {
  const fuel = r2(distanceKm * 0.65);
  const transportRaw = distanceKm * tons * 0.8 * CARGO_MULT[cargo];
  const transport = r2(transportRaw);
  const tolls = Math.ceil(distanceKm / 120) * 12;
  const handling = r2(transportRaw * 0.05);
  const suggested = r2(fuel + transport + tolls + handling);
  return { fuel, transport, tolls, handling, suggested, floor: r2(suggested * FLOOR_RATIO) };
}

/** Clave de `ROUTE_PATHS` para un par (el orden de la clave sigue a `CODES`). */
export function routeKey(a: CityCode, b: CityCode): string {
  return CODES.indexOf(a) < CODES.indexOf(b) ? `${a}-${b}` : `${b}-${a}`;
}
