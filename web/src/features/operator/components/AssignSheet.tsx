import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { MoneyInput } from '@/components/ui/number-input';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { Skeleton } from '@/components/ui/skeleton';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { RouteLine } from '@/components/domain/RouteLine';
import { EmptyState } from '@/components/domain/EmptyState';
import { TrustScore } from '@/components/domain/TrustScore';
import { VerifiedBadge } from '@/components/domain/VerifiedBadge';
import { CARGO_LABELS, VEHICLE_LABELS, formatDate, formatMoney, formatTons } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { AssignmentResult, Candidate, SlaRequest } from '../server';

/** Asignación manual (RN10): candidatos con vehículo disponible y capacidad, mejor Trust Score primero. */
export default function AssignSheet({ request, onClose, onAssigned }: { request: SlaRequest | null; onClose: () => void; onAssigned: (r: AssignmentResult, requestId: string) => void }) {
  const [candidates, setCandidates] = useState<Candidate[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [vehicleId, setVehicleId] = useState('');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setCandidates(null);
    setLoadError(null);
    setVehicleId('');
    setNotes('');
    setErrors({});
    setError(null);
    setBusy(false);
    setAmount(request?.suggestedPrice != null ? String(request.suggestedPrice) : '');
    if (!request?.id) return;
    let alive = true;
    actions.operator.candidates({ requestId: request.id }).then(({ data, error }) => {
      if (!alive) return;
      if (error) setLoadError(error.message);
      else setCandidates(data ?? []);
    });
    return () => { alive = false; };
  }, [request?.id]);

  const selected = candidates?.find((c) => c.vehicleId === vehicleId);
  const floor = request?.priceFloor ?? 0;
  const amt = Number(amount);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!request?.id || !selected) return;
    setError(null);
    const errs: Record<string, string> = {};
    if (!vehicleId) errs.vehicleId = 'Selecciona el transportista y su vehículo.';
    if (!(amt > 0)) errs.amount = 'Ingresa el monto de la asignación.';
    else if (floor > 0 && amt < floor) errs.amount = `El monto es menor al piso de la subasta (${formatMoney(floor)}). Sube el monto para continuar.`;
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const ok = await confirm({
      title: `¿Asignar la solicitud a ${selected.carrierName} por ${formatMoney(amt)}?`,
      description: 'Se creará la oferta, se adjudicará la solicitud y se generará el envío. El transportista y la PYME serán notificados.',
      confirmLabel: 'Asignar solicitud',
    });
    if (!ok) return;
    setBusy(true);
    const { data, error: err } = await actions.operator.assign({ requestId: request.id, carrierOrgId: selected.carrierOrgId!, vehicleId, amount: amt, notes: notes.trim() || undefined });
    if (err || !data) {
      setBusy(false);
      const { message, field } = splitActionError(err ?? { message: 'No pudimos asignar la solicitud. Inténtalo de nuevo.' });
      if (field === 'amount' || field === 'vehicleId') setErrors({ [field]: message });
      else setError(message);
      return;
    }
    toast.success('Solicitud asignada', `${data.carrierName} · ${data.vehiclePlate} · ${formatMoney(data.amount)}`);
    onAssigned(data, request.id);
  }

  return (
    <Sheet open={!!request} onOpenChange={(o) => !o && !busy && onClose()} title="Asignar transportista" description="Elige un transportista con vehículo disponible. Se crea la oferta, se adjudica y se genera el envío.">
      {request && (
        <form onSubmit={submit} noValidate className="flex min-h-full flex-col">
          <div className="flex-1 space-y-5 p-5">
            <div className="space-y-3 rounded-card bg-steel-100 p-4">
              <RouteLine origin={request.originCity!} destination={request.destinationCity!} />
              <div className="text-sm text-ink-700">{request.requesterName} · {CARGO_LABELS[request.cargoType!] ?? request.cargoType} · <span className="font-mono">{formatTons(request.weightKg)}</span> · recojo {formatDate(request.pickupDate)}</div>
              <div className="grid grid-cols-2 gap-3 border-t border-steel-200 pt-3 text-sm">
                <div><div className="text-xs text-ink-500">Precio sugerido</div><div className="money text-base">{formatMoney(request.suggestedPrice)}</div></div>
                <div><div className="text-xs text-ink-500">Piso (monto mínimo)</div><div className="font-mono font-semibold tabular-nums">{formatMoney(request.priceFloor)}</div></div>
              </div>
            </div>

            {error && <Alert tone="error" title="No pudimos asignar la solicitud">{error}</Alert>}

            {loadError ? (
              <Alert tone="error" title="No pudimos cargar los candidatos">{loadError}</Alert>
            ) : candidates === null ? (
              <div className="space-y-2" aria-busy="true" aria-label="Cargando candidatos"><Skeleton className="h-16" /><Skeleton className="h-16" /><Skeleton className="h-16" /></div>
            ) : candidates.length === 0 ? (
              <EmptyState title="Ningún transportista puede tomar esta carga" description={`Ningún transportista tiene un vehículo disponible con capacidad para ${formatTons(request.weightKg)}. Vuelve a intentarlo cuando se libere un vehículo.`} />
            ) : (
              <>
                <fieldset>
                  <legend className="mb-1.5 text-[13px] font-medium text-ink-700">Transportista y vehículo <span className="font-normal text-ink-500">· {candidates.length} {candidates.length === 1 ? 'candidato' : 'candidatos'}, mejor Trust Score primero</span></legend>
                  <RadioGroup value={vehicleId} onValueChange={(v) => { setVehicleId(v); setErrors((x) => ({ ...x, vehicleId: '' })); }} aria-label="Candidatos" aria-invalid={!!errors.vehicleId || undefined}>
                    {candidates.map((c, idx) => {
                      const on = c.vehicleId === vehicleId;
                      return (
                        <label key={c.vehicleId} htmlFor={`cand-${c.vehicleId}`} className={cn('flex cursor-pointer items-start gap-3 rounded-input border p-3 transition-colors hover:border-petrol-700/50', on ? 'border-petrol-700 bg-petrol-50' : 'border-steel-200 bg-surface')}>
                          <RadioGroupItem id={`cand-${c.vehicleId}`} value={c.vehicleId!} className="mt-0.5" />
                          <span className="min-w-0 flex-1">
                            <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                              <span className="truncate text-sm font-semibold text-ink-900">{c.carrierName}</span>
                              <VerifiedBadge verified={c.carrierRucVerified} variant="icon" />
                              <TrustScore score={c.carrierTrustScore} />
                              {idx === 0 && candidates.length > 1 && <Badge tone="emerald">Recomendado</Badge>}
                            </span>
                            <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-ink-500">
                              <span>{VEHICLE_LABELS[c.vehicleType!] ?? c.vehicleType} <span className="font-mono text-ink-700">{c.vehiclePlate}</span></span>
                              <span>Capacidad <span className="font-mono text-ink-700">{formatTons(c.maxWeightKg)}</span></span>
                            </span>
                          </span>
                        </label>
                      );
                    })}
                  </RadioGroup>
                  {errors.vehicleId && <p className="mt-1 text-xs text-red-600" role="alert">{errors.vehicleId}</p>}
                </fieldset>

                <Field label="Monto de la asignación" htmlFor="a-amount" error={errors.amount} hint={`Por defecto el precio sugerido. Mínimo ${formatMoney(floor)} (piso de la subasta).`}>
                  <MoneyInput id="a-amount" value={amount} onValueChange={(v) => { setAmount(v); setErrors((x) => ({ ...x, amount: '' })); }} invalid={!!errors.amount} className="h-12 text-lg" />
                </Field>
                {selected && amt > 0 && (
                  <p className="rounded-input bg-petrol-50 px-3 py-2.5 text-sm text-ink-700">
                    Asignarás a <span className="font-semibold text-ink-900">{selected.carrierName}</span> por <span className="money">{formatMoney(amt)}</span>
                    {request.suggestedPrice ? <> ({amt === request.suggestedPrice ? 'igual al precio sugerido' : `${amt > request.suggestedPrice ? '+' : '−'}${Math.abs(Math.round(((amt - request.suggestedPrice) / request.suggestedPrice) * 100))} % frente al sugerido`})</> : null}.
                  </p>
                )}
                <Field label="Notas" htmlFor="a-notes" hint="Opcional. Máximo 500 caracteres.">
                  <Textarea id="a-notes" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} placeholder="Ej. Asignada por falta de ofertas tras coordinar con el transportista." />
                </Field>
              </>
            )}
          </div>
          {candidates && candidates.length > 0 && (
            <div className="sticky bottom-0 border-t border-steel-200 bg-surface p-4">
              <Button type="submit" className="w-full" size="lg" loading={busy} disabled={!vehicleId}>{busy ? 'Asignando solicitud…' : 'Asignar solicitud'}</Button>
            </div>
          )}
        </form>
      )}
    </Sheet>
  );
}
