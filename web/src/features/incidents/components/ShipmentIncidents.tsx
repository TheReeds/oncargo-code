import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { CircleCheck } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { SkeletonList } from '@/components/ui/skeleton';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { useLiveRefresh } from '@/lib/realtime';
import { INCIDENT_CATEGORY_LABELS, formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import { ageTone, formatOpenTime, hoursOpen } from '../lib';
import type { IncidentView } from '../server';

/** Incidencias de un envío, en solo lectura (detalle de envío del operador). */
export default function ShipmentIncidents({ shipmentId }: { shipmentId: string }) {
  const [list, setList] = useState<IncidentView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    setError(null);
    const { data, error: err } = await actions.incidents.forShipment({ shipmentId });
    if (err) { setError(err.message); setList([]); return; }
    setList([...(data ?? [])].sort((a, b) => Number(!!b.active) - Number(!!a.active) || (b.createdAt ?? '').localeCompare(a.createdAt ?? '')));
  }, [shipmentId]);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh({ types: ['incident.reported', 'incident.updated'], match: (e) => e.data.shipmentId === shipmentId, refresh: load, fallbackMs: 60_000 });

  return (
    <Card>
      <CardHeader><div><CardTitle>Incidencias del envío</CardTitle><CardDescription>Una incidencia activa congela el avance de las paradas (RN11).</CardDescription></div></CardHeader>
      {error && <div className="p-4"><Alert tone="error" title="No pudimos cargar las incidencias" action={<Button variant="outline" size="sm" onClick={load}>Reintentar</Button>}>{error}</Alert></div>}
      {list === null ? <SkeletonList rows={2} /> : list.length === 0 && !error ? (
        <EmptyState compact icon={CircleCheck} title="Sin incidencias" description="Nadie reportó problemas en este envío." />
      ) : (
        <ul className="divide-y divide-steel-200">
          {list.map((i) => {
            const h = hoursOpen(i);
            return (
              <li key={i.id}>
                <a href={`/incidencias/${i.id}`} className="block px-5 py-3.5 hover:bg-steel-100/60">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <span className="text-sm font-semibold text-ink-900">{INCIDENT_CATEGORY_LABELS[i.category!]}</span>
                    <StatusPill status={i.status!} />
                    <span className={cn('ml-auto font-mono text-xs tabular-nums', ageTone(h, i.active))}>{i.active ? 'Abierta hace ' : 'Duró '}{formatOpenTime(h)}</span>
                  </div>
                  <p className="mt-1 line-clamp-2 text-sm text-ink-700">{i.description}</p>
                  <p className="mt-1 text-xs text-ink-500">Reportada {formatDateTime(i.createdAt)}</p>
                </a>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
