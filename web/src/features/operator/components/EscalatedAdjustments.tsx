import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Check } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { SkeletonList } from '@/components/ui/skeleton';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { useLiveRefresh } from '@/lib/realtime';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { formatDateTime, formatMoney } from '@/lib/format';
import { ADJUSTMENT_LABELS, signedMoney } from '@/features/shipments/components/AdjustmentsPanel';
import type { EscalatedAdjustment } from '../server';

/** Ajustes que la PYME no respondió a tiempo (2.6): los decide OnCargo con nota. */
export default function EscalatedAdjustments({ initial }: { initial: EscalatedAdjustment[] }) {
  const [rows, setRows] = useState<EscalatedAdjustment[] | null>(initial);
  const [error, setError] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<EscalatedAdjustment | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const load = useCallback(async () => {
    const { data, error: err } = await actions.operator.escalatedAdjustments({});
    if (err) { setError(err.message); return; }
    setError(null);
    setRows(data);
  }, []);
  useEffect(() => { if (!initial) load(); }, [initial, load]);
  useLiveRefresh({ types: ['shipment.adjustment_updated'], refresh: load, fallbackMs: 60_000 });

  async function accept(a: EscalatedAdjustment) {
    if (!(await confirm({ title: `¿Aceptar ${ADJUSTMENT_LABELS[a.type!].toLowerCase()} de ${signedMoney(a.amount)}?`, description: 'Se suma al precio final del envío y se avisa a las dos partes.', confirmLabel: 'Aceptar ajuste' }))) return;
    setBusy(a.id!);
    const { error: err } = await actions.shipments.decideAdjustment({ adjustmentId: a.id!, accept: true });
    setBusy(null);
    if (err) { toast.error(splitActionError(err).message); return; }
    toast.success('Ajuste aceptado');
    load();
  }

  return (
    <Card>
      {error && <Alert tone="error" title="No pudimos cargar los ajustes" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{error}</Alert>}
      {rows === null ? <SkeletonList rows={3} /> : rows.length === 0 ? (
        <EmptyState icon={Check} title="No hay ajustes por decidir" description="Aparecen aquí cuando la PYME no responde a tiempo." compact />
      ) : (
        <Table label="Ajustes por decidir" stack>
          <thead><tr><Th>Envío</Th><Th>Ajuste</Th><Th>Partes</Th><Th>Escalado</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
          <tbody>
            {rows.map((a) => (
              <Tr key={a.id} data-testid="escalated-adjustment">
                <Td primary>
                  <a className="font-mono font-medium text-petrol-700 hover:underline" href={`/operador/envios/${a.shipmentId}`}>{a.shipmentCode ?? a.shipmentId!.slice(0, 8)}</a>
                  {a.originCity && a.destinationCity && <div className="mt-1"><RouteLine size="sm" origin={a.originCity} destination={a.destinationCity} /></div>}
                </Td>
                <Td label="Ajuste">
                  <span className="font-medium text-ink-900">{ADJUSTMENT_LABELS[a.type!] ?? a.type}</span> <span className="font-mono tabular-nums">{signedMoney(a.amount)}</span>
                  <span className="block text-xs text-ink-500">Acordado {formatMoney(a.agreedPrice)}{a.note ? ` · ${a.note}` : ''}</span>
                </Td>
                <Td label="Partes"><span className="block text-sm">{a.requesterName}</span><span className="block text-xs text-ink-500">{a.carrierName}</span></Td>
                <Td label="Escalado" className="whitespace-nowrap">{formatDateTime(a.escalatedAt)}</Td>
                <Td actions>
                  <div className="flex flex-wrap gap-2">
                    <Button size="sm" variant="money" loading={busy === a.id} onClick={() => accept(a)}>Aceptar</Button>
                    <Button size="sm" variant="destructive" disabled={busy === a.id} onClick={() => setRejecting(a)}>Rechazar</Button>
                  </div>
                </Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
      <ReasonDialog
        open={!!rejecting} onOpenChange={(o) => !o && setRejecting(null)} title="Rechazar ajuste" required tone="destructive"
        description="Las dos partes verán tu motivo." confirmLabel="Rechazar ajuste" maxLength={LIMITS.adjustmentNote} reasonField="note"
        onSubmit={async (note) => {
          const { error: err } = await actions.shipments.decideAdjustment({ adjustmentId: rejecting!.id!, accept: false, note });
          if (err) return splitActionError(err);
          toast.success('Ajuste rechazado');
          setRejecting(null);
          load();
          return null;
        }}
      />
    </Card>
  );
}
