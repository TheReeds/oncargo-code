import { FileWarning } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import type { FleetDocument } from '../server';
import { DOC_LABEL, expiryText } from '../documents';

export type OwnerLabels = { VEHICLE: Record<string, string>; DRIVER: Record<string, string> };

/** Documentos que piden atención: vencidos o por vencer (≤ 30 días, según la API). Primero los vencidos. */
export const needsAttention = (docs: FleetDocument[]) =>
  docs.filter((d) => d.current !== false && (d.status === 'EXPIRED' || d.status === 'EXPIRING')).sort((a, b) => (a.expiresAt ?? '').localeCompare(b.expiresAt ?? ''));

/** Resumen por dueño para la tabla: «1 vencido», «2 por vencer», «Al día» o «Sin documentos». */
export function ownerDocsSummary(docs: FleetDocument[], ownerId: string): { tone: 'emerald' | 'amber' | 'red' | 'neutral'; label: string } {
  const own = docs.filter((d) => d.ownerId === ownerId && d.current !== false);
  const expired = own.filter((d) => d.status === 'EXPIRED').length;
  const expiring = own.filter((d) => d.status === 'EXPIRING').length;
  if (expired) return { tone: 'red', label: `${expired} ${expired === 1 ? 'vencido' : 'vencidos'}` };
  if (expiring) return { tone: 'amber', label: `${expiring} por vencer` };
  return own.length ? { tone: 'emerald', label: 'Al día' } : { tone: 'neutral', label: 'Sin documentos' };
}

/**
 * Arriba de Flota: vencidos y por vencer (≤ 30 días) con enlace al vehículo o conductor (abre sus documentos).
 * No renderiza nada si todo está al día.
 */
export default function ExpirySummary({ docs, owners, onOpen }: { docs: FleetDocument[]; owners: OwnerLabels; onOpen: (ownerType: 'VEHICLE' | 'DRIVER', id: string) => void }) {
  const list = needsAttention(docs);
  if (list.length === 0) return null;
  const expired = list.filter((d) => d.status === 'EXPIRED').length;
  const soon = list.length - expired;
  const title = [expired ? `${expired} ${expired === 1 ? 'documento vencido' : 'documentos vencidos'}` : '', soon ? `${soon} por vencer` : ''].filter(Boolean).join(' y ');
  return (
    <Alert tone={expired ? 'error' : 'warning'} title={title} className="[&>div]:w-full">
      <p>Renueva los documentos y súbelos para seguir ofertando sin avisos. La PYME ve si tu SOAT y tu revisión técnica están vigentes.</p>
      <ul className="mt-2 divide-y divide-steel-200" aria-label="Documentos por atender" data-testid="expiry-list">
        {list.map((d) => {
          const type = (d.ownerType === 'DRIVER' ? 'DRIVER' : 'VEHICLE') as 'VEHICLE' | 'DRIVER';
          const owner = owners[type][d.ownerId ?? ''] ?? (type === 'VEHICLE' ? 'Vehículo' : 'Conductor');
          return (
            <li key={d.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-1.5">
              <span className="min-w-0 text-sm"><span className="font-medium">{DOC_LABEL[d.type ?? ''] ?? d.typeLabel}</span> · <span className={type === 'VEHICLE' ? 'font-mono' : ''}>{owner}</span> · {expiryText(d.status, d.expiresAt, d.daysLeft)}</span>
              <Button size="sm" variant="outline" onClick={() => onOpen(type, d.ownerId!)}><FileWarning aria-hidden="true" />Ver documentos<span className="sr-only"> de {owner}</span></Button>
            </li>
          );
        })}
      </ul>
    </Alert>
  );
}

