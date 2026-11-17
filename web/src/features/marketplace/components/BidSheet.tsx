import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { MoneyInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { flash } from '@/components/ui/toaster';
import { TrustScore } from '@/components/domain/TrustScore';
import { MessageSquare, TrendingDown, Wallet } from 'lucide-react';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { Badge } from '@/components/ui/badge';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { CARGO_LABELS, formatDate, formatMoney, formatNumber, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { RequestView, VehicleView } from '@/lib/api/server';

export default function BidSheet({ request, vehicles, onClose, onAsk, defaultVehicleId }: { request: RequestView | null; vehicles: VehicleView[]; onClose: () => void; onAsk?: (r: RequestView) => void; defaultVehicleId?: string }) {
  const [vehicleId, setVehicleId] = useState('');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const only = vehicles.filter((v) => v.status === 'AVAILABLE' && (v.maxWeightKg ?? 0) >= (request?.weightKg ?? 0));
    setVehicleId(defaultVehicleId ?? (only.length === 1 ? only[0].id! : ''));
    setAmount('');
    setNotes('');
    setError(null);
    setErrors({});
    setBusy(false);
  }, [request?.id]);

  const compatible = vehicles.filter((v) => v.status === 'AVAILABLE' && (v.maxWeightKg ?? 0) >= (request?.weightKg ?? 0));
  const amt = Number(amount);
  const floor = request?.priceFloor ?? 0;
  const belowFloor = floor > 0 && amt > 0 && amt < floor;
  const vs = request?.suggestedPrice ? ((amt - request.suggestedPrice) / request.suggestedPrice) * 100 : null;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!request) return;
    setError(null);
    const errs: Record<string, string> = {};
    if (!vehicleId) errs.vehicleId = 'Selecciona el vehículo que hará el envío.';
    if (!(amt > 0)) errs.amount = 'Ingresa el monto de tu oferta.';
    else if (belowFloor) errs.amount = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube tu oferta para continuar.`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { error } = await actions.marketplace.placeBid({ requestId: request.id!, vehicleId, amount: amt, notes: notes.trim() || undefined });
    if (error) {
      setBusy(false);
      const { message, field } = splitActionError(error);
      if (field === 'amount' || field === 'vehicleId') setErrors({ [field]: message });
      else setError(message);
      return;
    }
    flash('Oferta enviada', 'success');
    window.location.reload();
  }

  const perKm = amt > 0 && request?.distanceKm ? amt / request.distanceKm : null;
  const bids = request?.auction?.bidCount ?? 0;
  const maxBids = request?.auction?.maxBids ?? 5;

  return (
    <Sheet open={!!request} onOpenChange={(o) => !o && !busy && onClose()} title="Enviar oferta" description="Compites en subasta inversa: a menor monto, más opciones de ganar.">
      {request && (
        <form onSubmit={submit} noValidate className="flex min-h-full flex-col">
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

            {error && <Alert tone="error" title="No pudimos enviar tu oferta">{error}</Alert>}

            {compatible.length === 0 ? (
              <Alert tone="warning" title="No tienes un vehículo compatible" action={<Button asChild size="sm" variant="outline"><a href="/transportista/flota">Ir a Flota</a></Button>}>
                Necesitas un vehículo disponible con capacidad para {formatTons(request.weightKg)}.
              </Alert>
            ) : (
              <>
                <Field label="Vehículo" htmlFor="b-vehicle" error={errors.vehicleId} hint={compatible.length === 1 ? 'Es tu único vehículo disponible con capacidad suficiente.' : `${compatible.length} vehículos disponibles con capacidad suficiente.`}>
                  <Combobox id="b-vehicle" value={vehicleId} onValueChange={setVehicleId} invalid={!!errors.vehicleId} placeholder="Selecciona un vehículo" searchPlaceholder="Buscar por placa…" options={compatible.map((v) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!]}`, hint: formatTons(v.maxWeightKg) }))} />
                </Field>
                <div>
                  <Field label="Tu oferta" htmlFor="b-amount" error={errors.amount} hint={`Mínimo ${formatMoney(floor)} (piso de la subasta).`}>
                    <MoneyInput id="b-amount" value={amount} onValueChange={setAmount} invalid={!!errors.amount} placeholder={request.suggestedPrice ? String(request.suggestedPrice) : '0.00'} className="h-12 text-lg" />
                  </Field>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {request.suggestedPrice ? <Button type="button" size="sm" variant="outline" onClick={() => setAmount(String(request.suggestedPrice))}>Usar sugerido · <span className="font-mono">{formatMoney(request.suggestedPrice)}</span></Button> : null}
                    {floor > 0 ? <Button type="button" size="sm" variant="outline" onClick={() => setAmount(String(floor))}>Usar piso · <span className="font-mono">{formatMoney(floor)}</span></Button> : null}
                  </div>
                </div>
                {amt > 0 && !belowFloor && (
                  <div className="rounded-card border border-emerald-500/30 bg-emerald-50 p-3 text-sm" aria-live="polite">
                    <div className="flex items-center gap-2 font-medium text-emerald-700"><Wallet className="size-4" aria-hidden="true" />Si la PYME acepta, cobras <span className="money text-base">{formatMoney(amt)}</span></div>
                    <ul className="mt-1.5 space-y-0.5 text-xs text-ink-700">
                      {perKm != null && <li><span className="font-mono">{formatMoney(perKm)}</span> por km recorrido ({formatNumber(request.distanceKm)} km)</li>}
                      {vs != null && <li className="flex items-center gap-1"><TrendingDown className="size-3.5" aria-hidden="true" />Está <span className={`font-mono font-semibold ${vs <= 0 ? 'text-emerald-700' : 'text-amber-700'}`}>{Math.abs(vs).toFixed(1)}%</span> {vs <= 0 ? 'por debajo' : 'por encima'} del sugerido{vs <= 0 ? ': más opciones de ganar.' : '.'}</li>}
                    </ul>
                  </div>
                )}
                <Field label="Nota para la PYME" htmlFor="b-notes" hint="Opcional. Máximo 500 caracteres."><Textarea id="b-notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} placeholder="Ej. Salimos el mismo día y contamos con GPS." /></Field>
              </>
            )}
          </div>

          {compatible.length > 0 && (
            <div className="sticky bottom-0 space-y-2 border-t border-steel-200 bg-surface p-4">
              <Button type="submit" variant="money" className="w-full" size="lg" loading={busy}>{busy ? 'Enviando oferta…' : 'Enviar oferta'}</Button>
              {onAsk && <Button type="button" variant="ghost" className="w-full" onClick={() => onAsk(request)}><MessageSquare />Preguntar al solicitante</Button>}
            </div>
          )}
        </form>
      )}
    </Sheet>
  );
}
