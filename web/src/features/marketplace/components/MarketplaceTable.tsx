import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, SearchX, SlidersHorizontal, Truck } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { FilterBar } from '@/components/ui/filter-bar';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Tooltip } from '@/components/ui/tooltip';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { QuickFilters } from '@/components/ui/quick-filters';
import { DateRangePicker, type DateRangeValue } from '@/components/ui/date-picker';
import { NumberInput } from '@/components/ui/number-input';
import { Checkbox } from '@/components/ui/form-controls';
import { Pagination } from '@/components/ui/pagination';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { useRealtime } from '@/lib/realtime';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { BidView, CityItem, RequestView, VehicleView } from '@/lib/api/server';
import { TrustScore } from '@/components/domain/TrustScore';
import NegotiationSheet from '@/features/negotiation/components/NegotiationSheet';
import { MessageSquare } from 'lucide-react';
import { cn } from '@/lib/utils';
import BidSheet from './BidSheet';
import SavedSearches from './SavedSearches';
import { standingText } from '../bid-state';
import { fleetBlocker, fleetCanCarry } from '@/features/fleet/compatibility';

export type MarketFilters = { origin: string; dest: string; from: string; to: string; cargo: string; maxKg: string; compat: boolean; sort: string };

const SORT_OPTIONS = [{ value: 'pickup', label: 'Recojo más próximo' }, { value: 'price_desc', label: 'Mayor precio' }, { value: 'recent', label: 'Más recientes' }];

import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';

export default function MarketplaceTable({ requests, vehicles, cities, filters, page, size, total, openRequest }: { requests: RequestView[]; vehicles: VehicleView[]; cities: CityItem[]; filters: MarketFilters; page: number; size: number; total: number; /** `?solicitud=`: carga que se abre al entrar (aviso de carga nueva que coincide). */ openRequest?: RequestView | null }) {
  const canOperate = useCan('OPERATE');
  const [f, setF] = useState(filters);
  const [active, setActive] = useState<RequestView | null>(openRequest ?? null);
  const [chat, setChat] = useState<RequestView | null>(null);
  const [showFilters, setShowFilters] = useState(false);

  // RN02 por fecha: «en ruta» no impide ofertar (depende de la agenda); solo el mantenimiento.
  const usable = vehicles.filter((v) => v.status !== 'MAINTENANCE');
  const maxCap = Math.max(0, ...usable.map((v) => v.maxWeightKg ?? 0));
  // Misma matriz que la hoja de oferta (RN02): tipo de carga, kg y m³ contra la flota. Ver `features/fleet/compatibility.ts`.
  const isCompatible = (r: RequestView) => fleetCanCarry(vehicles, r);

  const cityOptions = useMemo(() => cities.map((c) => ({ value: c.code!, label: c.name! })), [cities]);
  const cargoOptions = useMemo(() => [{ value: 'ALL', label: 'Todos los tipos' }, ...Object.entries(CARGO_LABELS).map(([value, label]) => ({ value, label }))], []);

  const hasFilters = !!(f.origin || f.dest || f.from || f.cargo || f.maxKg || f.compat);
  const fineFilters = [f.origin, f.dest, f.from, f.cargo, f.maxKg].filter(Boolean).length;
  const quick = f.compat ? 'compat' : f.sort === 'price_desc' ? 'pay' : 'all';
  const setQuick = (v: string) => go({ ...f, compat: v === 'compat', sort: v === 'pay' ? 'price_desc' : 'pickup' });
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
  // auction.updated trae el conteo de ofertas y si la subasta cerró: se aplica en sitio, sin recargar.
  const [live, setLive] = useState<Record<string, { bidCount?: number; closed?: boolean }>>({});
  useRealtime(['auction.updated'], (e) => {
    const id = e.data.requestId as string | undefined;
    if (!id) return;
    setLive((l) => ({ ...l, [id]: { bidCount: typeof e.data.bidCount === 'number' ? e.data.bidCount : l[id]?.bidCount, closed: !!e.data.auctionClosed } }));
  });
  // Oferta rápida: tras ofertar no se recarga; la fila pasa a «Ofertada» en sitio y el foco va a la siguiente carga.
  const [placed, setPlaced] = useState<Record<string, BidView>>({});
  const rows0 = requests.filter((r) => !live[r.id!]?.closed).map((r) => {
    const withLive = live[r.id!]?.bidCount != null && r.auction ? { ...r, auction: { ...r.auction, bidCount: live[r.id!]!.bidCount } } : r;
    const mine = placed[r.id!];
    return mine ? { ...withLive, myBid: mine, auction: withLive.auction ? { ...withLive.auction, bidCount: Math.max(withLive.auction.bidCount ?? 0, (r.auction?.bidCount ?? 0) + 1) } : withLive.auction } : withLive;
  });
  // Con "Mi vehículo", las cargas que la flota sí puede llevar van primero (orden estable dentro de la página).
  const rows = f.compat ? [...rows0].sort((a, b) => Number(isCompatible(b)) - Number(isCompatible(a))) : rows0;

  /** Siguiente carga de la lista (después de `afterId`) a la que todavía puedo ofertar. */
  const nextBiddable = (afterId: string) => {
    const i = rows.findIndex((r) => r.id === afterId);
    return rows.slice(i + 1).find((r) => !r.myBid && !placed[r.id!] && isCompatible(r)) ?? null;
  };
  function afterBid(bid: BidView, advance: boolean) {
    const current = active;
    if (!current) return;
    setPlaced((p) => ({ ...p, [current.id!]: bid }));
    const next = nextBiddable(current.id!);
    if (advance && next) { setActive(next); return; }
    setActive(null);
    // Radix devuelve el foco al botón que abrió la hoja (que ya no existe): se pasa a la siguiente carga.
    window.setTimeout(() => {
      const target = next ? document.querySelector<HTMLElement>(`[data-bid-row="${next.id}"]`) : null;
      (target ?? document.getElementById('m-filters'))?.focus?.();
    }, 150);
  }

  // El peso máximo se aplica al dejar de escribir.
  useEffect(() => {
    if (f.maxKg === filters.maxKg) return;
    const t = setTimeout(() => go(f), 700);
    return () => clearTimeout(t);
  }, [f.maxKg]);

  return (
    <>
      <ReadOnlyNotice className="mb-3">No puedes enviar ofertas ni escribir a los solicitantes.</ReadOnlyNotice>
      <Card>
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-steel-200 px-4 py-3 sm:px-5">
          <QuickFilters
            label="Vista rápida"
            value={quick}
            onValueChange={setQuick}
            options={[{ value: 'all', label: 'Todas' }, { value: 'compat', label: 'Mi vehículo' }, { value: 'pay', label: 'Mejor pago' }]}
          />
          <div className="flex flex-wrap items-center gap-3">
            <SavedSearches filters={f} vehicles={vehicles} />
            {maxCap > 0
              ? <span className="hidden items-center gap-1.5 text-xs text-ink-500 sm:inline-flex"><Truck className="size-3.5" aria-hidden="true" />Tu mayor vehículo disponible lleva <span className="font-mono font-semibold text-ink-700">{formatTons(maxCap)}</span></span>
              : <a href="/transportista/flota" className="text-xs font-medium text-amber-700 hover:underline">Agrega un vehículo disponible para ofertar</a>}
            <Button variant="outline" size="sm" className="sm:hidden" aria-expanded={showFilters} aria-controls="m-filters" onClick={() => setShowFilters((v) => !v)}>
              <SlidersHorizontal />Filtros{fineFilters > 0 && <span className="rounded-full bg-petrol-700 px-1.5 font-mono text-[11px] text-on-solid">{fineFilters}</span>}
            </Button>
          </div>
        </div>
        <div id="m-filters" className={cn(!showFilters && 'max-sm:hidden')}>
          <FilterBar
            count={total}
            noun={['carga', 'cargas']}
            active={hasFilters}
            onClear={clear}
            sort={<Select id="m-s" value={f.sort} onValueChange={(v) => set({ sort: v })} options={SORT_OPTIONS} className="h-9 w-52" aria-label="Ordenar por" />}
            extra={<>
              <Field label="Peso máximo" htmlFor="m-w" className="w-full sm:w-44"><NumberInput id="m-w" value={f.maxKg} onValueChange={(v) => setF({ ...f, maxKg: v })} suffix="kg" placeholder="Sin límite" /></Field>
              <Tooltip content={maxCap > 0 ? `Tu vehículo disponible con más capacidad lleva ${formatTons(maxCap)}.` : 'Agrega un vehículo disponible para filtrar por compatibilidad.'}>
                <label htmlFor="m-compat" className="flex h-10 cursor-pointer items-center gap-2 text-sm text-ink-700">
                  <Checkbox id="m-compat" checked={f.compat} onCheckedChange={(c) => set({ compat: c === true })} />
                  Solo cargas compatibles con mi flota
                  {maxCap > 0 && <span className="font-mono text-xs text-ink-500">≤ {formatTons(maxCap)}</span>}
                </label>
              </Tooltip>
            </>}
          >
            <Field label="Origen" htmlFor="m-o"><Combobox id="m-o" value={f.origin} onValueChange={(v) => set({ origin: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
            <Field label="Destino" htmlFor="m-d"><Combobox id="m-d" value={f.dest} onValueChange={(v) => set({ dest: v })} options={cityOptions} placeholder="Todas las ciudades" searchPlaceholder="Buscar ciudad…" allowClear /></Field>
            <Field label="Fecha de recojo" htmlFor="m-f"><DateRangePicker id="m-f" value={range} onValueChange={(r) => set({ from: r.from, to: r.to })} placeholder="Cualquier fecha" /></Field>
            <Field label="Tipo de carga" htmlFor="m-c"><Select id="m-c" value={f.cargo} onValueChange={(v) => set({ cargo: v === 'ALL' ? '' : v })} options={cargoOptions} placeholder="Todos los tipos" /></Field>
          </FilterBar>
        </div>
        {rows.length === 0 ? (
          !hasFilters ? (
            <EmptyState icon={Truck} title="No hay cargas abiertas por ahora" description="Cuando una PYME publique una solicitud, la verás aquí con su subasta en curso." action={<Button variant="outline" asChild><a href="/transportista/retornos">Ver cargas de retorno</a></Button>} />
          ) : (
            <EmptyState icon={SearchX} title="No hay cargas con estos filtros" description="Prueba con otra ciudad o amplía el rango de fechas." action={<Button variant="outline" onClick={clear}><SearchX /> Limpiar filtros</Button>} />
          )
        ) : (
          <Table label="Cargas disponibles" stack className="[&_td]:px-3 [&_th]:px-3 sm:[&_td:first-child]:pl-5 sm:[&_th:first-child]:pl-5 sm:[&_td:last-child]:pr-5">
            <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Subasta</Th><Th className="text-right">Precio</Th><Th><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {rows.map((r) => {
                const ok = isCompatible(r);
                const mine = !!r.myBid;
                return (
                  <Tr key={r.id}>
                    <Td primary className="min-w-0">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="font-medium">{CITY_LABELS[r.originCity!]} <span className="text-ink-500" aria-hidden="true">→</span><span className="sr-only">a</span> {CITY_LABELS[r.destinationCity!]}</div>
                          <div className="text-xs text-ink-700"><span className="font-mono">{formatKm(r.distanceKm)}</span> · recojo {formatDate(r.pickupDate)}</div>
                          <div className="mt-0.5 flex items-center gap-1.5 text-xs text-ink-500">
                            {r.requesterName && <span className="max-w-40 truncate">{r.requesterName}</span>}
                            <VerifiedBadge verified={r.requesterRucVerified} variant="icon" />
                            <TrustScore score={r.requesterTrustScore} />
                          </div>
                        </div>
                        <div className="hidden shrink-0 text-right @max-[720px]:block"><div className="money text-xl">{formatMoney(r.suggestedPrice)}</div><div className="font-mono text-[11px] text-ink-500">piso {formatMoney(r.priceFloor)}</div></div>
                      </div>
                    </Td>
                    <Td label="Carga">
                      <div className="@max-[720px]:text-right">
                        <div>{CARGO_LABELS[r.cargoType!]} · <span className="font-mono">{formatTons(r.weightKg)}</span></div>
                        <div className="text-xs text-ink-500">Mín. {r.minVehicleType ? VEHICLE_LABELS[r.minVehicleType] : '—'}</div>
                        {ok ? <Badge tone="emerald" className="mt-1">Compatible</Badge> : <Badge tone="neutral" className="mt-1" title="Ningún vehículo de tu flota puede llevar esta carga (tipo, peso o volumen)">{fleetBlocker(vehicles, r)}</Badge>}
                      </div>
                    </Td>
                    <Td label="Subasta" className="whitespace-nowrap">
                      <div className="@max-[720px]:text-right">
                        <div className="font-mono text-sm tabular-nums">{r.auction?.bidCount ?? 0}/{r.auction?.maxBids ?? 5} <span className="font-sans text-xs text-ink-500">ofertas</span></div>
                        <Countdown closesAt={r.auction?.closesAt} className="text-xs" />
                      </div>
                    </Td>
                    <Td label="Precio sugerido" className="whitespace-nowrap text-right @max-[720px]:hidden!">
                      <div className="@max-[720px]:text-right"><div className="money text-lg" title="Precio sugerido">{formatMoney(r.suggestedPrice)}</div><div className="font-mono text-xs text-ink-500">piso {formatMoney(r.priceFloor)}</div></div>
                    </Td>
                    <Td actions className="whitespace-nowrap text-right">
                      <div className="flex flex-col items-end gap-1 @max-[720px]:w-full @max-[720px]:flex-row @max-[720px]:items-center">
                        {mine ? (
                          <div className="flex flex-col items-end gap-0.5 @max-[720px]:flex-1 @max-[720px]:flex-row @max-[720px]:items-center @max-[720px]:gap-2"><Badge tone="petrol"><CheckCircle2 className="size-3" aria-hidden="true" />Ofertada</Badge><span className="font-mono text-xs tabular-nums text-ink-700">{formatMoney(r.myBid!.amount)}</span>{standingText(r.myBidStanding) && <span className="max-w-44 whitespace-normal text-xs font-medium text-ink-700" data-testid="market-standing">{standingText(r.myBidStanding)!.headline}</span>}</div>
                        ) : canOperate ? (
                          <Button size="sm" variant="money" className="@max-[720px]:flex-1" data-bid-row={r.id} onClick={() => setActive(r)}>Ofertar</Button>
                        ) : null}
                        {(canOperate || mine) && <Button size="sm" variant="ghost" className="@max-[720px]:flex-1" onClick={() => setChat(r)}><MessageSquare />{mine ? 'Conversar' : 'Preguntar'}<span className="sr-only"> al solicitante de {CITY_LABELS[r.originCity!]} a {CITY_LABELS[r.destinationCity!]}</span></Button>}
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      {total > size && <div className="border-t border-steel-200 px-4 py-3 sm:px-5"><Pagination page={page} size={size} total={total} onPageChange={(p) => go(f, p)} /></div>}
      </Card>
      <BidSheet request={active} vehicles={vehicles} onClose={() => setActive(null)} onAsk={(r) => { setActive(null); setChat(r); }} onPlaced={afterBid} hasNext={!!active && !!nextBiddable(active.id!)} />
      {chat && <NegotiationSheet open onOpenChange={(o) => !o && setChat(null)} requestId={chat.id!} bid={chat.myBid?.id ? { id: chat.myBid.id, amount: chat.myBid.amount, status: chat.myBid.status } : null} title={chat.myBid ? 'Conversar con la PYME' : 'Preguntar al solicitante'} description={`${CITY_LABELS[chat.originCity!]} → ${CITY_LABELS[chat.destinationCity!]} · ${chat.requesterName ?? ''}`} />}
    </>
  );
}
