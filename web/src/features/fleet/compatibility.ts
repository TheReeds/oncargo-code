import { CARGO_LABELS, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { VehicleView } from '@/lib/api/server';

/**
 * Compatibilidad vehículo ↔ carga (RN02). Única fuente en la web: la usan la hoja de oferta, el marketplace ("Compatible"),
 * Smart Load y el cambio de vehículo de un envío. Espeja `fleet/domain/VehicleCargoCompatibility` del backend; la matriz
 * tipo de carga → tipos de vehículo llega en `RequestView.compatibleVehicleTypes`, así no se duplica.
 * La API sigue siendo la fuente de verdad (`LOGYX-CAP-001..005`, campo `vehicleId`).
 */
export type CargoNeed = {
  weightKg?: number | null;
  volumeM3?: number | null;
  cargoType?: string | null;
  /** Tipos de vehículo aptos para el tipo de carga (`RequestView.compatibleVehicleTypes`). Vacío o ausente = no se restringe el tipo. */
  compatibleVehicleTypes?: readonly string[] | null;
};

export type Compat = { ok: true } | { ok: false; code: 'CAP-002' | 'CAP-003' | 'CAP-001' | 'CAP-004'; reason: string; short: string };

export const OK: Compat = { ok: true };

/** Evalúa un vehículo contra una carga. Orden idéntico al backend: disponibilidad, kg, tipo, volumen. */
export function vehicleFit(v: Pick<VehicleView, 'status' | 'type' | 'maxWeightKg' | 'maxVolumeM3'>, need: CargoNeed): Compat {
  if (v.status && v.status !== 'AVAILABLE') {
    return { ok: false, code: 'CAP-002', short: v.status === 'MAINTENANCE' ? 'En mantenimiento' : 'En ruta', reason: v.status === 'MAINTENANCE' ? 'Está en mantenimiento.' : 'Está en ruta.' };
  }
  if (need.weightKg != null && (v.maxWeightKg ?? 0) < need.weightKg) {
    return { ok: false, code: 'CAP-001', short: `Capacidad ${formatTons(v.maxWeightKg)}`, reason: `Su capacidad (${formatTons(v.maxWeightKg)}) no alcanza para ${formatTons(need.weightKg)}.` };
  }
  const types = need.compatibleVehicleTypes;
  if (types && types.length > 0 && v.type && !types.includes(v.type)) {
    const cargo = need.cargoType ? (CARGO_LABELS[need.cargoType] ?? need.cargoType).toLowerCase() : 'esta carga';
    return { ok: false, code: 'CAP-003', short: `${VEHICLE_LABELS[v.type] ?? v.type} no lleva ${need.cargoType ? cargo : 'esta carga'}`, reason: `Un vehículo ${(VEHICLE_LABELS[v.type] ?? v.type).toLowerCase()} no puede llevar ${need.cargoType ? `carga ${cargo}` : 'este tipo de carga'}.` };
  }
  if (need.volumeM3 != null && v.maxVolumeM3 != null && v.maxVolumeM3 < need.volumeM3) {
    return { ok: false, code: 'CAP-004', short: `Volumen ${v.maxVolumeM3} m³`, reason: `La carga ocupa ${need.volumeM3} m³ y su volumen máximo es ${v.maxVolumeM3} m³.` };
  }
  return OK;
}

/** Vehículos aptos y no aptos (con el motivo) para una carga. */
export function partitionFleet<V extends VehicleView>(vehicles: readonly V[], need: CargoNeed) {
  const fit: V[] = [];
  const unfit: { vehicle: V; compat: Extract<Compat, { ok: false }> }[] = [];
  for (const v of vehicles) {
    const c = vehicleFit(v, need);
    if (c.ok) fit.push(v);
    else unfit.push({ vehicle: v, compat: c });
  }
  return { fit, unfit };
}

export const requestNeed = (r: { weightKg?: number; volumeM3?: number; cargoType?: string; compatibleVehicleTypes?: readonly string[] }): CargoNeed => ({
  weightKg: r.weightKg, volumeM3: r.volumeM3, cargoType: r.cargoType, compatibleVehicleTypes: r.compatibleVehicleTypes,
});

/** ¿Algún vehículo de la flota (disponible) puede llevar la carga? Alimenta la etiqueta "Compatible" del marketplace. */
export const fleetCanCarry = (vehicles: readonly VehicleView[], r: Parameters<typeof requestNeed>[0]) => {
  const need = requestNeed(r);
  return vehicles.some((v) => vehicleFit(v, need).ok);
};

/** Motivo breve (para una etiqueta) por el que ningún vehículo de la flota sirve para la carga. */
export function fleetBlocker(vehicles: readonly VehicleView[], r: Parameters<typeof requestNeed>[0]): string {
  if (vehicles.length === 0) return 'Sin vehículos en tu flota';
  const need = requestNeed(r);
  const avail = vehicles.filter((v) => v.status === 'AVAILABLE');
  if (avail.length === 0) return 'Sin vehículo disponible';
  const reasons = avail.map((v) => vehicleFit(v, need)).filter((c): c is Extract<Compat, { ok: false }> => !c.ok);
  const rank = { 'CAP-004': 0, 'CAP-003': 1, 'CAP-001': 2, 'CAP-002': 3 } as const; // gana el motivo más cercano a servir
  reasons.sort((a, b) => rank[a.code] - rank[b.code]);
  const top = reasons[0]?.code;
  const cargo = r.cargoType ? (CARGO_LABELS[r.cargoType] ?? r.cargoType).toLowerCase() : null;
  if (top === 'CAP-003') return cargo ? `Tu flota no lleva carga ${cargo}` : 'Tu flota no lleva este tipo de carga';
  if (top === 'CAP-004') return 'Excede el volumen de tu flota';
  return 'Excede tu flota';
}
