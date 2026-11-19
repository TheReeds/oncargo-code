import { useState } from 'react';
import { Landmark } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
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
    <Card>
      <CardHeader>
        <div>
          <CardTitle className="flex items-center gap-2"><Landmark className="size-4 text-ink-500" aria-hidden="true" />Comisión pendiente por transportista</CardTitle>
          <CardDescription>Envíos entregados cuya comisión (con IGV) aún no se cobró.{truncated ? ' Se muestran los primeros 100 envíos.' : ''}</CardDescription>
        </div>
        {carriers.length > 0 && <Can><Button variant="money" onClick={() => setOpen({})}>Registrar cobro de comisión</Button></Can>}
      </CardHeader>
      {carriers.length === 0 ? <p className="px-5 pb-5 text-sm text-ink-500">No hay comisión pendiente de cobro.</p> : (
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
    </Card>
  );
}
