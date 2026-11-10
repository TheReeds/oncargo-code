import { useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { Input, Select } from '@/components/ui/input';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Card } from '@/components/ui/card';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { Countdown } from '@/components/domain/Countdown';
import { Button } from '@/components/ui/button';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { RequestView } from '@/lib/api/server';

export default function RequestsTable({ requests }: { requests: RequestView[] }) {
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [from, setFrom] = useState('');

  const rows = useMemo(() => {
    const term = q.trim().toLowerCase();
    return requests
      .filter((r) => (!status || r.status === status) && (!from || (r.pickupDate ?? '') >= from))
      .filter((r) => !term || [r.originCity, r.destinationCity, CITY_LABELS[r.originCity!], CITY_LABELS[r.destinationCity!], r.cargoDescription].join(' ').toLowerCase().includes(term))
      .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  }, [requests, status, q, from]);

  return (
    <Card>
      <div className="flex flex-wrap items-end gap-3 border-b border-steel-200 p-4">
        <div className="relative min-w-52 flex-1">
          <label htmlFor="f-q" className="sr-only">Buscar por ruta o carga</label>
          <Search className="pointer-events-none absolute left-3 top-3 size-4 text-ink-500" aria-hidden="true" />
          <Input id="f-q" className="pl-9" placeholder="Buscar ruta o carga…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div className="w-44">
          <label htmlFor="f-status" className="sr-only">Estado</label>
          <Select id="f-status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Todos los estados</option>
            <option value="PUBLISHED">Publicadas</option>
            <option value="MATCHED">Adjudicadas</option>
            <option value="CANCELLED">Canceladas</option>
          </Select>
        </div>
        <div className="w-44">
          <label htmlFor="f-from" className="mb-1 block text-xs text-ink-500">Recojo desde</label>
          <Input id="f-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
      </div>
      {rows.length === 0 ? (
        <EmptyState
          title={requests.length === 0 ? 'Aún no tienes solicitudes' : 'Sin resultados'}
          description={requests.length === 0 ? 'Publica tu primera carga y recibe ofertas de transportistas verificados.' : 'Prueba con otros filtros.'}
          action={requests.length === 0 ? <Button asChild><a href="/pyme/solicitudes/nueva">Nueva solicitud</a></Button> : undefined}
        />
      ) : (
        <Table>
          <thead><tr><Th>Ruta</Th><Th>Carga</Th><Th>Recojo</Th><Th>Estado</Th><Th>Subasta</Th><Th className="text-right">Sugerido</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <Tr key={r.id}>
                <Td>
                  <a href={`/pyme/solicitudes/${r.id}`} className="font-medium text-petrol-700 hover:underline">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</a>
                  <div className="font-mono text-xs text-ink-500">{formatKm(r.distanceKm)}</div>
                </Td>
                <Td><div>{CARGO_LABELS[r.cargoType!]}</div><div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
                <Td className="whitespace-nowrap">{formatDate(r.pickupDate)}</Td>
                <Td><StatusPill status={r.status!} /></Td>
                <Td className="whitespace-nowrap">
                  {r.status === 'PUBLISHED' && r.auction ? (
                    <div className="space-y-0.5"><div className="text-xs text-ink-700"><span className="font-mono">{r.auction.bidCount ?? 0}/{r.auction.maxBids ?? 5}</span> ofertas</div><Countdown closesAt={r.auction.closesAt} /></div>
                  ) : <span className="text-ink-500">—</span>}
                </Td>
                <Td className="text-right"><span className="money">{formatMoney(r.suggestedPrice)}</span></Td>
                <Td className="text-right"><Button asChild variant="outline" size="sm"><a href={`/pyme/solicitudes/${r.id}`}>Ver</a></Button></Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
    </Card>
  );
}
