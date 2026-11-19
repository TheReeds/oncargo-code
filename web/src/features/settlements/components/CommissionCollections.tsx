import { Receipt } from 'lucide-react';
import { EmptyState } from '@/components/domain/EmptyState';
import { SectionCard } from '@/components/domain/SectionCard';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { formatDate, formatMoney } from '@/lib/format';
import type { CommissionCollection } from '../server';

/** Cobros de comisión que OnCargo registró (transportista: los suyos; operador: todos). */
export default function CommissionCollections({ items, perspective, title = 'Cobros de comisión' }: { items: CommissionCollection[]; perspective: 'CARRIER' | 'OPERATOR'; title?: string }) {
  return (
    <SectionCard flush icon={Receipt} tone="petrol" title={title} count={items.length} description={perspective === 'CARRIER' ? 'Lo que OnCargo ya cobró de tus envíos: comisión más su IGV.' : 'Cobros registrados por el equipo de OnCargo.'}>
      {items.length === 0 ? <EmptyState compact icon={Receipt} title="Aún no hay cobros de comisión" description={perspective === 'CARRIER' ? 'Cuando OnCargo cobre la comisión de tus envíos, lo verás aquí.' : 'Los cobros que registres aparecerán aquí.'} /> : (
        <Table label={title} stack dense>
          <thead><tr><Th>Fecha</Th>{perspective === 'OPERATOR' && <Th>Transportista</Th>}<Th>Forma de pago</Th><Th>Operación</Th><Th className="text-right">Envíos</Th><Th className="text-right">Monto</Th><Th>Nota</Th></tr></thead>
          <tbody>
            {items.map((c) => (
              <Tr key={c.id} data-testid="commission-collection">
                <Td primary className="whitespace-nowrap font-medium">{formatDate(c.paidOn)}</Td>
                {perspective === 'OPERATOR' && <Td label="Transportista">{c.carrierName}</Td>}
                <Td label="Forma de pago">{c.methodLabel}</Td>
                <Td label="Operación"><span className="font-mono">{c.operationNumber}</span></Td>
                <Td label="Envíos" className="text-right"><span className="font-mono tabular-nums">{c.shipmentsCount}</span></Td>
                <Td label="Monto" className="text-right"><span className="money">{formatMoney(c.amount)}</span></Td>
                <Td label="Nota"><span className="block max-w-56 truncate text-ink-700">{c.note ?? '—'}</span></Td>
              </Tr>
            ))}
          </tbody>
        </Table>
      )}
    </SectionCard>
  );
}
