import { ShieldCheck } from 'lucide-react';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { SIGNAL_LABELS, signalDetail } from '../signals';
import type { FraudSignal } from '../server';

/** Panel «Señales» (2.15): datos repetidos entre empresas y tasas fuera de lo normal. Cada empresa enlaza a su ficha 360. */
export default function SignalsList({ signals, compact }: { signals: FraudSignal[]; compact?: boolean }) {
  if (!signals.length) {
    return <EmptyState icon={ShieldCheck} title="Sin señales" description="No hay datos repetidos ni tasas fuera de lo normal." compact />;
  }
  return (
    <Table label="Señales de fraude" stack dense={compact}>
      <thead><tr><Th>Señal</Th><Th>Dato</Th><Th>Empresas</Th></tr></thead>
      <tbody>
        {signals.map((s, i) => (
          <Tr key={`${s.kind}-${i}`} data-testid="signal-row">
            <Td primary>
              <span className="font-medium text-ink-900">{SIGNAL_LABELS[s.kind!] ?? s.kind}</span>
              <span className="ml-2"><Badge tone={s.severity === 'HIGH' ? 'red' : 'amber'}>{s.severity === 'HIGH' ? 'Alta' : 'Media'}</Badge></span>
            </Td>
            <Td label="Dato" className="font-mono text-sm tabular-nums">{signalDetail(s)}</Td>
            <Td label="Empresas">
              <ul className="space-y-1">
                {(s.organizations ?? []).map((o) => (
                  <li key={o.id} className="flex flex-wrap items-center gap-2">
                    <a className="font-medium text-petrol-700 hover:underline" href={`/operador/organizaciones/${o.id}`}>{o.legalName ?? o.id}</a>
                    {o.verificationStatus && <StatusPill kind="verification" status={o.verificationStatus} />}
                  </li>
                ))}
              </ul>
            </Td>
          </Tr>
        ))}
      </tbody>
    </Table>
  );
}
