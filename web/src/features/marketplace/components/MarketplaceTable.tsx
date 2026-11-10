import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, SearchX, ShieldCheck, X } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Avatar } from '@/components/ui/avatar';
import { Tooltip } from '@/components/ui/tooltip';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker, type DateRangeValue } from '@/components/ui/date-picker';
import { NumberInput } from '@/components/ui/number-input';
import { Checkbox } from '@/components/ui/form-controls';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { CityItem, RequestView, VehicleView } from '@/lib/api/server';
import BidSheet from './BidSheet';

export type MarketFilters = { origin: string; dest: string; from: string; to: string; cargo: string; maxKg: string; compat: boolean; sort: string };

const SORT_OPTIONS = [{ value: 'pickup', label: 'Recojo más próximo' }, { value: 'price_desc', label: 'Mayor precio' }, { value: 'recent', label: 'Más recientes' }];

export default function MarketplaceTable({ requests, vehicles, cities, filters, page, size, total }: { requests: RequestView[]; vehicles: VehicleView[]; cities: CityItem[]; filters: MarketFilters; page: number; size: number; total: number }) {
  const [f, setF] = useState(filters);
  const [active, setActive] = useState<RequestView | null>(null);

  const usable = vehicles.filter((v) => v.status === 'AVAILABLE');
  const maxCap = Math.max(0, ...usable.map((v) => v.maxWeightKg ?? 0));
  const isCompatible = (r: RequestView) => usable.some((v) => (v.maxWeightKg ?? 0) >= (r.weightKg ?? 0));

  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const cargoOptions = useMemo(() => [{ value: 'ALL', label: 'Todos los tipos' }, ...Object.entries(CARGO_LABELS).map(([value, label]) => ({ value, label }))], []);

  const hasFilters = !!(f.origin || f.dest || f.from || f.cargo || f.maxKg || f.compat);
  const range: DateRangeValue = { from: f.from, to: f.to };

  /** Navega a la URL con los filtros (SSR); cambiar un filtro vuelve a la página 1. */
  function go(next: MarketFilters, p = 1) {
    setF(next);
    const q = new URLSearchParams();
    if (next.origin) q.set('origen', next.origin);
    if (next.dest) q.set('destino', next.dest);
    if (next.from) { q.set('desde', next.from); if (next.to) q.set('hasta', next.to); }
    if (next.cargo) q.set('carga', next.cargo);
    if (next.maxKg) q.set('peso', next.maxKg);
    if (next.compat) q.set('compatibles', '1');
    if (next.sort && next.sort !== 'pickup') q.set('orden', next.sort);
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<MarketFilters>) => go({ ...f, ...patch });
  const clear = () => go({ origin: '', dest: '', from: '', to: '', cargo: '', maxKg: '', compat: false, sort: f.sort });
  const rows = requests;

  // El peso máximo se aplica al dejar de escribir.
  useEffect(() => {
    if (f.maxKg === filters.maxKg) return;
    const t = setTimeout(() => go(f), 700);
    return () => clearTimeout(t);
  }, [f.maxKg]);

  return (
    <>
      <Card>
        <div className="space-y-4 border-b border-steel-200 p-4 sm:p-5">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Origen" htmlFor="m-o"><Combobox id="m-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
            <Field label="Destino" htmlFor="m-d"><Combobox id="m-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
            <Field label="Fecha de recojo" htmlFor="m-f"><DateRangePicker id="m-f" value={range} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
            <Field label="Tipo de carga" htmlFor="m-c"><Select id="m-c" value={f.cargo} onValueChange={(v) => set({ cargo: v === 'ALL' ? '' : v })} options={cargoOptions} placeholder="Todos los tipos" /></Field>
          </div>
          <div className="flex flex-wrap items-end gap-x-5 gap-y-3">
            <Field label="Peso máximo" htmlFor="m-w" className="w-full sm:w-44"><NumberInput id="m-w" value={f.maxKg} onValueChange={(v) => setF({ ...f, maxKg: v })} suffix="kg" placeholder="Sin límite" /></Field>
            <Tooltip content={maxCap > 0 ? `Tu vehículo disponible con más capacidad lleva ${formatTons(maxCap)}.` : 'Agrega un vehículo disponible para filtrar por compatibilidad.'}>
              <label htmlFor="m-compat" className="flex h-10 cursor-pointer items-center gap-2 text-sm text-ink-700">
                <Checkbox id="m-compat" checked={f.compat} onCheckedChange={(c) => set({ compat: c === true })} />
                Solo cargas compatibles con mi flota
                {maxCap > 0 && <span className="font-mono text-xs text-ink-500">≤ {formatTons(maxCap)}</span>}
              </label>
            </Tooltip>
            <div className="ml-auto flex h-10 items-center gap-3">
              <span className="text-sm text-ink-500" aria-live="polite"><span className="font-mono font-semibold text-ink-900">{total}</span> {total === 1 ? 'carga' : 'cargas'}</span>
              <Select id="m-s" value={f.sort} onValueChange={(v) => set({ sort: v })} options={SORT_OPTIONS} className="h-9 w-44" />
              {hasFilters && <Button variant="ghost" size="sm" onClick={clear}><X /> Limpiar filtros</Button>}
            </div>
          </div>
        </div>
        {rows.length === 0 ? (
          !hasFilters ? (
            <EmptyState title="No hay cargas abiertas por ahora" description="Cuando una PYME publique una solicitud, la verás aquí con su subasta en curso." />
          ) : (
            <EmptyState title="No hay cargas con estos filtros" description="Prueba con otra ciudad o amplía el rango de fechas." action={<Button variant="outline" onClick={clear}><SearchX /> Limpiar filtros</Button>} />
          )
        ) : (
          <Table className="[&_td]:px-3 [&_th]:px-3 sm:[&_td:first-child]:pl-5 sm:[&_th:first-child]:pl-5">
            <thead><tr><Th>Recojo</Th><Th className="hidden text-right xl:table-cell">Distancia</Th><Th>Entrega</Th><Th>Carga</Th><Th className="hidden lg:table-cell">Vehículo mínimo</Th><Th>Ofertas</Th><Th className="text-right">Sugerido</Th><Th className="sticky right-0 bg-steel-100"><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {rows.map((r) => {
                const ok = isCompatible(r);
                return (
                  <Tr key={r.id}>
                    <Td><div className="font-medium">{CITY_LABELS[r.originCity!]}</div><div className="max-w-44 truncate text-xs text-ink-500">{r.originAddress}</div><div className="text-xs text-ink-700">{formatDate(r.pickupDate)}</div></Td>
                    <Td className="hidden whitespace-nowrap text-right font-mono tabular-nums text-ink-700 xl:table-cell">{formatKm(r.distanceKm)}</Td>
                    <Td><div className="font-medium">{CITY_LABELS[r.destinationCity!]} <span className="font-mono text-xs font-normal text-ink-500 xl:hidden">· {formatKm(r.distanceKm)}</span></div><div className="hidden max-w-44 truncate text-xs text-ink-500 xl:block">{r.destinationAddress}</div></Td>
                    <Td>
                      <div className="whitespace-nowrap">{CARGO_LABELS[r.cargoType!]} · <span className="font-mono">{formatTons(r.weightKg)}</span></div>
                      <div className="mt-1 flex items-center gap-1.5 text-xs text-ink-500">
                        {r.requesterName && <span className="hidden items-center gap-1.5 xl:flex"><Avatar name={r.requesterName} size="sm" className="size-5 text-[9px]" /><span className="max-w-32 truncate">{r.requesterName}</span></span>}
                        {r.requesterRucVerified && <Badge tone="petrol"><ShieldCheck className="size-3" aria-hidden="true" />Empresa verificada</Badge>}
                        {ok && <Badge tone="emerald">Compatible</Badge>}
                      </div>
                    </Td>
                    <Td className="hidden whitespace-nowrap lg:table-cell">{r.minVehicleType ? VEHICLE_LABELS[r.minVehicleType] : '—'}</Td>
                    <Td className="whitespace-nowrap">
                      <div className="font-mono text-sm tabular-nums">{r.auction?.bidCount ?? 0}/{r.auction?.maxBids ?? 5}</div>
                      <Countdown closesAt={r.auction?.closesAt} className="text-xs" />
                    </Td>
                    <Td className="whitespace-nowrap text-right"><div className="money">{formatMoney(r.suggestedPrice)}</div><div className="font-mono text-xs text-ink-500">piso {formatMoney(r.priceFloor)}</div></Td>
                    <Td className="sticky right-0 whitespace-nowrap bg-surface text-right shadow-[-8px_0_8px_-8px_rgba(14,23,38,0.15)]">
                      {r.myBid ? (
                        <div className="flex flex-col items-end gap-0.5"><Badge tone="petrol"><CheckCircle2 className="size-3" aria-hidden="true" />Ofertada</Badge><span className="font-mono text-xs tabular-nums text-ink-700">{formatMoney(r.myBid.amount)}</span></div>
                      ) : (
                        <Button size="sm" variant="money" onClick={() => setActive(r)}>Ofertar</Button>
                      )}
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>
      <BidSheet request={active} vehicles={vehicles} onClose={() => setActive(null)} />
    </>
  );
}
