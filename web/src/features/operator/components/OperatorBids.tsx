import { Gavel } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { StatusPill } from '@/components/domain/StatusPill';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { TrustScore } from '@/components/domain/TrustScore';
import { EmptyState } from '@/components/domain/EmptyState';
import { cn } from '@/lib/utils';
import { VEHICLE_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import type { BidView } from '@/lib/api/server';

const pct = (amount: number, base?: number | null) => (base ? ((amount - base) / base) * 100 : null);

/** Ofertas de una solicitud, solo lectura (el operador las lee desde V15). Orden: vigentes primero, de menor a mayor monto. */
export default function OperatorBids({ bids, suggested }: { bids: BidView[]; suggested?: number | null }) {
  const live = (b: BidView) => b.status !== 'WITHDRAWN' && b.status !== 'REJECTED';
  const sorted = [...bids].sort((a, b) => Number(live(b)) - Number(live(a)) || (a.amount ?? 0) - (b.amount ?? 0));
  const best = sorted.find((b) => b.status === 'PENDING' || b.status === 'ACCEPTED');
  return (
    <Card>
      <CardHeader><div><CardTitle>Ofertas recibidas <span className="font-mono text-ink-500">({bids.length})</span></CardTitle><CardDescription>Solo consulta: la PYME es quien elige.</CardDescription></div></CardHeader>
      {sorted.length === 0 ? (
        <EmptyState compact icon={Gavel} title="Aún no hay ofertas" description="Cuando un transportista oferte, la verás aquí con su monto y su vehículo." />
      ) : (
        <Table label="Ofertas de la solicitud" stack dense>
          <thead><tr><Th>Transportista</Th><Th className="text-right">Monto</Th><Th>Vehículo</Th><Th>Enviada</Th><Th>Estado</Th></tr></thead>
          <tbody>
            {sorted.map((b) => {
              const d = pct(b.amount ?? 0, suggested);
              return (
                <Tr key={b.id} data-testid="operator-bid-row" className={cn(!live(b) && 'opacity-70')}>
                  <Td primary>
                    <div className="flex flex-wrap items-center gap-1.5 font-medium text-ink-900">{b.carrierName}<VerifiedBadge verified={b.carrierRucVerified} variant="icon" /><TrustScore score={b.carrierTrustScore} /></div>
                    {b.assignedByOperator && <Badge tone="petrol" className="mt-1">Asignada por OnCargo</Badge>}
                    {b.notes && <p className="mt-1 max-w-md text-xs text-ink-700">{b.notes}</p>}
                  </Td>
                  <Td label="Monto" className="whitespace-nowrap text-right">
                    <div className="money text-base">{formatMoney(b.amount)}</div>
                    {d != null && <div className={cn('font-mono text-xs tabular-nums', d <= 0 ? 'text-emerald-700' : 'text-amber-700')}>{`${d > 0 ? '+' : ''}${d.toFixed(1)}%`}<span className="font-sans text-ink-500"> vs. sugerido</span></div>}
                    {best?.id === b.id && sorted.filter(live).length > 1 && <Badge tone="emerald" className="mt-1">Mejor precio</Badge>}
                  </Td>
                  <Td label="Vehículo" className="whitespace-nowrap">{b.vehiclePlate ? <><div className="font-mono">{b.vehiclePlate}</div><div className="text-xs text-ink-500">{b.vehicleType ? VEHICLE_LABELS[b.vehicleType] ?? b.vehicleType : ''}</div></> : '—'}</Td>
                  <Td label="Enviada" className="whitespace-nowrap text-ink-700">{formatDateTime(b.createdAt)}</Td>
                  <Td label="Estado"><StatusPill status={b.status!} kind="bid" /></Td>
                </Tr>
              );
            })}
          </tbody>
        </Table>
      )}
    </Card>
  );
}
