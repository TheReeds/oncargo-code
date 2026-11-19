import { useCallback, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { CalendarRange, ChevronLeft, ChevronRight, FileWarning, Gavel, Lock, Route, Truck, UserRoundPlus, Users } from 'lucide-react';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/form-controls';
import { QuickFilters } from '@/components/ui/quick-filters';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Combobox } from '@/components/ui/combobox';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { Table, Th } from '@/components/ui/table';
import { EmptyState } from '@/components/domain/EmptyState';
import { StatusPill } from '@/components/domain/StatusPill';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { useLiveRefresh } from '@/lib/realtime';
import { Can } from '@/lib/permissions/react';
import { LIMITS } from '@/lib/limits';
import { addDays, dayRange, limaToday, weekdayShort } from '@/lib/lima-date';
import { formatDate } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Schemas, ShipmentView } from '@/lib/api/server';
import AssignSheet, { routeOf } from './AssignSheet';

type Schedule = Schemas['ScheduleView'];
type Row = Schemas['ScheduleRowView'];
type Slot = Schemas['SlotView'];

export const AGENDA_DAYS = [7, 14] as const;

/** Cada compromiso se distingue por ícono y palabra, no solo por color. */
const KIND: Record<string, { label: string; icon: typeof Truck; cls: string }> = {
  SHIPMENT: { label: 'Envío', icon: Truck, cls: 'border-petrol-700/40 bg-petrol-50 text-petrol-700' },
  BID: { label: 'Oferta pendiente', icon: Gavel, cls: 'border-dashed border-amber-500/60 bg-amber-50 text-amber-700' },
  TRIP: { label: 'Viaje', icon: Route, cls: 'border-emerald-500/40 bg-emerald-50 text-emerald-700' },
};
const kindOf = (k?: string) => KIND[k ?? ''] ?? { label: 'Compromiso', icon: CalendarRange, cls: 'border-steel-200 bg-steel-100 text-ink-700' };

/** Leyenda visible del calendario (misma tabla de íconos y colores que las barras). */
function Legend() {
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-ink-700" aria-label="Leyenda de la agenda" data-testid="agenda-legend">
      {Object.entries(KIND).map(([k, v]) => <li key={k} className="flex items-center gap-1.5"><span className={cn('inline-flex size-5 items-center justify-center rounded border', v.cls)}><v.icon className="size-3" aria-hidden="true" /></span>{v.label}</li>)}
      <li className="flex items-center gap-1.5"><span className="inline-flex size-5 items-center justify-center rounded border border-red-600/40 bg-red-50 text-red-700"><FileWarning className="size-3" aria-hidden="true" /></span>Vencimiento de documento</li>
      <li className="flex items-center gap-1.5"><span className="inline-flex size-5 items-center justify-center rounded border border-steel-200 bg-steel-100 text-ink-500"><Lock className="size-3" aria-hidden="true" /></span>No disponible</li>
    </ul>
  );
}

function Bar({ slot, day, first }: { slot: Slot; day: string; first: boolean }) {
  const k = kindOf(slot.kind);
  const start = slot.from === day;
  const end = slot.to === day;
  return (
    <li
      className={cn('flex min-w-0 items-center gap-1 border px-1.5 py-1 text-[11px] leading-tight', k.cls, start || first ? 'rounded-l-md' : 'rounded-l-none border-l-0', end ? 'rounded-r-md' : 'rounded-r-none border-r-0')}
      data-kind={slot.kind}
    >
      <k.icon className="size-3 shrink-0" aria-hidden="true" />
      {start || first
        ? <span className="min-w-0 truncate" title={`${k.label}: ${slot.label}`}><span className="font-semibold">{k.label.split(' ')[0]}</span> · {slot.label}</span>
        : <span className="sr-only">{k.label} continúa: {slot.label}</span>}
      {(start || first) && <span className="sr-only"> del {formatDate(slot.from)} al {formatDate(slot.to)}</span>}
    </li>
  );
}

function AgendaTable({ rows, days, today, kind }: { rows: Row[]; days: string[]; today: string; kind: 'vehicles' | 'drivers' }) {
  const what = kind === 'vehicles' ? 'Vehículo' : 'Conductor';
  return (
    <Table label={`Agenda de ${kind === 'vehicles' ? 'vehículos' : 'conductores'}`} className="min-w-max border-separate border-spacing-0">
      <caption className="sr-only">Agenda de {kind === 'vehicles' ? 'vehículos' : 'conductores'} del {formatDate(days[0])} al {formatDate(days[days.length - 1])}: cada celda lista los compromisos de ese día.</caption>
      <thead>
        <tr>
          <Th scope="col" className="sticky left-0 z-10 w-44 bg-steel-100 sm:w-52">{what}</Th>
          {days.map((d) => (
            <Th key={d} scope="col" className={cn('w-36 text-center', d === today && 'bg-petrol-50 text-petrol-700')} aria-current={d === today ? 'date' : undefined}>
              <span className="block capitalize">{weekdayShort(d)}</span>
              <span className="block font-mono text-sm normal-case tabular-nums">{formatDate(d)}</span>
              {d === today && <span className="block text-[10px] font-semibold">Hoy</span>}
            </Th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id} data-testid="agenda-row" className={cn(r.blocked && 'bg-steel-100/60')}>
            <th scope="row" className="sticky left-0 z-10 border-b border-steel-200 bg-surface px-3 py-2 text-left align-top">
              <span className={cn('block text-sm font-semibold text-ink-900', kind === 'vehicles' && 'font-mono')}>{r.label}</span>
              {r.detail && <span className="block text-xs font-normal text-ink-500">{r.detail}</span>}
              {r.blocked && <span className="mt-0.5 inline-flex items-center gap-1 text-xs font-medium text-amber-700" data-testid="agenda-blocked"><Lock className="size-3" aria-hidden="true" />{r.blockedReason ?? 'No disponible'}</span>}
            </th>
            {days.map((d, i) => {
              const slots = (r.slots ?? []).filter((s) => s.from && s.to && s.from <= d && d <= s.to);
              const exp = (r.expiries ?? []).filter((x) => x.on && (x.on === d || (i === 0 && x.on < d)));
              return (
                <td key={d} className={cn('h-14 border-b border-l border-steel-200 p-1 align-top', d === today && 'bg-petrol-50/50', r.blocked && 'bg-[repeating-linear-gradient(135deg,transparent_0_6px,var(--color-steel-200)_6px_7px)]')}>
                  {(slots.length > 0 || exp.length > 0) && (
                    <ul className="space-y-0.5">
                      {slots.map((s, j) => <Bar key={`${s.kind}-${s.refId}-${j}`} slot={s} day={d} first={i === 0} />)}
                      {exp.map((x, j) => (
                        <li key={`e${j}`} className={cn('flex items-center gap-1 rounded-md border px-1.5 py-1 text-[11px] leading-tight', x.expired ? 'border-red-600/40 bg-red-50 text-red-700' : 'border-amber-500/50 bg-amber-50 text-amber-700')} data-testid="agenda-expiry">
                          <FileWarning className="size-3 shrink-0" aria-hidden="true" />
                          <span className="min-w-0 truncate" title={x.label}>{x.expired ? 'Venció' : 'Vence'}: {x.label}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </td>
              );
            })}
          </tr>
        ))}
      </tbody>
    </Table>
  );
}

type BatchResult = { id: string; ok: boolean; message: string };

/** «Asignar en lote»: un conductor para varios envíos sin conductor; una llamada por envío y un resumen por fila (no se aborta al primer error). */
function BatchDialog({ shipments, drivers, open, onOpenChange, onDone }: { shipments: ShipmentView[]; drivers: { value: string; label: string; hint?: string; disabled?: boolean }[]; open: boolean; onOpenChange: (o: boolean) => void; onDone: (assignedIds: string[]) => void }) {
  const [driverId, setDriverId] = useState('');
  const [reason, setReason] = useState('');
  const [results, setResults] = useState<BatchResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const needsReason = shipments.some((s) => s.status === 'IN_TRANSIT');

  async function run(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!driverId) { setError('Elige el conductor.'); return; }
    if (needsReason && reason.trim().length < 3) { setError('Indica el motivo para los envíos que ya se recogieron.'); return; }
    setError(null);
    setBusy(true);
    const out: BatchResult[] = [];
    for (const s of shipments) {
      const { error: err } = await actions.fleet.assign({ shipmentId: s.id!, driverId, reason: s.status === 'IN_TRANSIT' ? reason.trim() : undefined });
      out.push({ id: s.id!, ok: !err, message: err ? splitActionError(err).message : 'Asignado' });
      setResults([...out]);
    }
    setBusy(false);
    const okIds = out.filter((r) => r.ok).map((r) => r.id);
    if (okIds.length) { toast.success(okIds.length === out.length ? 'Envíos asignados' : `${okIds.length} de ${out.length} envíos asignados`); onDone(okIds); }
  }
  const finished = results && results.length === shipments.length && !busy;
  return (
    <Dialog open={open} onOpenChange={(o) => !busy && onOpenChange(o)} title="Asignar en lote" description={`Un mismo conductor para ${shipments.length} ${shipments.length === 1 ? 'envío' : 'envíos'} sin conductor. Cada envío se valida por separado.`}
      footer={finished ? <Button onClick={() => onOpenChange(false)}>Cerrar</Button> : <><Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>Volver</Button><Button type="submit" form="batch-form" loading={busy}>{busy ? 'Asignando…' : 'Asignar conductor'}</Button></>}>
      <form id="batch-form" onSubmit={run} noValidate method="post" className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        {!results && (
          <>
            <Field label="Conductor" htmlFor="batch-driver"><Combobox id="batch-driver" value={driverId} onValueChange={setDriverId} options={drivers} placeholder="Elige un conductor" searchPlaceholder="Buscar conductor…" /></Field>
            {needsReason && <Field label="Motivo (envíos ya recogidos)" htmlFor="batch-reason" hint="La PYME lo verá."><Textarea id="batch-reason" value={reason} maxLength={LIMITS.shipmentReason} onChange={(e) => setReason(e.target.value)} rows={3} /></Field>}
          </>
        )}
        <ul className="divide-y divide-steel-200 rounded-input border border-steel-200 text-sm" aria-label="Envíos del lote" data-testid="batch-results" aria-live="polite">
          {shipments.map((s) => {
            const r = results?.find((x) => x.id === s.id);
            return (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
                <span className="min-w-0"><span className="font-medium">{routeOf(s)}</span> <span className="text-xs text-ink-500">· {s.code ?? ''}</span></span>
                {r ? <span className={cn('text-xs font-medium', r.ok ? 'text-emerald-700' : 'text-red-700')}>{r.ok ? 'Asignado' : `No se pudo: ${r.message}`}</span> : <span className="text-xs text-ink-500">{busy ? 'En espera…' : 'Pendiente'}</span>}
              </li>
            );
          })}
        </ul>
      </form>
    </Dialog>
  );
}

/** Envíos activos sin conductor: «Asignar» uno (AssignSheet) o varios a un mismo conductor (lote). */
function Unassigned({ items, drivers, onAssign, onBatchDone }: { items: ShipmentView[]; drivers: { value: string; label: string; hint?: string; disabled?: boolean }[]; onAssign: (s: ShipmentView) => void; onBatchDone: (ids: string[]) => void }) {
  const [sel, setSel] = useState<string[]>([]);
  // Los envíos del lote se congelan al abrir: la lista de «Sin conductor» se refresca sola mientras se asigna.
  const [batch, setBatch] = useState<ShipmentView[] | null>(null);
  if (items.length === 0) return null;
  return (
    <Card data-testid="unassigned">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><UserRoundPlus className="size-4 text-amber-700" aria-hidden="true" />Sin conductor <span className="font-mono text-ink-500">({items.length})</span></CardTitle>
        <Can><Button size="sm" variant="outline" disabled={sel.length < 2} onClick={() => setBatch(items.filter((s) => sel.includes(s.id!)))}>Asignar en lote{sel.length > 0 ? ` (${sel.length})` : ''}</Button></Can>
      </CardHeader>
      <ul className="divide-y divide-steel-200">
        {items.map((s) => (
          <li key={s.id} className="flex flex-wrap items-center gap-3 px-4 py-2.5 sm:px-5" data-testid="unassigned-row">
            <Can><Checkbox id={`un-${s.id}`} checked={sel.includes(s.id!)} onCheckedChange={(c) => setSel((l) => (c === true ? [...l, s.id!] : l.filter((x) => x !== s.id)))} aria-label={`Elegir el envío ${routeOf(s)} para asignar en lote`} /></Can>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-ink-900">{routeOf(s)}</p>
              <p className="text-xs text-ink-500">{s.code ? <span className="font-mono">{s.code}</span> : null}{s.code ? ' · ' : ''}recojo {formatDate(s.pickupDate)}{s.vehiclePlate ? <> · <span className="font-mono">{s.vehiclePlate}</span></> : null}</p>
            </div>
            <StatusPill status={s.status!} />
            <Can><Button size="sm" onClick={() => onAssign(s)}><UserRoundPlus aria-hidden="true" />Asignar<span className="sr-only"> conductor al envío {routeOf(s)}</span></Button></Can>
          </li>
        ))}
      </ul>
      {batch && <BatchDialog shipments={batch} drivers={drivers} open onOpenChange={(o) => { if (!o) setBatch(null); }} onDone={(ids) => { onBatchDone(ids); setSel((l) => l.filter((x) => !ids.includes(x))); }} />}
    </Card>
  );
}

/**
 * Agenda de flota (2.13): filas = vehículos o conductores, columnas = días (7 o 14), con envíos, ofertas pendientes y viajes como
 * barras con ícono y texto, vencimientos de documentos como marcador del día y filas bloqueadas con su motivo. Tabla semántica
 * dentro de una región enfocable con scroll horizontal contenido. `from` y `days` viven en la URL (`?desde=&dias=`).
 */
export default function FleetAgenda({ initial, from, days, unassigned: unassignedInitial, driverOptions }: { initial: Schedule; from: string; days: number; unassigned: ShipmentView[]; driverOptions: { value: string; label: string; hint?: string; disabled?: boolean }[] }) {
  const [schedule, setSchedule] = useState(initial);
  const [unassigned, setUnassigned] = useState(unassignedInitial);
  const [tab, setTab] = useState<'vehicles' | 'drivers'>('vehicles');
  const [assigning, setAssigning] = useState<ShipmentView | null>(null);
  const today = limaToday();
  const dayList = useMemo(() => dayRange(from, days), [from, days]);
  const to = addDays(from, days - 1);

  const go = (nextFrom: string, nextDays = days) => {
    const q = new URLSearchParams();
    if (nextFrom !== today) q.set('desde', nextFrom);
    if (nextDays !== 7) q.set('dias', String(nextDays));
    window.location.href = `${window.location.pathname}${q.toString() ? `?${q}` : ''}`;
  };

  const refresh = useCallback(async () => {
    const [s, u] = await Promise.all([actions.fleet.schedule({ from, to }), actions.fleet.unassigned({})]);
    if (s.data) setSchedule(s.data as Schedule);
    if (u.data) setUnassigned(u.data as ShipmentView[]);
  }, [from, to]);
  useLiveRefresh({ types: ['shipment.driver_assigned', 'shipment.status_changed', 'bid.placed', 'bid.accepted', 'trip_booking.updated'], refresh, fallbackMs: 60_000 });

  const rows = (tab === 'vehicles' ? schedule.vehicles : schedule.drivers) ?? [];
  const range = `${formatDate(from)} al ${formatDate(to)}`;

  return (
    <div className="space-y-5">
      <Unassigned items={unassigned} drivers={driverOptions} onAssign={setAssigning} onBatchDone={() => refresh()} />

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-steel-200 px-4 py-3 sm:px-5">
          <Tabs value={tab} onValueChange={(v) => setTab(v as typeof tab)}>
            <TabsList aria-label="Ver agenda de">
              <TabsTrigger value="vehicles" aria-controls={undefined}><Truck className="size-4" aria-hidden="true" />Vehículos <span className="font-mono text-xs text-ink-500">{schedule.vehicles?.length ?? 0}</span></TabsTrigger>
              <TabsTrigger value="drivers" aria-controls={undefined}><Users className="size-4" aria-hidden="true" />Conductores <span className="font-mono text-xs text-ink-500">{schedule.drivers?.length ?? 0}</span></TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="flex flex-wrap items-center gap-2">
            <QuickFilters label="Días a mostrar" value={String(days)} onValueChange={(v) => go(from, Number(v))} options={AGENDA_DAYS.map((d) => ({ value: String(d), label: `${d} días` }))} />
            <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Navegar la agenda">
              <Button size="sm" variant="outline" onClick={() => go(addDays(from, -7))}><ChevronLeft aria-hidden="true" />Semana anterior</Button>
              <Button size="sm" variant="outline" disabled={from === today} onClick={() => go(today)}>Hoy</Button>
              <Button size="sm" variant="outline" onClick={() => go(addDays(from, 7))}>Semana siguiente<ChevronRight aria-hidden="true" /></Button>
            </div>
          </div>
        </div>
        <div className="space-y-2 border-b border-steel-200 px-4 py-3 sm:px-5">
          <p className="text-sm font-medium text-ink-900" aria-live="polite" data-testid="agenda-range">{range}</p>
          <Legend />
        </div>
        {rows.length === 0 ? (
          <EmptyState compact icon={tab === 'vehicles' ? Truck : Users} title={tab === 'vehicles' ? 'Aún no tienes vehículos' : 'Aún no tienes conductores'} description="Regístralos en Flota para ver aquí su agenda." action={<Button asChild variant="outline"><a href="/transportista/flota">Ir a Flota</a></Button>} />
        ) : (
          <AgendaTable rows={rows} days={dayList} today={today} kind={tab} />
        )}
      </Card>

      <AssignSheet shipment={assigning} onClose={() => setAssigning(null)} onAssigned={(s) => { setUnassigned((l) => l.filter((x) => x.id !== s.id)); setAssigning(null); refresh(); }} />
    </div>
  );
}
