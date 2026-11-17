import { useCallback, useMemo, useState } from 'react';
import { useLiveRefresh } from '@/lib/realtime';
import { actions } from 'astro:actions';
import { MessageSquare, TriangleAlert, CircleCheck, Lock } from 'lucide-react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DescriptionList } from '@/components/ui/description-list';
import DocumentsPanel from '@/features/documents/components/DocumentsPanel';
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
import { OUTCOME_BY_SHIPMENT, OUTCOME_LABEL, ageTone, formatOpenTime, hoursOpen } from '../lib';
import { Select } from '@/components/ui/select';
import type { IncidentView } from '../server';

const MAX = 2000;
type Route = { origin: string; destination: string } | null;

/** Detalle de una incidencia: datos, hilo de comentarios y, para el operador, el panel de gestión. */
export default function IncidentDetail({ initial, orgType, route, shipmentStatus }: { initial: IncidentView; orgType: 'PYME' | 'CARRIER' | 'OPERATOR'; route: Route; shipmentStatus?: string }) {
  const [inc, setInc] = useState(initial);
  const [comment, setComment] = useState('');
  const [commentError, setCommentError] = useState<string | null>(null);
  const [posting, setPosting] = useState(false);
  const [notes, setNotes] = useState(initial.resolutionNotes ?? '');
  const [notesError, setNotesError] = useState<string | null>(null);
  const [changing, setChanging] = useState<string | null>(null);
  const [outcome, setOutcome] = useState('');
  const [outcomeError, setOutcomeError] = useState<string | null>(null);
  const outcomes = (shipmentStatus && OUTCOME_BY_SHIPMENT[shipmentStatus]) || ['RESUME', 'DELIVERED', 'CANCELLED'];
  const savedOutcome = (inc as { shipmentOutcome?: string }).shipmentOutcome;
  // Estado y comentarios en vivo (incident.updated de esta incidencia).
  const reloadIncident = useCallback(async () => {
    const { data } = await actions.incidents.get({ id: initial.id! });
    if (data) setInc(data);
  }, [initial.id]);
  useLiveRefresh({ types: ['incident.updated', 'incident.reported'], match: (e) => e.data.incidentId === initial.id, refresh: reloadIncident, fallbackMs: 60_000 });
  const operator = orgType === 'OPERATOR';
  const closed = inc.status === 'CLOSED';
  const shipmentHref = orgType === 'PYME' ? `/pyme/envios?id=${inc.shipmentId}` : orgType === 'CARRIER' ? `/transportista/envios?id=${inc.shipmentId}` : `/operador/envios/${inc.shipmentId}`;
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
    setOutcomeError(null);
    // RF55: resolver, o cerrar sin haber resuelto, exige notas y el estado final del envío.
    const needsOutcome = status === 'RESOLVED' || (status === 'CLOSED' && inc.status !== 'RESOLVED');
    if (needsOutcome) {
      let bad = false;
      if (!notes.trim()) { setNotesError(status === 'RESOLVED' ? 'Escribe las notas de resolución para resolver la incidencia.' : 'Escribe las notas de resolución para cerrar la incidencia.'); bad = true; }
      if (!outcome) { setOutcomeError('Elige qué pasa con el envío.'); bad = true; }
      if (bad) return;
    }
    const effect = needsOutcome ? ({ RESUME: ' El envío continuará su ruta.', DELIVERED: ' El envío se dará por entregado y se cerrarán sus paradas.', CANCELLED: ' Se cancelarán el envío y la solicitud, y se liberará la flota. No se puede deshacer.' } as Record<string, string>)[outcome] : '';
    const cancelling = needsOutcome && outcome === 'CANCELLED';
    const texts = {
      IN_REVIEW: { title: '¿Pasar la incidencia a revisión?', description: 'Las partes verán que LOGYX ya la está atendiendo. El envío sigue detenido.', confirmLabel: 'Pasar a revisión', tone: undefined },
      RESOLVED: { title: '¿Resolver la incidencia?', description: `Se avisará a ambas partes con tus notas de resolución.${effect}`, confirmLabel: 'Resolver incidencia', tone: cancelling ? ('destructive' as const) : undefined },
      CLOSED: { title: '¿Cerrar la incidencia?', description: `Ya no se podrá comentar. No se puede deshacer.${effect}`, confirmLabel: 'Cerrar incidencia', tone: 'destructive' as const },
    }[status];
    if (!(await confirm(texts))) return;
    setChanging(status);
    const { data, error } = await actions.incidents.changeStatus({ id: inc.id!, status, resolutionNotes: notes, shipmentOutcome: needsOutcome ? (outcome as 'RESUME' | 'DELIVERED' | 'CANCELLED') : undefined });
    setChanging(null);
    if (error) {
      const { message, field } = splitActionError(error);
      if (field === 'resolutionNotes') setNotesError(message); else if (field === 'shipmentOutcome') setOutcomeError(message); else toast.error(message);
      return;
    }
    setInc(data);
    toast.success(status === 'IN_REVIEW' ? 'Incidencia en revisión' : status === 'RESOLVED' ? 'Incidencia resuelta' : 'Incidencia cerrada');
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="space-y-6">
        {inc.active && (
          <Alert tone="error" title="El envío está detenido" action={<Button size="sm" variant="outline" asChild><a href={shipmentHref}>Ver envío</a></Button>}>
            Mientras esta incidencia siga activa, el conductor no puede avanzar las paradas (RN11). Se libera cuando el operador la resuelve o la cierra.
          </Alert>
        )}
        <Card>
          <CardHeader><div><CardTitle>Descripción</CardTitle><CardDescription>Reportada {formatDateTime(inc.createdAt)}</CardDescription></div><StatusPill status={inc.status!} /></CardHeader>
          <CardBody className="space-y-4">
            <p className="whitespace-pre-wrap text-sm text-ink-900">{inc.description}</p>
            {inc.resolutionNotes && (
              <div className="rounded-input border border-emerald-500/40 bg-emerald-50 px-3 py-2.5 text-sm text-emerald-700">
                <p className="font-semibold">Notas de resolución</p>
                <p className="mt-0.5 whitespace-pre-wrap">{inc.resolutionNotes}</p>
                {savedOutcome && OUTCOME_LABEL[savedOutcome] && <p className="mt-2 border-t border-emerald-500/30 pt-2 text-xs"><span className="font-semibold">Estado final del envío:</span> {OUTCOME_LABEL[savedOutcome].label}</p>}
              </div>
            )}
          </CardBody>
        </Card>

        <Card>
          <CardHeader><div><CardTitle>Historial y comentarios</CardTitle><CardDescription>{operator ? 'Lo que escribas lo verán la PYME y el transportista.' : 'Lo que escribas lo verán la otra parte y el operador de LOGYX.'}</CardDescription></div></CardHeader>
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
        <DocumentsPanel shipmentId={inc.shipmentId!} perspective={orgType} status="" readOnly title="Evidencias y documentos del envío" />
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader><CardTitle>Datos</CardTitle></CardHeader>
          <CardBody className="space-y-4 text-sm">
            {route && <RouteLine size="sm" origin={route.origin} destination={route.destination} />}
            <DescriptionList cols={1} items={[
              { label: 'Categoría', value: INCIDENT_CATEGORY_LABELS[inc.category!] },
              { label: 'PYME', value: inc.requesterName },
              { label: 'Transportista', value: inc.carrierName },
              { label: 'Reportada', value: formatDateTime(inc.createdAt) },
              { label: inc.active ? 'Tiempo abierta' : 'Duración', value: <span className={ageTone(h, inc.active)}>{formatOpenTime(h)}</span>, mono: true },
              { label: 'Envío', value: <a className="font-medium text-petrol-700 hover:underline" href={shipmentHref}>{operator ? 'Ver detalle del envío' : 'Ver envío en seguimiento'}</a> },
            ]} />
          </CardBody>
        </Card>

        {operator && (
          <Card>
            <CardHeader><div><CardTitle>Gestionar incidencia</CardTitle><CardDescription>Solo el operador cambia el estado.</CardDescription></div><StatusPill status={inc.status!} /></CardHeader>
            <CardBody className="space-y-4">
              {closed ? (
                <p className="text-sm text-ink-500">La incidencia está cerrada. No hay más acciones disponibles.</p>
              ) : (
                <>
                  <Field label="Notas de resolución" htmlFor="inc-notes" error={notesError} required={inc.status !== 'RESOLVED'} hint={`Obligatorias para resolver o cerrar. Las verán ambas partes. ${notes.length}/${MAX}`}>
                    <Textarea id="inc-notes" value={notes} maxLength={MAX} rows={4} onChange={(e) => setNotes(e.target.value)} aria-invalid={!!notesError} placeholder="Qué se hizo para resolver el problema." disabled={inc.status === 'RESOLVED'} />
                  </Field>
                  {inc.status !== 'RESOLVED' && (
                    <Field label="Qué pasa con el envío" htmlFor="inc-outcome" error={outcomeError} required hint={outcome ? OUTCOME_LABEL[outcome].hint : 'Obligatorio al resolver o cerrar la incidencia.'}>
                      <Select id="inc-outcome" value={outcome} onValueChange={(v) => { setOutcome(v); setOutcomeError(null); }} options={outcomes.map((o) => ({ value: o, label: OUTCOME_LABEL[o].label }))} placeholder="Elige el estado final" invalid={!!outcomeError} />
                    </Field>
                  )}
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
