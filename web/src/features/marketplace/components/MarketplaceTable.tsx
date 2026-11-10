import { useMemo, useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select } from '@/components/ui/input';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { CityItem, RequestView, VehicleView } from '@/lib/api/server';
import BidSheet from './BidSheet';

export default function MarketplaceTable({ requests, vehicles, cities }: { requests: RequestView[]; vehicles: VehicleView[]; cities: CityItem[] }) {
  const [origin, setOrigin] = useState('');
  const [dest, setDest] = useState('');
  const [date, setDate] = useState('');
  const [compat, setCompat] = useState(false);
  const [active, setActive] = useState<RequestView | null>(null);

  const usable = vehicles.filter((v) => v.status === 'AVAILABLE');
  const maxCap = Math.max(0, ...usable.map((v) => v.maxWeightKg ?? 0));
  const isCompatible = (r: RequestView) => usable.some((v) => (v.maxWeightKg ?? 0) >= (r.weightKg ?? 0));

  const rows = useMemo(
    () =>
      requests
        .filter((r) => (!origin || r.originCity === origin) && (!dest || r.destinationCity === dest) && (!date || r.pickupDate === date) && (!compat || isCompatible(r)))
        .sort((a, b) => (a.auction?.closesAt ?? '').localeCompare(b.auction?.closesAt ?? '')),
    [requests, origin, dest, date, compat, vehicles],
  );

  return (
    <>
      <Card>
        <div className="flex flex-wrap items-end gap-3 border-b border-steel-200 p-4">
          <div className="w-40"><label htmlFor="m-o" className="mb-1 block text-xs text-ink-500">Origen</label>
            <Select id="m-o" value={origin} onChange={(e) => setOrigin(e.target.value)}><option value="">Todos</option>{cities.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</Select></div>
          <div className="w-40"><label htmlFor="m-d" className="mb-1 block text-xs text-ink-500">Destino</label>
            <Select id="m-d" value={dest} onChange={(e) => setDest(e.target.value)}><option value="">Todos</option>{cities.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}</Select></div>
          <div className="w-44"><label htmlFor="m-f" className="mb-1 block text-xs text-ink-500">Fecha de recojo</label>
            <input id="m-f" type="date" value={date} onChange={(e) => setDate(e.target.value)} className="h-10 w-full rounded-input border border-ink-400/60 bg-surface px-3 text-sm" /></div>
          <label className="flex h-10 cursor-pointer items-center gap-2 rounded-input border border-steel-200 bg-surface px-3 text-sm has-[:checked]:border-emerald-500 has-[:checked]:bg-emerald-50">
            <input type="checkbox" checked={compat} onChange={(e) => setCompat(e.target.checked)} className="size-4 accent-[#047857]" />
            Compatible con mi flota
            <span className="font-mono text-xs text-ink-500">{maxCap > 0 ? `≤ ${formatTons(maxCap)}` : 'sin vehículos'}</span>
          </label>
          {(origin || dest || date || compat) && <Button variant="ghost" size="sm" onClick={() => { setOrigin(''); setDest(''); setDate(''); setCompat(false); }}>Limpiar</Button>}
          <span className="ml-auto text-sm text-ink-500"><span className="font-mono">{rows.length}</span> cargas</span>
        </div>
        {rows.length === 0 ? (
          <EmptyState title={requests.length === 0 ? 'No hay cargas abiertas por ahora' : 'Ninguna carga coincide con los filtros'} description={requests.length === 0 ? 'Cuando una PYME publique una carga la verás aquí.' : 'Prueba quitando algún filtro.'} />
        ) : (
          <Table className="[&_td]:px-3 [&_th]:px-3">
            <thead><tr><Th>Recojo</Th><Th className="hidden text-right xl:table-cell">Distancia</Th><Th>Entrega</Th><Th>Carga</Th><Th>Ofertas</Th><Th className="text-right">Sugerido</Th><Th className="sticky right-0 bg-steel-100"><span className="sr-only">Acción</span></Th></tr></thead>
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
                        <span className="hidden max-w-40 truncate xl:inline">{r.requesterName}</span>
                        {ok && <Badge tone="emerald">Compatible</Badge>}
                      </div>
                    </Td>
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
      </Card>
      <BidSheet request={active} vehicles={vehicles} onClose={() => setActive(null)} />
    </>
  );
}
