import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { History, UserRound } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { formatDateTime } from '@/lib/format';
import { roleLabel } from '@/lib/permissions';
import { useLiveRefresh } from '@/lib/realtime';
import { activityLabel, actorText } from '../labels';
import type { ActivitySubject } from '../server';

type Entry = { at?: string; action: string; entity?: string; entityId?: string; actor?: { name?: string; role?: string } | null; organizationName?: string | null; own?: boolean };
type Data = { createdBy?: { name?: string; role?: string } | null; entries?: Entry[] };

const CREATED_VERB: Record<ActivitySubject, string> = { REQUEST: 'Publicada por', BID: 'Ofertada por', SHIPMENT: 'Creado por', TRIP: 'Publicado por', INCIDENT: 'Reportada por' };

/** «Publicada por Ana R. (Operaciones)»: autoría compacta para encabezados. Sin autor visible no renderiza nada. */
export function AuthorLine({ subject, createdBy, className }: { subject: ActivitySubject; createdBy?: { name?: string; role?: string } | null; className?: string }) {
  if (!createdBy?.name) return null;
  return (
    <span className={className} data-testid="author-line">
      <UserRound className="mr-1 inline size-3.5 align-[-2px] text-ink-500" aria-hidden="true" />
      {CREATED_VERB[subject]} <span className="font-medium text-ink-700">{createdBy.name}</span>{createdBy.role ? ` (${roleLabel(createdBy.role)})` : ''}
    </span>
  );
}

/**
 * Tarjeta «Actividad» (roadmap v2 1.8): quién creó el recurso y quién hizo cada acción, desde la auditoría bajo RLS
 * (`GET /activity`). Ves las acciones y las personas de tu empresa; las de la otra parte aparecen con su razón social.
 * Solo lectura (sirve igual a un Lector). Se refresca con los eventos en vivo del recurso.
 */
export default function ActivityTrail({ subject, id, initial, title = 'Actividad', liveTypes = [] }: { subject: ActivitySubject; id: string; initial?: Data | null; title?: string; liveTypes?: string[] }) {
  const [data, setData] = useState<Data | null>(initial ?? null);
  const [error, setError] = useState(false);
  const [all, setAll] = useState(false);

  const load = useCallback(async () => {
    const { data: d, error: err } = await actions.activity.get({ subject, id });
    if (err || !d) { setError(true); return; }
    setError(false);
    setData(d as Data);
  }, [subject, id]);

  useEffect(() => { if (!initial) load(); }, [initial, load]);
  useLiveRefresh({ types: liveTypes, refresh: load, fallbackMs: 0 });

  const entries = data?.entries ?? [];
  const shown = all ? entries : entries.slice(0, 6);
  return (
    <Card className="min-w-0" data-testid="activity-trail">
      <CardHeader><CardTitle className="flex items-center gap-2 text-base"><History className="size-4 text-ink-500" aria-hidden="true" /> {title}</CardTitle></CardHeader>
      <CardBody className="space-y-3">
        <AuthorLine subject={subject} createdBy={data?.createdBy} className="block text-sm text-ink-500" />
        {error ? (
          <p role="alert" className="text-sm text-ink-700">No pudimos cargar la actividad. <Button variant="link" className="h-auto p-0" onClick={load}>Reintentar</Button></p>
        ) : data === null ? (
          <div className="space-y-2" aria-busy="true">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-8 w-full" />)}</div>
        ) : entries.length === 0 ? (
          <p className="text-sm text-ink-500">Todavía no hay acciones registradas.</p>
        ) : (
          <ol className="space-y-2.5" aria-label={title}>
            {shown.map((e, i) => (
              <li key={`${e.action}-${e.entityId}-${e.at}-${i}`} className="flex items-start gap-2.5 text-sm">
                <span className="mt-1.5 size-2 shrink-0 rounded-full bg-petrol-700" aria-hidden="true" />
                <span className="min-w-0">
                  <span className="font-medium text-ink-900">{activityLabel(e.action)}</span>
                  <span className="block text-xs text-ink-500">{actorText(e)} · {formatDateTime(e.at)}</span>
                </span>
              </li>
            ))}
          </ol>
        )}
        {entries.length > 6 && <Button variant="ghost" size="sm" onClick={() => setAll((v) => !v)}>{all ? 'Ver menos' : `Ver las ${entries.length} acciones`}</Button>}
      </CardBody>
    </Card>
  );
}
