import { useEffect, useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { toFormErrors, isVerificationFailure, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { partitionFleet, requestNeed } from '@/features/fleet/compatibility';
import { UnfitList } from '@/features/fleet/components/UnfitList';
import { VerificationBlock } from '@/components/domain/VerificationBlock';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { MoneyInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { TrustScore } from '@/components/domain/TrustScore';
import { BarChart3, ChevronRight, MessageSquare, TrendingDown, Wallet } from 'lucide-react';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Badge } from '@/components/ui/badge';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { CARGO_LABELS, formatDate, formatMoney, formatNumber, formatTons, VEHICLE_LABELS } from '@/lib/format';
import { formatRate, priceBreakdown } from '@/lib/pricing';
import { loadCommissionRate } from '../commission';
import { shortIssue } from '@/features/fleet/documents';
import { clippedToFloor, recalled, remember, shortcutAmount, SHORTCUT_LABEL, type Shortcut } from '../quick-bid';
import type { BidView, RequestView, VehicleView } from '@/lib/api/server';

type Elig = {
  windowFrom?: string; windowTo?: string;
  vehicles?: { vehicleId?: string; eligible?: boolean; blockerCode?: string | null; blockerMessage?: string | null; freeFrom?: string | null; busy?: { kind?: string; label?: string; from?: string; to?: string }[]; warnings?: { code?: string; message?: string }[] }[];
};
type VehicleElig = NonNullable<Elig['vehicles']>[number];
type Lane = { samples?: number; windowDays?: number; median?: number; low?: number; high?: number };

const isDocWarning = (code?: string) => code === 'LOGYX-FLT-007' || code === 'LOGYX-FLT-008';

const range = (from?: string, to?: string) => (from && to && from !== to ? `${formatDate(from)} a ${formatDate(to)}` : formatDate(from));

/** Texto corto del estado de agenda de un vehículo para esta carga (hint del selector). */
function agendaHint(e: VehicleElig | undefined): string | null {
  if (!e) return null;
  if (!e.eligible) {
    if (e.blockerCode === 'LOGYX-CAP-005') return e.freeFrom ? `Ocupado · libre el ${formatDate(e.freeFrom)}` : 'Ocupado en esas fechas';
    if (e.blockerCode === 'LOGYX-CAP-007') return 'Demasiadas ofertas en esas fechas';
    // 2.4: el operador activó el bloqueo por documento vencido (LOGYX-FLT-007): «Bloqueado: SOAT vencido el 1 oct».
    if (e.blockerCode === 'LOGYX-FLT-007') return `Bloqueado: ${shortIssue(e.blockerMessage)}`;
    return null;
  }
  const bids = e.busy?.filter((b) => b.kind === 'BID').length ?? 0;
  return bids > 0 ? `${bids} ${bids === 1 ? 'oferta pendiente' : 'ofertas pendientes'} en esas fechas` : 'Libre en esas fechas';
}

/**
 * Hoja de oferta. Oferta rápida (roadmap 1.10): preselecciona el último vehículo (si es apto y está libre; si no, el apto de
 * menor capacidad), recuerda nota y atajo de monto, y NO recarga la página: avisa con `onPlaced`. Con `hasNext`, «Enviar y
 * ofertar la siguiente» encadena cargas con una sola acción por carga.
 */
export default function BidSheet({ request, vehicles, onClose, onAsk, defaultVehicleId, onPlaced, hasNext }: {
  request: RequestView | null; vehicles: VehicleView[]; onClose: () => void; onAsk?: (r: RequestView) => void; defaultVehicleId?: string;
  /** Tras ofertar. `advance`: la persona pidió pasar a la siguiente carga. Sin esto, se recarga la página (compatibilidad). */
  onPlaced?: (bid: BidView, advance: boolean) => void; hasNext?: boolean;
}) {
  const [vehicleId, setVehicleId] = useState('');
  const [amount, setAmount] = useState('');
  const [shortcut, setShortcut] = useState<Shortcut | null>(null);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [blocked, setBlocked] = useState<ActionFailure | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState<'send' | 'next' | null>(null);
  const [elig, setElig] = useState<Elig | null>(null);
  const [rate, setRate] = useState<number | null>(null);
  // 2.8: precio de la ruta (`undefined` = cargando, `null` = sin datos suficientes: no se muestra nada).
  const [lane, setLane] = useState<Lane | null | undefined>(undefined);
  const touchedVehicle = useRef(false);
  const advance = useRef(false);

  const fleet = useMemo(() => (request ? partitionFleet(vehicles, requestNeed(request)) : { fit: [] as VehicleView[], unfit: [] }), [vehicles, request]);
  const eligOf = (id: string) => elig?.vehicles?.find((v) => v.vehicleId === id);
  // Aptos por la matriz y, si la agenda ya cargó, libres para esa fecha (CAP-005/007 los bloquea el servidor).
  const compatible = fleet.fit;
  const bookable = compatible.filter((v) => eligOf(v.id!)?.eligible !== false);
  const agendaBlocked = compatible.filter((v) => eligOf(v.id!)?.eligible === false);

  useEffect(() => { loadCommissionRate().then(setRate); }, []);

  // Al abrir otra carga: recordar lo último, limpiar y pedir la agenda de la flota para esas fechas.
  useEffect(() => {
    touchedVehicle.current = false;
    const last = recalled();
    setVehicleId('');
    setShortcut(null);
    setAmount('');
    setNotes(last.notes);
    setError(null);
    setBlocked(null);
    setErrors({});
    setBusy(null);
    setElig(null);
    setLane(undefined);
    if (!request?.id) return;
    // El atajo recordado rellena el monto (editable); sin datos de la carga queda vacío.
    if (last.shortcut) {
      const v = shortcutAmount(last.shortcut, request.suggestedPrice, request.priceFloor);
      if (v != null) { setShortcut(last.shortcut); setAmount(String(v)); }
    }
    let alive = true;
    actions.marketplace.eligibility({ requestId: request.id }).then(({ data }) => { if (alive && data) setElig(data as Elig); }).catch(() => undefined);
    actions.marketplace.laneStats({ requestId: request.id }).then(({ data }) => { if (alive) setLane((data as Lane | null) ?? null); }).catch(() => alive && setLane(null));
    // Foco en el monto: con vehículo y monto ya puestos, Enter envía.
    const t = window.setTimeout(() => document.getElementById('b-amount')?.focus(), 80);
    return () => { alive = false; window.clearTimeout(t); };
  }, [request?.id]);

  // Vehículo: el del retorno/Smart Load, el último usado, o el apto de menor capacidad. Solo si la persona no eligió aún.
  useEffect(() => {
    if (!request || touchedVehicle.current) return;
    const pool = bookable.length ? bookable : [];
    const last = recalled().vehicleId;
    const pick = (defaultVehicleId && pool.find((v) => v.id === defaultVehicleId))
      || (last && pool.find((v) => v.id === last))
      || [...pool].sort((a, b) => (a.maxWeightKg ?? 0) - (b.maxWeightKg ?? 0))[0];
    setVehicleId(pick?.id ?? '');
  }, [request?.id, elig, vehicles]);

  const amt = Number(amount);
  const floor = request?.priceFloor ?? 0;
  const belowFloor = floor > 0 && amt > 0 && amt < floor;
  const vs = request?.suggestedPrice ? ((amt - request.suggestedPrice) / request.suggestedPrice) * 100 : null;
  const net = rate != null && amt > 0 && !belowFloor ? priceBreakdown(amt, rate) : null;
  const selected = vehicleId ? eligOf(vehicleId) : undefined;

  function pickShortcut(s: Shortcut) {
    const v = shortcutAmount(s, request?.suggestedPrice, request?.priceFloor);
    if (v == null) return;
    setShortcut(s);
    setAmount(String(v));
    setErrors((x) => ({ ...x, amount: '' }));
  }

  async function submit(e: React.FormEvent, goNext = false) {
    e.preventDefault();
    if (!request || busy) return;
    setError(null);
    setBlocked(null);
    const errs: Record<string, string> = {};
    if (!vehicleId) errs.vehicleId = 'Selecciona el vehículo que hará el envío.';
    if (!(amt > 0)) errs.amount = 'Ingresa el monto de tu oferta.';
    else if (belowFloor) errs.amount = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube tu oferta para continuar.`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    advance.current = goNext;
    setBusy(goNext ? 'next' : 'send');
    const { data, error } = await actions.marketplace.placeBid({ requestId: request.id!, vehicleId, amount: amt, notes: notes.trim() || undefined });
    if (error) {
      setBusy(null);
      const r = toFormErrors(error, ['amount', 'vehicleId', 'notes']);
      if (isVerificationFailure(r.failure)) setBlocked(r.failure);
      else if (r.formError) setError(r.formError);
      setErrors(r.errors);
      return;
    }
    remember(vehicleId, notes, shortcut);
    toast.success('Oferta enviada');
    for (const w of (data as BidView).warnings ?? []) {
      if (isDocWarning(w.code)) toast.warning(w.message ?? 'Revisa los documentos del vehículo', 'Renueva el documento y súbelo en Flota.');
      else toast.warning(w.message ?? 'Ese vehículo ya tiene ofertas para esas fechas', 'Si la PYME acepta una, las demás no se podrán cumplir con este vehículo.');
    }
    if (onPlaced) { setBusy(null); onPlaced(data as BidView, goNext); } else window.location.reload();
  }

  const perKm = amt > 0 && request?.distanceKm ? amt / request.distanceKm : null;
  const bids = request?.auction?.bidCount ?? 0;
  const maxBids = request?.auction?.maxBids ?? 5;
  const shortcuts: Shortcut[] = ['suggested', 'm5', 'm10', 'floor'];

  return (
    <Sheet open={!!request} onOpenChange={(o) => !o && !busy && onClose()} title="Enviar oferta" description="Compites en subasta inversa: a menor monto, más opciones de ganar.">
      {request && (
        <form onSubmit={(e) => submit(e, false)} noValidate className="flex min-h-full flex-col">
          <div className="flex-1 space-y-5 p-5">
            <div className="space-y-3 rounded-card border border-steel-200 bg-steel-100/60 p-4">
              <RouteLine origin={request.originCity!} destination={request.destinationCity!} km={request.distanceKm} originSub={`Recojo ${formatDate(request.pickupDate)}`} />
              <div className="flex flex-wrap items-center gap-1.5">
                <Badge tone="neutral">{CARGO_LABELS[request.cargoType!]}</Badge>
                <Badge tone="neutral"><span className="font-mono">{formatTons(request.weightKg)}</span></Badge>
                {request.minVehicleType && <Badge tone="neutral">Mín. {VEHICLE_LABELS[request.minVehicleType]}</Badge>}
                {request.auction?.closesAt && <span className="ml-auto inline-flex items-center gap-2 text-xs text-ink-500">{request.auction.status ? <><span className="font-mono">{bids}/{maxBids}</span> ofertas</> : null}<Countdown closesAt={request.auction.closesAt} /></span>}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-steel-200 pt-3 text-sm">
                <span className="flex items-center gap-1.5 text-ink-700">{request.requesterName ?? 'Solicitante'}<VerifiedBadge verified={request.requesterRucVerified} variant="icon" /></span>
                <TrustScore score={request.requesterTrustScore} variant="meter" className="w-40" />
              </div>
              <div className="grid grid-cols-2 gap-3 border-t border-steel-200 pt-3 text-sm">
                <div><div className="text-xs text-ink-500">Precio sugerido</div><div className="money text-xl">{formatMoney(request.suggestedPrice)}</div></div>
                <div><div className="text-xs text-ink-500">Piso (monto mínimo)</div><div className="font-mono text-xl font-semibold tabular-nums text-ink-900">{formatMoney(request.priceFloor)}</div></div>
              </div>
            </div>

            {lane === undefined ? <div aria-hidden="true" className="lx-skeleton h-4 w-2/3 rounded-input" /> : lane ? (
              <section aria-labelledby="b-lane" className="rounded-input border border-steel-200 px-3 py-2.5 text-sm" data-testid="lane-stats">
                <h3 id="b-lane" className="flex items-center gap-1.5 text-xs font-semibold text-ink-700"><BarChart3 className="size-3.5" aria-hidden="true" />Precio de esta ruta</h3>
                <p className="mt-0.5 text-ink-700">Mediana adjudicada en esta ruta: <span className="font-mono font-semibold tabular-nums text-ink-900">{formatMoney(lane.median)}</span> <span className="text-xs text-ink-500">(últimos {lane.windowDays ?? 90} días, {lane.samples} envíos)</span>.</p>
                <p className="text-xs text-ink-500">Rango habitual <span className="font-mono tabular-nums">{formatMoney(lane.low)}–{formatMoney(lane.high)}</span></p>
              </section>
            ) : null}

            {error && <Alert tone="error" title="No pudimos enviar tu oferta">{error}</Alert>}
            {blocked && <VerificationBlock failure={blocked} />}

            {compatible.length === 0 ? (
              <div className="space-y-3">
                <Alert tone="warning" title={vehicles.length === 0 ? 'Aún no tienes vehículos' : 'Ningún vehículo de tu flota puede llevar esta carga'} action={<Button asChild size="sm" variant="outline"><a href="/transportista/flota">{vehicles.length === 0 ? 'Registrar un vehículo' : 'Ir a Flota'}</a></Button>}>
                  {vehicles.length === 0
                    ? 'Para ofertar necesitas un vehículo propio. Regístralo en Flota y vuelve a esta carga.'
                    : `Para ofertar por esta carga necesitas un vehículo ${request.compatibleVehicleTypes?.length ? `de tipo ${request.compatibleVehicleTypes.map((t) => VEHICLE_LABELS[t] ?? t).join(', ')} ` : ''}que no esté en mantenimiento, con capacidad para ${formatTons(request.weightKg)}${request.volumeM3 ? ` y ${request.volumeM3} m³` : ''}.`}
                </Alert>
                <UnfitList items={fleet.unfit} />
              </div>
            ) : (
              <>
                <Field label="Vehículo" htmlFor="b-vehicle" error={errors.vehicleId} hint={`${compatible.length === 1 ? 'Es tu único vehículo apto para esta carga.' : `${compatible.length} vehículos aptos para esta carga.`}${elig ? ` Disponibilidad según el recojo del ${formatDate(request.pickupDate)} (ocupa ${range(elig.windowFrom, elig.windowTo)}).` : ''}`}>
                  <Combobox
                    id="b-vehicle" value={vehicleId} onValueChange={(v) => { touchedVehicle.current = true; setVehicleId(v); setErrors((x) => ({ ...x, vehicleId: '' })); }} invalid={!!errors.vehicleId} placeholder="Selecciona un vehículo" searchPlaceholder="Buscar por placa…"
                    options={[
                      ...compatible.map((v) => {
                        const e = eligOf(v.id!);
                        const blockedByAgenda = e?.eligible === false;
                        return { value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!]}`, hint: agendaHint(e) ?? formatTons(v.maxWeightKg), disabled: blockedByAgenda };
                      }),
                      ...fleet.unfit.map(({ vehicle: v, compat }) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!]}`, hint: compat.short, disabled: true })),
                    ]}
                  />
                </Field>
                {selected?.warnings?.map((w) => isDocWarning(w.code) ? (
                  <Alert key={w.code} tone="warning" title={w.code === 'LOGYX-FLT-007' ? 'Documento vencido' : 'Un documento vence antes del recojo'} className="mt-0">
                    {w.message}. Puedes ofertar igual, pero conviene regularizarlo: la PYME ve si tu SOAT y tu revisión técnica están vigentes. <a href="/transportista/flota" className="font-medium underline">Ir a Flota</a>
                  </Alert>
                ) : (
                  <Alert key={w.code} tone="warning" title={w.message ?? 'Ese vehículo ya tiene ofertas para esas fechas'}>
                    Puedes ofertar igual, pero si la PYME acepta una, el sistema bloqueará la aceptación de las demás con ese vehículo.
                    {selected.busy?.length ? <ul className="mt-1.5 list-disc pl-4 text-xs">{selected.busy.filter((b) => b.kind === 'BID').map((b, i) => <li key={i}>{b.label} · {range(b.from, b.to)}</li>)}</ul> : null}
                  </Alert>
                ))}
                {agendaBlocked.length > 0 && (
                  <div className="rounded-input border border-steel-200 bg-steel-100/60 p-3 text-xs text-ink-700" data-testid="agenda-blocked">
                    <p className="mb-1 font-medium text-ink-900">No disponibles para esta carga</p>
                    <ul className="space-y-0.5">
                      {agendaBlocked.map((v) => {
                        const e = eligOf(v.id!)!;
                        const slot = e.busy?.find((b) => b.kind === 'SHIPMENT') ?? e.busy?.[0];
                        return <li key={v.id}><span className="font-mono font-semibold">{v.plate}</span>: {e.blockerCode === 'LOGYX-CAP-007' ? e.blockerMessage : e.blockerCode === 'LOGYX-FLT-007' ? `bloqueado, ${shortIssue(e.blockerMessage)}` : `ocupado ${range(slot?.from, slot?.to)}${e.freeFrom ? `, libre el ${formatDate(e.freeFrom)}` : ''}`}</li>;
                      })}
                    </ul>
                  </div>
                )}
                <UnfitList items={fleet.unfit} />
                <div>
                  <Field label="Tu oferta" htmlFor="b-amount" error={errors.amount} hint={`Mínimo ${formatMoney(floor)} (piso de la subasta). Incluye IGV.`}>
                    <MoneyInput id="b-amount" value={amount} onValueChange={(v) => { setAmount(v); setShortcut(null); }} invalid={!!errors.amount} placeholder={request.suggestedPrice ? String(request.suggestedPrice) : '0.00'} className="h-12 text-lg" />
                  </Field>
                  <div role="group" aria-label="Atajos de monto" className="mt-2 flex flex-wrap gap-2">
                    {shortcuts.map((s) => {
                      const v = shortcutAmount(s, request.suggestedPrice, request.priceFloor);
                      if (v == null) return null;
                      return (
                        <Button key={s} type="button" size="sm" variant={shortcut === s ? 'default' : 'outline'} aria-pressed={shortcut === s} onClick={() => pickShortcut(s)}>
                          {SHORTCUT_LABEL[s]}{clippedToFloor(s, request.suggestedPrice, request.priceFloor) ? ' (piso)' : ''} · <span className="font-mono">{formatMoney(v)}</span>
                        </Button>
                      );
                    })}
                  </div>
                </div>
                {amt > 0 && !belowFloor && (
                  <div className="rounded-card border border-emerald-500/30 bg-emerald-50 p-3 text-sm" aria-live="polite" data-testid="net-box">
                    {net ? (
                      <>
                        <div className="flex items-center gap-2 font-medium text-emerald-700"><Wallet className="size-4" aria-hidden="true" />Cobras <span className="money text-base" data-testid="net-amount">{formatMoney(net.carrierNet)}</span> neto</div>
                        <details className="group mt-1.5 text-xs text-ink-700">
                          <summary className="flex w-fit cursor-pointer list-none items-center gap-1 rounded-sm font-medium text-petrol-700 hover:underline [&::-webkit-details-marker]:hidden"><ChevronRight className="size-3.5 transition-transform group-open:rotate-90 motion-reduce:transition-none" aria-hidden="true" />Ver cómo se calcula</summary>
                          <dl className="mt-2 grid grid-cols-[1fr_auto] gap-x-4 gap-y-1" data-testid="net-breakdown">
                            <dt>Valor de venta (sin IGV)</dt><dd className="text-right font-mono tabular-nums">{formatMoney(net.netOfVat)}</dd>
                            <dt>IGV ({formatRate(0.18)})</dt><dd className="text-right font-mono tabular-nums">{formatMoney(net.vat)}</dd>
                            <dt className="border-t border-steel-200 pt-1 font-medium text-ink-900">Tu oferta (con IGV)</dt><dd className="border-t border-steel-200 pt-1 text-right font-mono font-medium tabular-nums text-ink-900">{formatMoney(net.amount)}</dd>
                            <dt>Comisión de OnCargo ({formatRate(net.commissionRate)} del valor de venta)</dt><dd className="text-right font-mono tabular-nums">− {formatMoney(net.commission)}</dd>
                            <dt>IGV de la comisión ({formatRate(0.18)})</dt><dd className="text-right font-mono tabular-nums">− {formatMoney(net.commissionVat)}</dd>
                            <dt className="border-t border-steel-200 pt-1 font-medium text-emerald-700">Cobras neto</dt><dd className="border-t border-steel-200 pt-1 text-right font-mono font-semibold tabular-nums text-emerald-700">{formatMoney(net.carrierNet)}</dd>
                          </dl>
                        </details>
                      </>
                    ) : (
                      <div className="flex items-center gap-2 font-medium text-emerald-700"><Wallet className="size-4" aria-hidden="true" />Si la PYME acepta, ofertas <span className="money text-base">{formatMoney(amt)}</span> (IGV incluido)</div>
                    )}
                    <ul className="mt-1.5 space-y-0.5 text-xs text-ink-700">
                      {perKm != null && <li><span className="font-mono">{formatMoney(perKm)}</span> por km recorrido ({formatNumber(request.distanceKm)} km)</li>}
                      {vs != null && <li className="flex items-center gap-1"><TrendingDown className="size-3.5" aria-hidden="true" />Está <span className={`font-mono font-semibold ${vs <= 0 ? 'text-emerald-700' : 'text-amber-700'}`}>{Math.abs(vs).toFixed(1)}%</span> {vs <= 0 ? 'por debajo' : 'por encima'} del sugerido{vs <= 0 ? ': más opciones de ganar.' : '.'}</li>}
                    </ul>
                  </div>
                )}
                <Field label="Nota para la PYME" htmlFor="b-notes" error={errors.notes} hint={`Opcional; se recuerda para la próxima oferta. Máximo ${LIMITS.bidNotes} caracteres. No incluyas teléfonos ni correos.`}><Textarea id="b-notes" value={notes} onChange={(e) => { setNotes(e.target.value); setErrors((x) => ({ ...x, notes: '' })); }} maxLength={LIMITS.bidNotes} aria-invalid={!!errors.notes || undefined} placeholder="Ej. Salimos el mismo día y contamos con GPS." /></Field>
              </>
            )}
          </div>

          {compatible.length > 0 && (
            <div className="sticky bottom-0 space-y-2 border-t border-steel-200 bg-surface p-4">
              <Button type="submit" variant="money" className="w-full" size="lg" loading={busy === 'send'} disabled={!!busy}>{busy === 'send' ? 'Enviando oferta…' : 'Enviar oferta'}</Button>
              {hasNext && onPlaced && <Button type="button" variant="outline" className="w-full" loading={busy === 'next'} disabled={!!busy} onClick={(e) => submit(e as unknown as React.FormEvent, true)}>{busy === 'next' ? 'Enviando…' : 'Enviar y ofertar la siguiente carga'}</Button>}
              {onAsk && <Button type="button" variant="ghost" className="w-full" onClick={() => onAsk(request)}><MessageSquare />Preguntar al solicitante</Button>}
            </div>
          )}
        </form>
      )}
    </Sheet>
  );
}
