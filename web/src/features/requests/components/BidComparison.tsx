import { useState } from 'react';
import { actions } from 'astro:actions';
import { BadgeCheck, Trophy } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { formatDateTime, formatMoney } from '@/lib/format';
import type { BidView } from '@/lib/api/server';

export function diffPct(amount: number, suggested?: number | null) {
  if (!suggested) return null;
  return ((amount - suggested) / suggested) * 100;
}

export default function BidComparison({ bids, suggested, canAccept }: { bids: BidView[]; suggested?: number | null; canAccept: boolean }) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const sorted = [...bids].sort((a, b) => (a.amount ?? 0) - (b.amount ?? 0));
  const best = sorted.find((b) => b.status !== 'REJECTED');

  async function accept(id: string) {
    setError(null);
    setBusyId(id);
    const { data, error } = await actions.requests.accept({ bidId: id });
    if (error) {
      setError(error.message);
      setBusyId(null);
      setConfirmId(null);
      return;
    }
    window.location.href = `/pyme/envios?id=${data.shipmentId}`;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ofertas recibidas <span className="ml-1 font-mono text-ink-500">({bids.length})</span></CardTitle>
        <span className="text-xs text-ink-500">Ordenadas por menor monto</span>
      </CardHeader>
      {error && <div className="p-4 pb-0"><Alert tone="error">{error}</Alert></div>}
      {bids.length === 0 ? (
        <EmptyState title="Aún no hay ofertas" description="Los transportistas compatibles verán tu carga en el marketplace. Te mostraremos las ofertas aquí." />
      ) : (
        <Table>
          <thead><tr><Th>Transportista</Th><Th className="text-right">Oferta</Th><Th className="text-right">vs. sugerido</Th><Th>Nota</Th><Th>Estado</Th>{canAccept && <Th><span className="sr-only">Acción</span></Th>}</tr></thead>
          <tbody>
            {sorted.map((b) => {
              const d = diffPct(b.amount ?? 0, suggested);
              const isBest = best?.id === b.id;
              return (
                <Tr key={b.id} className={isBest ? 'bg-emerald-50/60' : ''}>
                  <Td>
                    <div className="flex items-center gap-1.5 font-medium">{b.carrierName}{b.carrierRucVerified && <BadgeCheck className="size-4 text-emerald-700" aria-label="Empresa verificada" />}</div>
                    <div className="text-xs text-ink-500">{formatDateTime(b.createdAt)}</div>
                  </Td>
                  <Td className="whitespace-nowrap text-right">
                    <span className="money text-base">{formatMoney(b.amount)}</span>
                    {isBest && <div className="mt-0.5 flex justify-end"><Badge tone="emerald"><Trophy className="size-3" aria-hidden="true" />Mejor precio</Badge></div>}
                  </Td>
                  <Td className={`whitespace-nowrap text-right font-mono tabular-nums ${d != null && d <= 0 ? 'text-emerald-700' : 'text-amber-700'}`}>
                    {d == null ? '—' : `${d > 0 ? '+' : ''}${d.toFixed(1)}%`}
                  </Td>
                  <Td className="max-w-56 text-ink-700">{b.notes || <span className="text-ink-500">—</span>}</Td>
                  <Td><StatusPill status={b.status!} kind="bid" /></Td>
                  {canAccept && (
                    <Td className="text-right">
                      {b.status === 'PENDING' &&
                        (confirmId === b.id ? (
                          <div className="flex justify-end gap-1.5">
                            <Button size="sm" variant="money" disabled={busyId === b.id} onClick={() => accept(b.id!)}>{busyId === b.id ? 'Aceptando…' : 'Confirmar'}</Button>
                            <Button size="sm" variant="ghost" onClick={() => setConfirmId(null)}>No</Button>
                          </div>
                        ) : (
                          <Button size="sm" variant={isBest ? 'money' : 'outline'} disabled={busyId !== null} onClick={() => setConfirmId(b.id!)}>Aceptar</Button>
                        ))}
                    </Td>
                  )}
                </Tr>
              );
            })}
          </tbody>
        </Table>
      )}
    </Card>
  );
}
