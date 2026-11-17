import { useEffect, useMemo, useState } from 'react';
import { SearchX } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { FilterBar } from '@/components/ui/filter-bar';
import { Combobox } from '@/components/ui/combobox';
import { DateRangePicker } from '@/components/ui/date-picker';
import { NumberInput } from '@/components/ui/number-input';
import { Pagination } from '@/components/ui/pagination';
import { SkeletonList } from '@/components/ui/skeleton';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { CapacityBar } from '@/components/domain/CapacityBar';
import { TrustScore } from '@/components/domain/TrustScore';
import { StatusPill } from '@/components/domain/StatusPill';
import { CITY_LABELS, estimateBookingPrice, formatDateTime, formatMoney, formatNumber, formatTripRate, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { CityItem } from '@/lib/api/server';
import type { TripView } from '../server';
import BookTripSheet from './BookTripSheet';

export type TripFilters = { origin: string; dest: string; from: string; to: string; minKg: string };

export default function SearchTrips({ trips, cities, filters, page, size, total }: { trips: TripView[]; cities: CityItem[]; filters: TripFilters; page: number; size: number; total: number }) {
  const [f, setF] = useState(filters);
  const [active, setActive] = useState<TripView | null>(null);
  const [loading, setLoading] = useState(false);
  const kg = Number(f.minKg);
  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const hasFilters = !!(f.origin || f.dest || f.from || f.minKg);

  function go(next: TripFilters, p = 1) {
    setF(next);
    setLoading(true);
    const q = new URLSearchParams();
    if (next.origin) q.set('origen', next.origin);
    if (next.dest) q.set('destino', next.dest);
    if (next.from) { q.set('desde', next.from); if (next.to) q.set('hasta', next.to); }
    if (next.minKg) q.set('peso', next.minKg);
    if (p > 1) q.set('pagina', String(p));
    const qs = q.toString();
    window.location.href = window.location.pathname + (qs ? `?${qs}` : '');
  }
  const set = (patch: Partial<TripFilters>) => go({ ...f, ...patch });
  const clear = () => go({ origin: '', dest: '', from: '', to: '', minKg: '' });

  useEffect(() => {
    if (f.minKg === filters.minKg) return;
    const t = setTimeout(() => go(f), 700);
    return () => clearTimeout(t);
  }, [f.minKg]);

  return (
    <>
      <Card>
        <FilterBar count={total} noun={['viaje', 'viajes']} active={hasFilters} onClear={clear}>
          <Field label="Origen" htmlFor="s-o"><Combobox id="s-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Destino" htmlFor="s-d"><Combobox id="s-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
          <Field label="Fecha de salida" htmlFor="s-f"><DateRangePicker id="s-f" value={{ from: f.from, to: f.to }} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
          <Field label="Peso a enviar" htmlFor="s-w"><NumberInput id="s-w" value={f.minKg} onValueChange={(v) => setF({ ...f, minKg: v })} suffix="kg" placeholder="Cualquiera" /></Field>
        </FilterBar>
        {loading ? (
          <SkeletonList rows={4} />
        ) : trips.length === 0 ? (
          hasFilters ? (
            <EmptyState icon={SearchX} title="No hay viajes con estos filtros" description="Prueba con otra ciudad o amplía el rango de fechas." action={<Button variant="outline" onClick={clear}>Limpiar filtros</Button>} />
          ) : (
            <EmptyState title="No hay viajes publicados por ahora" description="Cuando un transportista publique un viaje con espacio libre, lo verás aquí. Mientras tanto, puedes publicar una solicitud y recibir ofertas." action={<Button asChild variant="outline"><a href="/pyme/solicitudes/nueva">Publicar solicitud</a></Button>} />
          )
        ) : (
          <ul className="divide-y divide-steel-200" aria-label="Viajes disponibles">
            {trips.map((t) => {
              const est = kg > 0 ? estimateBookingPrice(t.pricingMode, t.rate ?? 0, kg, t.capacityKg ?? 0) : null;
              const fits = kg <= 0 || kg <= (t.remainingKg ?? 0);
              return (
                <li key={t.id} className="grid gap-x-5 gap-y-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)_minmax(0,1fr)_auto] lg:items-center" data-testid="trip-row">
                  <div className="min-w-0 space-y-1.5">
                    <RouteLine size="sm" origin={t.originCity!} destination={t.destinationCity!} originSub={formatDateTime(t.departureAt)} />
                    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-500"><a href={`/organizaciones/${t.carrierOrgId}`} className="max-w-48 truncate rounded-sm font-medium text-ink-700 hover:text-petrol-700 hover:underline">{t.carrierName}</a><TrustScore score={t.carrierTrustScore} /><span>· {VEHICLE_LABELS[t.vehicleType!] ?? t.vehicleType}</span>{t.status === 'FILLING' && <StatusPill kind="trip" status="FILLING" />}</div>
                  </div>
                  <div className="min-w-0">
                    <div className="text-xs text-ink-500">Tarifa</div>
                    <div className="money">{formatTripRate(t.pricingMode, t.rate)}</div>
                    {est != null && fits && <div className="mt-0.5 text-xs text-ink-700">Tu carga de <span className="font-mono">{formatNumber(kg)} kg</span>: <span className="font-mono font-semibold text-emerald-700">{formatMoney(est)}</span></div>}
                    {!fits && <div className="mt-0.5 text-xs text-amber-700">No cabe tu carga de {formatNumber(kg)} kg</div>}
                  </div>
                  <div className="min-w-0"><CapacityBar used={t.confirmedWeightKg ?? 0} total={t.capacityKg ?? 0} label={`Ocupación del viaje de ${CITY_LABELS[t.originCity!]} a ${CITY_LABELS[t.destinationCity!]}`} /><div className="mt-0.5 text-xs text-ink-700">Quedan <span className="font-mono">{formatTons(t.remainingKg)}</span></div></div>
                  <div className="lg:text-right"><Button variant="money" size="sm" className="max-lg:w-full" disabled={!fits} onClick={() => setActive(t)}>Reservar espacio<span className="sr-only"> en el viaje de {t.carrierName}</span></Button></div>
                </li>
              );
            })}
          </ul>
        )}
        {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>
      <BookTripSheet trip={active} defaultWeight={f.minKg} onClose={() => setActive(null)} />
    </>
  );
}
