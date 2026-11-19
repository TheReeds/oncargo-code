import { useState } from 'react';
import { actions } from 'astro:actions';
import { Archive, ArchiveRestore, CalendarClock, CirclePause, CirclePlay, CirclePlus, FilePen, FileStack, MoreHorizontal, Send, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { SectionCard } from '@/components/domain/SectionCard';
import { Can } from '@/lib/permissions/react';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatDateTime, formatTons } from '@/lib/format';
import { splitActionError } from '@/lib/action-error';
import { frequencyText, leadText, nextText, RUN_LABEL, RUN_TONE, scheduleState } from '../labels';
import type { DraftView, TemplateView } from '../server';
import { PublishTemplateDialog } from './PublishTemplateDialog';
import { ScheduleDialog } from './ScheduleDialog';

const route = (t: { originCity?: string; destinationCity?: string }) => `${CITY_LABELS[t.originCity!]} → ${CITY_LABELS[t.destinationCity!]}`;

/**
 * «Plantillas»: cargas que se repiten. Lista con su programación (publica sola o deja borrador), borradores por
 * confirmar y las últimas ocurrencias. Todo lo que escribe va bajo `<Can>`; el Lector ve la lista.
 */
export default function TemplatesManager({ initial, drafts: initialDrafts, today }: { initial: TemplateView[]; drafts: DraftView[]; today: string }) {
  const [templates, setTemplates] = useState(initial);
  const [drafts, setDrafts] = useState(initialDrafts);
  const [view, setView] = useState('active');
  const [scheduling, setScheduling] = useState<TemplateView | null>(null);
  const [publishing, setPublishing] = useState<TemplateView | null>(null);
  const [busyDraft, setBusyDraft] = useState<string | null>(null);

  async function reload() {
    const [t, d] = await Promise.all([actions.templates.list({ includeArchived: true }), actions.templates.drafts()]);
    if (t.data) setTemplates(t.data);
    if (d.data) setDrafts(d.data);
  }

  const active = templates.filter((t) => !t.archived), archived = templates.length - active.length;
  const scheduled = active.filter((t) => t.schedule && !t.schedule.paused && !t.schedule.finished).length;
  const paused = active.filter((t) => t.schedule?.paused).length;
  const rows = templates.filter((t) => (view === 'archived' ? t.archived : !t.archived));

  async function act(label: string, call: () => Promise<{ error?: { message: string } | undefined }>, ok: string) {
    const { error } = await call();
    if (error) return void toast.error(label, error.message);
    toast.success(ok);
    await reload();
  }

  async function archive(t: TemplateView) {
    if (!(await confirm({ title: `¿Archivar «${t.name}»?`, description: 'Deja de publicar sola y sale de la lista. Las solicitudes ya publicadas no cambian y puedes restaurarla cuando quieras.', confirmLabel: 'Archivar plantilla' }))) return;
    await act('No pudimos archivar la plantilla', () => actions.templates.archive({ id: t.id! }), 'Plantilla archivada');
  }
  async function unschedule(t: TemplateView) {
    if (!(await confirm({ title: '¿Quitar la programación?', description: `«${t.name}» deja de publicarse sola. La plantilla y su historial se quedan.`, confirmLabel: 'Quitar programación' }))) return;
    await act('No pudimos quitar la programación', () => actions.templates.unschedule({ id: t.id! }), 'Programación quitada');
  }
  async function publishDraft(d: DraftView) {
    setBusyDraft(d.runId!);
    const { data, error } = await actions.templates.publishDraft({ runId: d.runId! });
    setBusyDraft(null);
    if (error) {
      const f = splitActionError(error);
      toast.error('No pudimos publicar el borrador', f.message);
      await reload();
      return;
    }
    toast.success('Solicitud publicada');
    window.location.href = `/pyme/solicitudes/${(data as { requestId?: string }).requestId}`;
  }
  async function discardDraft(d: DraftView) {
    if (!(await confirm({ title: '¿Descartar este borrador?', description: `No se publicará la carga del ${formatDate(d.pickupDate)}. La plantilla sigue programada.`, confirmLabel: 'Descartar borrador', tone: 'destructive' }))) return;
    setBusyDraft(d.runId!);
    await act('No pudimos descartar el borrador', () => actions.templates.discardDraft({ runId: d.runId! }), 'Borrador descartado');
    setBusyDraft(null);
  }

  if (templates.length === 0)
    return (
      <Card>
        <EmptyState
          icon={FileStack}
          title="Aún no tienes plantillas"
          description="Guarda una carga que repites como plantilla y publícala en un clic, o prográmala para que se publique sola. Al publicar, elige «Guardar como plantilla»; también puedes hacerlo desde el detalle de una solicitud."
          action={<Can fallback={null}><Button asChild><a href="/pyme/solicitudes/nueva"><CirclePlus />Crear desde una solicitud nueva</a></Button></Can>}
        />
      </Card>
    );

  return (
    <div className="min-w-0 space-y-5">
      <KpiGrid>
        <KpiTile label="Plantillas activas" value={active.length} tone="petrol" icon={FileStack} context={archived ? `${archived} ${archived === 1 ? 'archivada' : 'archivadas'}` : 'listas para publicar'} />
        <KpiTile label="Programadas" value={scheduled} tone="money" icon={CalendarClock} context="se publican solas" />
        <KpiTile label="Pausadas" value={paused} tone={paused ? 'warn' : 'petrol'} icon={CirclePause} context={paused ? 'no se publican hasta reanudarlas' : 'ninguna en pausa'} />
        <KpiTile label="Borradores por confirmar" value={drafts.length} tone={drafts.length ? 'warn' : 'petrol'} icon={FilePen} context={drafts.length ? 'revisa y publica' : 'nada pendiente'} />
      </KpiGrid>
      {drafts.length > 0 && (
        <SectionCard flush icon={FilePen} tone="warn" title="Borradores por confirmar" count={drafts.length} description="Se generaron desde una programación y esperan tu visto bueno.">
          <div className="divide-y divide-steel-200">
            {drafts.map((d) => (
              <div key={d.runId} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <p className="truncate font-medium text-ink-900">{d.templateName}</p>
                  <p className="text-sm text-ink-700">{route(d)} · {CARGO_LABELS[d.cargoType!]} · {formatTons(d.weightKg)} · Recojo <span className="font-mono">{formatDate(d.pickupDate)}</span></p>
                </div>
                <Can fallback={null}>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => discardDraft(d)} disabled={busyDraft === d.runId}>Descartar</Button>
                    <Button size="sm" loading={busyDraft === d.runId} onClick={() => publishDraft(d)}><Send />Publicar solicitud</Button>
                  </div>
                </Can>
              </div>
            ))}
          </div>
        </SectionCard>
      )}

      <QuickFilters label="Estado de las plantillas" value={view} onValueChange={setView}
        options={[{ value: 'active', label: 'Activas', count: active.length }, { value: 'archived', label: 'Archivadas', count: archived }]} />

      <Card className="min-w-0 overflow-hidden">
        {rows.length === 0 ? (
          <EmptyState title={view === 'archived' ? 'No hay plantillas archivadas' : 'No hay plantillas activas'} description="Las plantillas archivadas se pueden restaurar cuando las necesites." />
        ) : (
          <Table label="Tus plantillas" stack>
            <thead>
              <tr><Th>Plantilla</Th><Th>Carga</Th><Th>Programación</Th><Th>Última publicación</Th><Th><span className="sr-only">Acciones</span></Th></tr>
            </thead>
            <tbody>
              {rows.map((t) => {
                const st = scheduleState(t);
                const s = t.schedule;
                const last = t.recentRuns?.[0];
                return (
                  <Tr key={t.id}>
                    <Td primary>
                      <div className="font-medium text-ink-900">{t.name}</div>
                      <div className="text-sm text-ink-700">{route(t)}</div>
                    </Td>
                    <Td label="Carga"><div><div>{CARGO_LABELS[t.cargoType!]}</div><div className="font-mono text-xs text-ink-500">{formatTons(t.weightKg)}</div></div></Td>
                    <Td label="Programación">
                      <div className="space-y-1">
                        <Badge tone={st.tone}>{st.label}</Badge>
                        {s && !t.archived && (
                          <div className="text-xs text-ink-700">
                            <div>{frequencyText(s)} · {leadText(s.leadDays)}</div>
                            {nextText(s) && <div className="font-mono text-ink-500">{nextText(s)}</div>}
                          </div>
                        )}
                        {last && last.status !== 'PUBLISHED' && (
                          <div className="text-xs"><Badge tone={RUN_TONE[last.status!] ?? 'neutral'}>{RUN_LABEL[last.status!] ?? last.status}</Badge>{last.errorMessage && <span className="ml-1 text-ink-700">{last.errorMessage}</span>}</div>
                        )}
                      </div>
                    </Td>
                    <Td label="Última publicación" className="whitespace-nowrap text-sm">{t.lastPublishedAt ? formatDateTime(t.lastPublishedAt) : <span className="text-ink-500">Nunca</span>}</Td>
                    <Td actions className="text-right">
                      <Can fallback={null}>
                        {!t.archived && <Button size="sm" variant="outline" onClick={() => setPublishing(t)}><Send />Publicar ahora<span className="sr-only"> {t.name}</span></Button>}
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild><Button variant="ghost" size="icon" className="flex-none!" aria-label={`Más acciones de ${t.name}`}><MoreHorizontal /></Button></DropdownMenuTrigger>
                          <DropdownMenuContent>
                            {!t.archived && <DropdownMenuItem onSelect={() => { window.location.href = `/pyme/solicitudes/nueva?plantilla=${t.id}`; }}><FilePen />Abrir en el formulario</DropdownMenuItem>}
                            {!t.archived && <DropdownMenuItem onSelect={() => setScheduling(t)}><CalendarClock />{s ? 'Cambiar programación' : 'Programar'}</DropdownMenuItem>}
                            {s && !t.archived && !s.finished && (s.paused
                              ? <DropdownMenuItem onSelect={() => act('No pudimos reanudar la programación', () => actions.templates.resume({ id: t.id! }), 'Programación reanudada')}><CirclePlay />Reanudar</DropdownMenuItem>
                              : <DropdownMenuItem onSelect={() => act('No pudimos pausar la programación', () => actions.templates.pause({ id: t.id! }), 'Programación pausada')}><CirclePause />Pausar</DropdownMenuItem>)}
                            {s && !t.archived && <DropdownMenuItem destructive onSelect={() => unschedule(t)}><Trash2 />Quitar programación</DropdownMenuItem>}
                            {t.archived
                              ? <DropdownMenuItem onSelect={() => act('No pudimos restaurar la plantilla', () => actions.templates.restore({ id: t.id! }), 'Plantilla restaurada')}><ArchiveRestore />Restaurar</DropdownMenuItem>
                              : <DropdownMenuItem destructive onSelect={() => archive(t)}><Archive />Archivar</DropdownMenuItem>}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </Can>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>
      <ReadOnlyNotice>No puedes publicar ni programar plantillas.</ReadOnlyNotice>
      <Alert tone="info">Una carga programada se publica con las reglas del día: precio sugerido, verificación de tu empresa y datos de contacto. Si algo falla, te avisamos y no se reintenta.</Alert>
      {scheduling && <ScheduleDialog template={scheduling} today={today} onClose={() => setScheduling(null)} onSaved={() => { setScheduling(null); void reload(); }} />}
      {publishing && <PublishTemplateDialog template={publishing} today={today} onClose={() => setPublishing(null)} />}
    </div>
  );
}
