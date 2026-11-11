import { useCallback, useMemo, useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { MessageSquare, TriangleAlert, CircleCheck, Lock } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { Timeline, type TimelineItem } from '@/components/ui/timeline';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { splitActionError } from '@/lib/action-error';
import { INCIDENT_CATEGORY_LABELS, formatDateTime } from '@/lib/format';
import { ageTone, formatOpenTime, hoursOpen } from '../lib';
import type { IncidentView } from '../server';

const MAX = 2000;
type Route = { origin: string; destination: string } | null;

/** Detalle de una incidencia: datos, hilo de comentarios y, para el operador, el panel de gestión. */
export default function IncidentDetail({ initial, orgType, route }: { initial: IncidentView; orgType: 'PYME' | 'CARRIER' | 'OPERATOR'; route: Route }) {
  const [inc, setInc] = useState(initial);
  const [comment, setComment] = useState('');
  const [commentError, setCommentError] = useState<string | null>(null);
  const [posting, setPosting] = useState(false);
  const [notes, setNotes] = useState(initial.resolutionNotes ?? '');
  const [notesError, setNotesError] = useState<string | null>(null);
  const [changing, setChanging] = useState<string | null>(null);
  // Estado y comentarios en vivo (incident.updated de esta incidencia).
  const reloadIncident = useCallback(async () => {
    const { data } = await actions.incidents.get({ id: initial.id! });
    if (data) setInc(data);
  }, [initial.id]);
  useLiveRefresh({ types: ['incident.updated', 'incident.reported'], match: (e) => e.data.incidentId === initial.id, refresh: reloadIncident, fallbackMs: 60_000 });
  const operator = orgType === 'OPERATOR';
  const closed = inc.status === 'CLOSED';
  const shipmentHref = orgType === 'PYME' ? `/pyme/envios?id=${inc.shipmentId}` : orgType === 'CARRIER' ? `/transportista/envios?id=${inc.shipmentId}` : null;
  const h = hoursOpen(inc);

  const items: TimelineItem[] = useMemo(() => {
    const out: TimelineItem[] = [{ id: 'created', title: 'Incidencia reportada', detail: null, meta: formatDateTime(inc.createdAt), status: 'done', icon: <TriangleAlert className="size-3" aria-hidden="true" /> }];
    for (const c of [...(inc.comments ?? [])].sort((a, b) => (a.createdAt ?? '').localeCompare(b.createdAt ?? ''))) {
      out.push({ id: c.id!, title: c.authorName ?? 'Comentario', detail: c.body, meta: formatDateTime(c.createdAt), status: 'pending', icon: <MessageSquare className="size-3" aria-hidden="true" /> });
    }
    if (inc.resolvedAt) out.push({ id: 'resolved', title: closed ? 'Incidencia cerrada' : 'Incidencia resuelta', detail: inc.resolutionNotes, meta: formatDateTime(inc.resolvedAt), status: 'done', icon: <CircleCheck className="size-3" aria-hidden="true" /> });
    return out;
  }, [inc, closed]);

  async function send(e: React.FormEvent) {
    e.preventDefault();
    if (!comment.trim()) { setCommentError('Escribe un comentario antes de enviarlo.'); return; }
    setCommentError(null);
    setPosting(true);
    const { data, error } = await actions.incidents.comment({ id: inc.id!, body: comment });
    setPosting(false);
    if (error) { const { message } = splitActionError(error); setCommentError(message); return; }
    setInc(data);
    setComment('');
    toast.success('Comentario enviado');
  }

  async function change(status: 'IN_REVIEW' | 'RESOLVED' | 'CLOSED') {
    setNotesError(null);
    if (status === 'RESOLVED' && !notes.trim()) { setNotesError('Escribe las notas de resolución para resolver la incidencia.'); return; }
    const texts = {
      IN_REVIEW: { title: '¿Pasar la incidencia a revisión?', description: 'Las partes verán que LOGYX ya la está atendiendo. El envío sigue detenido.', confirmLabel: 'Pasar a revisión', tone: undefined },
      RESOLVED: { title: '¿Resolver la incidencia?', description: 'Se liberará el envío y se avisará a ambas partes con tus notas de resolución.', confirmLabel: 'Resolver incidencia', tone: undefined },
      CLOSED: { title: '¿Cerrar la incidencia?', description: 'Se liberará el envío y ya no se podrá comentar. No se puede deshacer.', confirmLabel: 'Cerrar incidencia', tone: 'destructive' as const },
    }[status];
    if (!(await confirm(texts))) return;
    setChanging(status);
    const { data, error } = await actions.incidents.changeStatus({ id: inc.id!, status, resolutionNotes: notes });
    setChanging(null);
    if (error) {
      const { message, field } = splitActionError(error);
      if (field === 'resolutionNotes') setNotesError(message); else toast.error(message);
      return;
    }
    setInc(data);
    toast.success(status === 'IN_REVIEW' ? 'Incidencia en revisión' : status === 'RESOLVED' ? 'Incidencia resuelta' : 'Incidencia cerrada');
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="space-y-6">
        {inc.active && (
          <Alert tone="error" title="El envío está detenido">
            Mientras esta incidencia siga activa, el conductor no puede avanzar las paradas (RN11). Se libera cuando el operador la resuelve o la cierra.
          </Alert>
        )}
        <Card>
          <CardHeader><CardTitle>Descripción</CardTitle><StatusPill status={inc.status!} /></CardHeader>
          <CardBody className="space-y-4">
            <p className="whitespace-pre-wrap text-sm text-ink-900">{inc.description}</p>
            {inc.resolutionNotes && (
              <div className="rounded-input border border-emerald-500/40 bg-emerald-50 px-3 py-2.5 text-sm text-emerald-700">
                <p className="font-semibold">Notas de resolución</p>
                <p className="mt-0.5 whitespace-pre-wrap">{inc.resolutionNotes}</p>
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader><CardTitle>Historial y comentarios</CardTitle></CardHeader>
          <CardBody className="space-y-6">
            <Timeline items={items} orientation="vertical" label="Historial de la incidencia" />
            {closed ? (
              <p className="flex items-center gap-2 rounded-input bg-steel-100 px-3 py-2.5 text-sm text-ink-700"><Lock className="size-4 shrink-0" aria-hidden="true" /> La incidencia está cerrada y ya no admite comentarios.</p>
            ) : (
              <form onSubmit={send} method="post" noValidate className="space-y-3 border-t border-steel-200 pt-5">
                <Field label="Agregar comentario" htmlFor="inc-comment" error={commentError} hint={`${comment.length}/${MAX} caracteres`}>
                  <Textarea id="inc-comment" value={comment} maxLength={MAX} rows={3} onChange={(e) => setComment(e.target.value)} aria-invalid={!!commentError} placeholder={operator ? 'Escribe una actualización para las partes.' : 'Agrega información o responde al operador.'} />
                </Field>
                <div className="flex justify-end"><Button type="submit" loading={posting}>{posting ? 'Enviando…' : 'Enviar comentario'}</Button></div>
              </form>
            )}
          </CardBody>
        </Card>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle>Datos</CardTitle></CardHeader>
          <CardBody className="space-y-4 text-sm">
            {route && <RouteLine size="sm" origin={route.origin} destination={route.destination} />}
            <ul className="space-y-3">
              <Item k="Categoría" v={INCIDENT_CATEGORY_LABELS[inc.category!]} />
              <Item k="PYME" v={inc.requesterName} />
              <Item k="Transportista" v={inc.carrierName} />
              <Item k="Reportada" v={formatDateTime(inc.createdAt)} />
              <Item k={inc.active ? 'Tiempo abierta' : 'Duración'} v={<span className={`font-mono tabular-nums ${ageTone(h, inc.active)}`}>{formatOpenTime(h)}</span>} />
              <Item k="Envío" v={shipmentHref ? <a className="font-medium text-petrol-700 hover:underline" href={shipmentHref}>Ver envío en seguimiento</a> : <span className="font-mono text-xs">{inc.shipmentId?.slice(0, 8)}</span>} />
            </ul>
          </CardBody>
        </Card>

        {operator && (
          <Card>
            <CardHeader><CardTitle>Gestionar incidencia</CardTitle></CardHeader>
            <CardBody className="space-y-4">
              {closed ? (
                <p className="text-sm text-ink-500">La incidencia está cerrada. No hay más acciones disponibles.</p>
              ) : (
                <>
                  <Field label="Notas de resolución" htmlFor="inc-notes" error={notesError} required={inc.status !== 'RESOLVED'} hint={`Obligatorias para resolver. Las verán ambas partes. ${notes.length}/${MAX}`}>
                    <Textarea id="inc-notes" value={notes} maxLength={MAX} rows={4} onChange={(e) => setNotes(e.target.value)} aria-invalid={!!notesError} placeholder="Qué se hizo para resolver el problema." disabled={inc.status === 'RESOLVED'} />
                  </Field>
                  <div className="flex flex-col gap-2">
                    {inc.status === 'OPEN' && <Button variant="outline" onClick={() => change('IN_REVIEW')} loading={changing === 'IN_REVIEW'} disabled={!!changing}>Pasar a revisión</Button>}
                    {(inc.status === 'OPEN' || inc.status === 'IN_REVIEW') && <Button variant="money" onClick={() => change('RESOLVED')} loading={changing === 'RESOLVED'} disabled={!!changing}>Resolver incidencia</Button>}
                    <Button variant="destructive" onClick={() => change('CLOSED')} loading={changing === 'CLOSED'} disabled={!!changing}>Cerrar incidencia</Button>
                  </div>
                  <p className="text-xs text-ink-500">Al resolver o cerrar se libera el envío y se avisa a ambas partes.</p>
                </>
              )}
            </CardBody>
          </Card>
        )}
      </div>
    </div>
  );
}

function Item({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <li className="flex items-baseline justify-between gap-4">
      <span className="text-ink-500">{k}</span>
      <span className="text-right font-medium text-ink-900">{v ?? '—'}</span>
    </li>
  );
}
