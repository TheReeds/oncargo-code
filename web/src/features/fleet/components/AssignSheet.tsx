import { useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { MapPin, UserRound, Truck } from 'lucide-react';
import { Sheet } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { RadioGroup, RadioGroupItem } from '@/components/ui/form-controls';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { toast } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS, VEHICLE_LABELS, formatDate, formatNumber } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { ShipmentView } from '@/lib/api/server';
import type { Schemas } from '@/lib/api/server';
import { shortIssue } from '../documents';

type Suggestions = Schemas['SuggestionsView'];
type DriverOpt = NonNullable<Suggestions['drivers']>[number];
type VehicleOpt = NonNullable<Suggestions['vehicles']>[number];

export const routeOf = (s: Pick<ShipmentView, 'originCity' | 'destinationCity'>) => `${CITY_LABELS[s.originCity!] ?? s.originCity} → ${CITY_LABELS[s.destinationCity!] ?? s.destinationCity}`;

/** Por qué un conductor no se puede asignar (cuenta desactivada u ocupado en esas fechas). */
export const driverBlock = (d: DriverOpt): string | null => (d.assignable ? null : d.active === false ? 'Cuenta desactivada' : 'Ocupado en esas fechas');

function Choice({ id, name, disabled, children }: { id: string; name: string; disabled?: boolean; children: React.ReactNode }) {
  return (
    <label htmlFor={id} className={cn('flex items-start gap-3 rounded-input border border-steel-200 px-3 py-2.5 text-sm has-[[data-state=checked]]:border-petrol-700/50 has-[[data-state=checked]]:bg-petrol-50', disabled ? 'cursor-not-allowed bg-steel-100/60 opacity-80' : 'cursor-pointer hover:bg-steel-100/60')}>
      <RadioGroupItem id={id} value={name} disabled={disabled} className="mt-0.5" />
      <span className="min-w-0 flex-1">{children}</span>
    </label>
  );
}

/**
 * «Asignar» un envío activo sin conductor (2.13): conductores libres en las fechas del envío (ya vienen ordenados por cercanía al
 * recojo y carga de trabajo) y vehículos con su elegibilidad. Un solo paso: `PUT /shipments/{id}/assignment` (atómico). Si el
 * envío ya se recogió pide un motivo con `ReasonDialog`. Los no asignables salen deshabilitados con su motivo.
 */
export default function AssignSheet({ shipment, onClose, onAssigned }: { shipment: ShipmentView | null; onClose: () => void; onAssigned: (s: ShipmentView) => void }) {
  const [sug, setSug] = useState<Suggestions | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [driverId, setDriverId] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reasonOpen, setReasonOpen] = useState(false);
  const pickedUp = shipment?.status === 'IN_TRANSIT';

  useEffect(() => {
    setSug(null); setLoadError(null); setDriverId(''); setVehicleId(''); setError(null); setBusy(false); setReasonOpen(false);
    if (!shipment?.id) return;
    let alive = true;
    actions.fleet.suggestions({ shipmentId: shipment.id }).then(({ data, error: e }) => {
      if (!alive) return;
      if (e) { setLoadError(splitActionError(e).message); return; }
      const s = data as Suggestions;
      setSug(s);
      setVehicleId(s.vehicles?.find((v) => v.current)?.vehicleId ?? '');
    });
    return () => { alive = false; };
  }, [shipment?.id]);

  const drivers = sug?.drivers ?? [];
  const vehicles = sug?.vehicles ?? [];
  const currentVehicle = vehicles.find((v) => v.current)?.vehicleId ?? '';

  async function assign(reason?: string): Promise<{ message: string; field: string | null; code: string | null } | null> {
    const { data, error: e } = await actions.fleet.assign({
      shipmentId: shipment!.id!, driverId: driverId || undefined, vehicleId: vehicleId && vehicleId !== currentVehicle ? vehicleId : undefined, reason: reason || undefined,
    });
    if (e) return splitActionError(e);
    toast.success('Envío asignado');
    onAssigned(data as ShipmentView);
    return null;
  }

  async function confirmDirect() {
    if (!driverId) { setError('Elige el conductor que llevará el envío.'); return; }
    setError(null);
    if (pickedUp) { setReasonOpen(true); return; }
    setBusy(true);
    const f = await assign();
    setBusy(false);
    if (f) setError(f.message);
  }

  const win = sug?.shipment;
  return (
    <>
      <Sheet open={!!shipment} onOpenChange={(o) => !o && !busy && onClose()} title="Asignar conductor" description={shipment ? `${routeOf(shipment)}${shipment.code ? ` · ${shipment.code}` : ''}` : undefined}>
        {shipment && (
          <div className="flex min-h-full flex-col">
            <div className="flex-1 space-y-5 p-5">
              {loadError && <Alert tone="error" title="No pudimos cargar las sugerencias">{loadError}</Alert>}
              {error && <Alert tone="error">{error}</Alert>}
              {!sug && !loadError && <div className="space-y-2"><Skeleton className="h-14 w-full" /><Skeleton className="h-14 w-full" /><Skeleton className="h-14 w-full" /></div>}
              {sug && (
                <>
                  {win && <p className="text-sm text-ink-700" data-testid="assign-window">Ocupa del <span className="font-medium">{formatDate(win.from)}</span> al <span className="font-medium">{formatDate(win.to)}</span>{win.weightKg ? <> · <span className="font-mono">{formatNumber(win.weightKg)} kg</span></> : null}.</p>}
                  <fieldset>
                    <legend className="flex items-center gap-1.5 text-sm font-semibold text-ink-900"><UserRound className="size-4 text-petrol-700" aria-hidden="true" />Conductores libres en esas fechas</legend>
                    {drivers.length === 0 ? <p className="mt-2 text-sm text-ink-500">Aún no tienes conductores. Regístralos en Flota.</p> : (
                      <RadioGroup value={driverId} onValueChange={(v) => { setDriverId(v); setError(null); }} className="mt-2" aria-label="Conductores">
                        {drivers.map((d) => {
                          const why = driverBlock(d);
                          return (
                            <Choice key={d.driverId} id={`as-d-${d.driverId}`} name={d.driverId!} disabled={!!why}>
                              <span className="flex flex-wrap items-center justify-between gap-x-3">
                                <span className="font-medium text-ink-900">{d.name}</span>
                                {why ? <span className="text-xs font-medium text-amber-700" data-testid="driver-blocked">{why}</span> : <span className="text-xs font-medium text-emerald-700">Libre</span>}
                              </span>
                              <span className="mt-0.5 flex flex-wrap items-center gap-x-3 text-xs text-ink-500">
                                {d.distanceKm != null && <span className="inline-flex items-center gap-1"><MapPin className="size-3" aria-hidden="true" /><span className="font-mono tabular-nums">{formatNumber(d.distanceKm)} km</span> del recojo</span>}
                                <span><span className="font-mono tabular-nums">{d.workloadDays ?? 0}</span> {d.workloadDays === 1 ? 'día' : 'días'} de carga</span>
                              </span>
                            </Choice>
                          );
                        })}
                      </RadioGroup>
                    )}
                  </fieldset>
                  <fieldset>
                    <legend className="flex items-center gap-1.5 text-sm font-semibold text-ink-900"><Truck className="size-4 text-petrol-700" aria-hidden="true" />Vehículo</legend>
                    <RadioGroup value={vehicleId} onValueChange={setVehicleId} className="mt-2" aria-label="Vehículos">
                      {vehicles.map((v: VehicleOpt) => {
                        const blocked = v.eligible === false && !v.current;
                        return (
                          <Choice key={v.vehicleId} id={`as-v-${v.vehicleId}`} name={v.vehicleId!} disabled={blocked}>
                            <span className="flex flex-wrap items-center justify-between gap-x-3">
                              <span><span className="font-mono font-semibold">{v.plate}</span> <span className="text-ink-700">· {VEHICLE_LABELS[v.type ?? ''] ?? v.type}</span></span>
                              {v.current ? <span className="text-xs text-ink-500">Vehículo actual</span> : blocked ? null : <span className="text-xs font-medium text-emerald-700">Apto</span>}
                            </span>
                            {blocked && <span className="mt-0.5 block text-xs text-amber-700" data-testid="vehicle-blocked">{v.blockerCode === 'LOGYX-FLT-007' ? `Bloqueado: ${shortIssue(v.blockerMessage)}` : v.blockerMessage ?? 'No puede ir en este envío'}</span>}
                          </Choice>
                        );
                      })}
                    </RadioGroup>
                  </fieldset>
                </>
              )}
            </div>
            <div className="sticky bottom-0 space-y-2 border-t border-steel-200 bg-surface p-4">
              {pickedUp && <p className="text-xs text-ink-500">El envío ya se recogió: te pediremos el motivo del cambio.</p>}
              <Button className="w-full" size="lg" loading={busy} disabled={!sug || busy} onClick={confirmDirect}>{busy ? 'Asignando…' : 'Asignar conductor'}</Button>
            </div>
          </div>
        )}
      </Sheet>
      <ReasonDialog open={reasonOpen} onOpenChange={setReasonOpen} title="Motivo de la asignación" description="El envío ya se recogió: la PYME verá el motivo del cambio." required minLength={3} maxLength={LIMITS.shipmentReason}
        label="Motivo" placeholder="Ej. El conductor original no pudo continuar el viaje." confirmLabel="Asignar conductor"
        onSubmit={async (reason) => assign(reason)} />
    </>
  );
}
