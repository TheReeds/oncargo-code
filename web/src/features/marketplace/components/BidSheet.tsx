import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Field, Input, Select, Textarea } from '@/components/ui/input';
import { Alert } from '@/components/ui/alert';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { CARGO_LABELS, formatDate, formatMoney, formatTons, VEHICLE_LABELS } from '@/lib/format';
import type { RequestView, VehicleView } from '@/lib/api/server';

export default function BidSheet({ request, vehicles, onClose }: { request: RequestView | null; vehicles: VehicleView[]; onClose: () => void }) {
  const [vehicleId, setVehicleId] = useState('');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    setVehicleId('');
    setAmount('');
    setNotes('');
    setError(null);
    setErrors({});
    setDone(false);
  }, [request?.id]);

  const compatible = vehicles.filter((v) => v.status === 'AVAILABLE' && (v.maxWeightKg ?? 0) >= (request?.weightKg ?? 0));
  const amt = Number(amount);
  const belowFloor = request?.priceFloor != null && amt > 0 && amt < request.priceFloor;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!request) return;
    setError(null);
    const errs: Record<string, string> = {};
    if (!vehicleId) errs.vehicleId = 'Selecciona un vehículo';
    if (!(amt > 0)) errs.amount = 'Ingresa un monto válido';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { error } = await actions.marketplace.placeBid({ requestId: request.id!, vehicleId, amount: amt, notes: notes.trim() || undefined });
    setBusy(false);
    if (error) return setError(error.message);
    setDone(true);
  }

  return (
    <Sheet open={!!request} onOpenChange={(o) => !o && (done ? window.location.reload() : onClose())} title="Enviar oferta" description="Compites en subasta inversa: menor monto, mayor chance.">
      {request && (
        <form onSubmit={submit} noValidate className="space-y-5 p-5">
          <div className="space-y-3 rounded-card bg-steel-100 p-4">
            <RouteLine origin={request.originCity!} destination={request.destinationCity!} km={request.distanceKm} />
            <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-ink-700">
              <span>{CARGO_LABELS[request.cargoType!]} · <span className="font-mono">{formatTons(request.weightKg)}</span> · recojo {formatDate(request.pickupDate)}</span>
              <Countdown closesAt={request.auction?.closesAt} />
            </div>
            <div className="grid grid-cols-2 gap-3 border-t border-steel-200 pt-3 text-sm">
              <div><div className="text-xs text-ink-500">Precio sugerido</div><div className="money">{formatMoney(request.suggestedPrice)}</div></div>
              <div><div className="text-xs text-ink-500">Piso (monto mínimo)</div><div className="font-mono font-semibold tabular-nums">{formatMoney(request.priceFloor)}</div></div>
            </div>
          </div>

          {done ? (
            <div className="space-y-3">
              <Alert tone="success">¡Oferta enviada por {formatMoney(amt)}! La PYME la verá en su comparador.</Alert>
              <Button className="w-full" onClick={() => window.location.reload()}>Listo</Button>
            </div>
          ) : (
            <>
              {error && <Alert tone="error">{error}</Alert>}
              <Field label="Vehículo" htmlFor="b-vehicle" error={errors.vehicleId} hint={compatible.length ? 'Solo se muestran vehículos disponibles con capacidad suficiente.' : undefined}>
                <Select id="b-vehicle" value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} aria-invalid={!!errors.vehicleId}>
                  <option value="">Selecciona…</option>
                  {compatible.map((v) => <option key={v.id} value={v.id}>{v.plate} · {VEHICLE_LABELS[v.type!]} · {formatTons(v.maxWeightKg)}</option>)}
                </Select>
              </Field>
              {compatible.length === 0 && <Alert tone="info">No tienes un vehículo disponible con capacidad para {formatTons(request.weightKg)}. <a className="underline" href="/transportista/flota">Ir a Flota</a>.</Alert>}
              <Field label="Tu oferta (S/)" htmlFor="b-amount" error={errors.amount} hint={`Debe ser igual o mayor al piso: ${formatMoney(request.priceFloor)}`}>
                <Input id="b-amount" type="number" inputMode="decimal" step="0.01" min="0" value={amount} onChange={(e) => setAmount(e.target.value)} className="font-mono text-lg" placeholder={String(request.suggestedPrice ?? '')} aria-invalid={!!errors.amount} />
              </Field>
              {belowFloor && <Alert tone="info">Este monto está por debajo del piso; la API podría rechazarlo.</Alert>}
              <Field label="Nota para la PYME" htmlFor="b-notes" hint="Opcional"><Textarea id="b-notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} /></Field>
              <Button type="submit" variant="money" className="w-full" size="lg" disabled={busy}>{busy ? 'Enviando…' : 'Enviar oferta'}</Button>
            </>
          )}
        </form>
      )}
    </Sheet>
  );
}
