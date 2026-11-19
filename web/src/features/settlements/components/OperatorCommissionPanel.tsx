import { useState } from 'react';
import { Landmark } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/domain/EmptyState';
import { SectionCard } from '@/components/domain/SectionCard';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Can } from '@/lib/permissions/react';
import { formatMoney } from '@/lib/format';
import CommissionCollectDialog, { type PendingCarrier } from './CommissionCollectDialog';

/** Comisión pendiente por transportista, con «Registrar cobro de comisión». */
export default function OperatorCommissionPanel({ carriers, truncated }: { carriers: PendingCarrier[]; truncated?: boolean }) {
  const [open, setOpen] = useState<{ carrierId?: string } | null>(null);
  const [all, setAll] = useState(false);
  const shown = all ? carriers : carriers.slice(0, 8);
  return (
    <SectionCard flush icon={Landmark} tone="money" title="Comisión pendiente por transportista" count={carriers.length}
      description={`Envíos entregados cuya comisión (con IGV) aún no se cobró.${truncated ? ' Se muestran los primeros 100 envíos.' : ''}`}
      action={carriers.length > 0 ? <Can><Button variant="money" onClick={() => setOpen({})}>Registrar cobro de comisión</Button></Can> : undefined}>
      {carriers.length === 0 ? <EmptyState compact icon={Landmark} title="No hay comisión pendiente" description="Todas las comisiones de envíos entregados ya se cobraron." /> : (
        <Table label="Comisión pendiente por transportista" stack dense>
          <thead><tr><Th>Transportista</Th><Th className="text-right">Envíos</Th><Th className="text-right">Comisión con IGV</Th><Th className="relative"><span className="sr-only">Acciones</span></Th></tr></thead>
          <tbody>
            {shown.map((c) => (
              <Tr key={c.id} data-testid="pending-carrier">
                <Td primary className="font-medium text-ink-900">{c.name}</Td>
                <Td label="Envíos" className="text-right"><span className="font-mono tabular-nums">{c.count}</span></Td>
                <Td label="Comisión con IGV" className="text-right"><span className="money">{formatMoney(c.amount)}</span></Td>
                <Td actions className="text-right"><Can><Button size="sm" variant="outline" onClick={() => setOpen({ carrierId: c.id })}>Cobrar<span className="sr-only"> a {c.name}</span></Button></Can></Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
      {carriers.length > 8 && <div className="border-t border-steel-200 px-5 py-3"><Button variant="ghost" size="sm" onClick={() => setAll(!all)}>{all ? 'Ver menos' : `Ver los ${carriers.length - 8} restantes`}</Button></div>}
      {open && <CommissionCollectDialog carriers={carriers} initialCarrierId={open.carrierId} onClose={() => setOpen(null)} onDone={() => { setOpen(null); window.location.reload(); }} />}
    </SectionCard>
  );
}
