import { VEHICLE_LABELS } from '@/lib/format';
import type { partitionFleet } from '../compatibility';

/** Vehículos que no pueden llevar la carga, cada uno con su motivo (RN02). Visible siempre, no solo dentro del combobox. */
export function UnfitList({ items }: { items: ReturnType<typeof partitionFleet>['unfit'] }) {
  if (items.length === 0) return null;
  return (
    <div className="rounded-input border border-steel-200 bg-steel-100/60 p-3 text-xs text-ink-700">
      <p className="mb-1 font-medium text-ink-900">No disponibles para esta carga</p>
      <ul className="space-y-0.5">
        {items.map(({ vehicle: v, compat }) => (
          <li key={v.id}><span className="font-mono font-semibold">{v.plate}</span> · {VEHICLE_LABELS[v.type!]}: {compat.reason}</li>
        ))}
      </ul>
    </div>
  );
}
