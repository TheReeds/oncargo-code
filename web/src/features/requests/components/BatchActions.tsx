import { useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Ban, CalendarClock, Copy, X } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { DatePicker } from '@/components/ui/date-picker';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { confirm } from '@/components/ui/confirm-dialog';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import type { Schemas } from '@/lib/api/server';

export type BatchResult = Schemas['BatchResultView'];
type Done = { verb: string; result: BatchResult };

const todayLima = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** Resultado por solicitud de una acción masiva: lo que funcionó y, por cada fallo, su motivo (las reglas se evalúan por ítem). */
export function BatchResultDialog({ done, labels, onClose }: { done: Done; labels: Record<string, string>; onClose: () => void }) {
  const { result } = done;
  const failed = (result.items ?? []).filter((i) => !i.ok);
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()} title={result.failed ? 'Terminamos, con avisos' : 'Listo'}
      description={`${done.verb}: ${plural(result.succeeded ?? 0, 'solicitud', 'solicitudes')} de ${result.total}. ${result.failed ? 'Las demás no cambiaron.' : ''}`}>
      <div className="space-y-4">
        {failed.length > 0 && (
          <Alert tone="warning" title={`${plural(failed.length, 'solicitud no se pudo', 'solicitudes no se pudieron')} procesar`}>
            <ul className="mt-1 space-y-1">
              {failed.map((i) => <li key={i.requestId}><span className="font-medium">{labels[i.requestId!] ?? 'Solicitud'}</span>: {i.message}</li>)}
            </ul>
          </Alert>
        )}
        <div className="flex justify-end border-t border-steel-200 pt-3"><Button onClick={onClose}>Cerrar</Button></div>
      </div>
    </Dialog>
  );
}

/**
 * Barra de acciones masivas de «Mis solicitudes» (roadmap 2.10): cancelar con un motivo común, reprogramar la fecha y
 * duplicar para la semana siguiente. No es atómico: cada solicitud se procesa sola y el resultado dice cuáles fallaron.
 */
export function BatchBar({ ids, labels, onClear }: { ids: string[]; labels: Record<string, string>; onClear: () => void }) {
  const [dialog, setDialog] = useState<'cancel' | 'reschedule' | null>(null);
  const [done, setDone] = useState<Done | null>(null);
  const [busy, setBusy] = useState(false);
  const dupKey = useRef('');
  useEffect(() => { dupKey.current = crypto.randomUUID(); }, [ids.join(',')]); // eslint-disable-line react-hooks/exhaustive-deps

  async function duplicate() {
    if (!(await confirm({
      title: `¿Duplicar ${plural(ids.length, 'solicitud', 'solicitudes')} para la semana siguiente?`,
      description: 'Publicamos una copia de cada una con el recojo el mismo día de la semana siguiente. Se aplican las mismas reglas que al publicar: si alguna falla, las demás igual se crean.',
      confirmLabel: 'Duplicar solicitudes',
    }))) return;
    setBusy(true);
    const { data, error } = await actions.requests.batchDuplicate({ ids, idempotencyKey: dupKey.current });
    setBusy(false);
    if (error) return void setDone({ verb: 'Duplicadas', result: { total: ids.length, succeeded: 0, failed: ids.length, items: ids.map((id) => ({ requestId: id, ok: false, message: splitActionError(error).message })) } });
    setDone({ verb: 'Duplicadas', result: data });
  }

  return (
    <>
      <div role="region" aria-label="Acciones sobre las solicitudes seleccionadas" className="flex flex-wrap items-center gap-2 border-b border-steel-200 bg-petrol-50 px-4 py-2.5">
        <span className="mr-auto text-sm font-medium text-petrol-800" aria-live="polite">{plural(ids.length, 'seleccionada', 'seleccionadas')}</span>
        <Button size="sm" variant="outline" onClick={() => setDialog('cancel')}><Ban />Cancelar</Button>
        <Button size="sm" variant="outline" onClick={() => setDialog('reschedule')}><CalendarClock />Reprogramar</Button>
        <Button size="sm" variant="outline" loading={busy} onClick={() => void duplicate()}><Copy />Duplicar para la semana siguiente</Button>
        <Button size="sm" variant="ghost" onClick={onClear}><X />Limpiar selección</Button>
      </div>
      <ReasonDialog
        open={dialog === 'cancel'} onOpenChange={(o) => !o && setDialog(null)}
        title={`¿Cancelar ${plural(ids.length, 'solicitud', 'solicitudes')}?`}
        description="Se cerrarán las subastas y los transportistas con ofertas recibirán un aviso con el motivo. Las que ya no se puedan cancelar quedan como están. No se puede deshacer."
        label="Motivo común" hint="Opcional. Lo verán los transportistas con ofertas." placeholder="Ej. Cambió la fecha de despacho." maxLength={LIMITS.requestCancelReason}
        confirmLabel="Cancelar solicitudes" tone="destructive"
        onSubmit={async (reason) => {
          const { data, error } = await actions.requests.batchCancel({ ids, reason: reason || undefined });
          if (error) return splitActionError(error);
          setDone({ verb: 'Canceladas', result: data });
          return null;
        }}
      />
      {dialog === 'reschedule' && <RescheduleManyDialog count={ids.length} ids={ids} onClose={() => setDialog(null)} onDone={(r) => { setDialog(null); setDone({ verb: 'Reprogramadas', result: r }); }} />}
      {done && <BatchResultDialog done={done} labels={labels} onClose={() => window.location.reload()} />}
    </>
  );
}

function RescheduleManyDialog({ ids, count, onClose, onDone }: { ids: string[]; count: number; onClose: () => void; onDone: (r: BatchResult) => void }) {
  const [date, setDate] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const today = todayLima();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!date) return void setError('Selecciona la nueva fecha de recojo.');
    if (date < today) return void setError('Elige una fecha de hoy en adelante.');
    setBusy(true);
    const { data, error: err } = await actions.requests.batchReschedule({ ids, pickupDate: date });
    setBusy(false);
    if (err) return void setError(splitActionError(err).message);
    onDone(data);
  }

  return (
    <Dialog open onOpenChange={(o) => !busy && !o && onClose()} title={`Reprogramar ${plural(count, 'solicitud', 'solicitudes')}`}
      description="Cambia la fecha de recojo de las solicitudes publicadas. Las que ya tienen ofertas no se reprograman: cancélalas y vuelve a publicarlas.">
      <form onSubmit={submit} noValidate method="post" className="space-y-4">
        <Field label="Nueva fecha de recojo" htmlFor="batch-date" error={error}>
          <DatePicker id="batch-date" value={date} onValueChange={(v) => { setDate(v); setError(null); }} min={today} invalid={!!error} />
        </Field>
        <div className="flex flex-col-reverse gap-2 border-t border-steel-200 pt-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" loading={busy}>Reprogramar solicitudes</Button>
        </div>
      </form>
    </Dialog>
  );
}
