import { useCan } from '@/lib/use-can';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Ban, Repeat, UserRoundCog, UserRoundPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Alert } from '@/components/ui/alert';
import { Field } from '@/components/ui/field';
import { Combobox } from '@/components/ui/combobox';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { flash } from '@/components/ui/toaster';
import { splitActionError, type ActionFailure } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { VEHICLE_LABELS, formatTons } from '@/lib/format';
import { partitionFleet, requestNeed } from '@/features/fleet/compatibility';
import { UnfitList } from '@/features/fleet/components/UnfitList';
import type { DriverView, RequestView, ShipmentView, VehicleView } from '@/lib/api/server';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';

type Ctx = { vehicles: VehicleView[]; drivers: DriverView[]; request: RequestView | null };
/** Flota del transportista (y la solicitud para la matriz RN02), cargada cuando se abre un diálogo del transportista. */
function useFleetContext(enabled: boolean, requestId?: string) {
  const [ctx, setCtx] = useState<Ctx | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!enabled || ctx) return;
    actions.shipments.fleetContext({ requestId }).then(({ data, error: e }) => {
      if (e) setError(splitActionError(e).message);
      else setCtx(data as Ctx);
    });
  }, [enabled, ctx, requestId]);
  return { ctx, error };
}

/** Cancelar el envío con motivo (F06). Las partes antes del recojo; el operador también en tránsito. */
function CancelShipmentDialog({ shipment, perspective, open, onOpenChange }: { shipment: ShipmentView; perspective: Perspective; open: boolean; onOpenChange: (o: boolean) => void }) {
  const description = perspective === 'PYME'
    ? 'Se cancela el envío y también tu solicitud; el transportista recibe el aviso con tu motivo. Solo puedes cancelar antes del recojo. No se puede deshacer.'
    : perspective === 'CARRIER'
      ? 'Se cancela el envío y la solicitud de la PYME, y se libera tu vehículo y tu conductor. La PYME recibe el aviso con tu motivo. Solo puedes cancelar antes del recojo. No se puede deshacer.'
      : 'Se cancela el envío y la solicitud, y se avisa a ambas partes con el motivo. No se puede deshacer.';
  return (
    <ReasonDialog
      open={open} onOpenChange={onOpenChange} title="¿Cancelar este envío?" description={description} label="Motivo de la cancelación" required minLength={3}
      hint="Lo verá la otra parte. No incluyas teléfonos ni correos." placeholder="Ej. El cliente pidió postergar la entrega." maxLength={LIMITS.shipmentReason}
      confirmLabel="Cancelar envío" tone="destructive"
      onSubmit={async (reason) => {
        const { error } = await actions.shipments.cancel({ id: shipment.id!, reason });
        if (error) return splitActionError(error);
        flash('Envío cancelado');
        window.location.reload();
        return null;
      }}
    />
  );
}

/** Cambiar el vehículo del envío (RN02, `CAP-*`, `SHP-007`); en ruta el motivo es obligatorio (`SHP-006`). */
function ChangeVehicleDialog({ shipment, open, onOpenChange }: { shipment: ShipmentView; open: boolean; onOpenChange: (o: boolean) => void }) {
  const inTransit = shipment.status === 'IN_TRANSIT';
  const { ctx, error } = useFleetContext(open, shipment.requestId);
  const [vehicleId, setVehicleId] = useState('');
  const [vehErr, setVehErr] = useState<string | null>(null);
  useEffect(() => { if (open) { setVehicleId(''); setVehErr(null); } }, [open]);

  // Matriz RN02: la solicitud trae tipo de carga y tipos aptos; si no se pudo leer, al menos kg del envío.
  const need = useMemo(() => (ctx?.request ? requestNeed(ctx.request) : { weightKg: shipment.weightKg }), [ctx, shipment.weightKg]);
  const { fit, unfit } = useMemo(() => partitionFleet((ctx?.vehicles ?? []).filter((v) => v.id !== shipment.vehicleId), need), [ctx, need, shipment.vehicleId]);
  const options = [
    ...fit.map((v) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!]}`, hint: formatTons(v.maxWeightKg) })),
    ...unfit.map(({ vehicle: v, compat }) => ({ value: v.id!, label: `${v.plate} · ${VEHICLE_LABELS[v.type!]}`, hint: compat.short, disabled: true })),
  ];
  return (
    <ReasonDialog
      open={open} onOpenChange={onOpenChange} title="Cambiar el vehículo del envío"
      description={`Hoy lo lleva ${shipment.vehiclePlate ?? 'un vehículo sin placa'}. El nuevo vehículo debe poder llevar la carga (tipo, peso y volumen) y estar libre en esas fechas. La PYME recibe el aviso.`}
      label="Motivo del cambio" required={inTransit} minLength={3} hint={inTransit ? 'Obligatorio en ruta: la PYME lo verá.' : 'Opcional. La PYME lo verá.'} placeholder="Ej. El camión original tuvo una falla mecánica."
      maxLength={LIMITS.shipmentReason} confirmLabel="Cambiar vehículo"
      canSubmit={() => { if (!vehicleId) { setVehErr('Selecciona el vehículo que llevará el envío.'); return false; } return true; }}
      onFailure={(f: ActionFailure) => { if (f.field === 'vehicleId' || f.code?.startsWith('LOGYX-CAP-') || f.code === 'LOGYX-SHP-007') { setVehErr(f.message); return true; } return false; }}
      onSubmit={async (reason) => {
        setVehErr(null);
        const { error: e } = await actions.shipments.changeVehicle({ id: shipment.id!, vehicleId, reason: reason || undefined });
        if (e) return splitActionError(e);
        flash('Vehículo cambiado');
        window.location.reload();
        return null;
      }}
    >
      {error && <Alert tone="error" title="No pudimos cargar tu flota">{error}</Alert>}
      {ctx && ctx.vehicles.length <= 1 ? (
        <Alert tone="info" title="No tienes otro vehículo">Registra otro vehículo en <a className="font-medium underline" href="/transportista/flota">Flota</a> para poder reemplazar el actual.</Alert>
      ) : (
        <>
          <Field label="Nuevo vehículo" htmlFor="cv-vehicle" error={vehErr} required hint={ctx ? `${fit.length} ${fit.length === 1 ? 'vehículo apto' : 'vehículos aptos'} para esta carga.` : 'Cargando tu flota…'}>
            <Combobox id="cv-vehicle" value={vehicleId} onValueChange={(v) => { setVehicleId(v); setVehErr(null); }} invalid={!!vehErr} options={options} placeholder="Elige un vehículo" searchPlaceholder="Buscar por placa…" disabled={!ctx} />
          </Field>
          <UnfitList items={unfit} />
        </>
      )}
    </ReasonDialog>
  );
}

/** Asignar conductor (sin motivo) o relevo de conductor (`SHP-006`: motivo obligatorio en ruta). `SHP-004` queda inline en el campo. */
function DriverDialog({ shipment, open, onOpenChange }: { shipment: ShipmentView; open: boolean; onOpenChange: (o: boolean) => void }) {
  const relief = !!shipment.driverId;
  const inTransit = shipment.status === 'IN_TRANSIT';
  const { ctx, error } = useFleetContext(open);
  const [driverId, setDriverId] = useState('');
  const [drvErr, setDrvErr] = useState<string | null>(null);
  useEffect(() => { if (open) { setDriverId(''); setDrvErr(null); } }, [open]);
  const drivers = (ctx?.drivers ?? []).filter((d) => d.id !== shipment.driverId);
  return (
    <ReasonDialog
      open={open} onOpenChange={onOpenChange} title={relief ? 'Relevo de conductor' : 'Asignar conductor'}
      description={relief ? `Hoy lo conduce ${shipment.driverName ?? 'otro conductor'}. El nuevo conductor recibirá el envío en su app y no puede tener otro envío en esas fechas.` : 'Recibirá el envío en su app y podrá iniciar el viaje. No puede tener otro envío activo en esas fechas.'}
      hideReason={!relief} label="Motivo del relevo" required={inTransit} minLength={3} hint={inTransit ? 'Obligatorio en ruta: la PYME lo verá.' : 'Opcional. La PYME lo verá.'} placeholder="Ej. El conductor se enfermó en la ruta."
      maxLength={LIMITS.shipmentReason} confirmLabel={relief ? 'Relevar conductor' : 'Asignar conductor'}
      canSubmit={() => { if (!driverId) { setDrvErr('Selecciona un conductor.'); return false; } return true; }}
      onFailure={(f) => { if (f.field === 'driverId' || f.code === 'LOGYX-SHP-004') { setDrvErr(f.message); return true; } return false; }}
      onSubmit={async (reason) => {
        setDrvErr(null);
        const { error: e } = await actions.shipments.assignDriver({ id: shipment.id!, driverId, reason: relief && reason ? reason : undefined });
        if (e) return splitActionError(e);
        flash(relief ? 'Conductor relevado' : 'Conductor asignado');
        window.location.reload();
        return null;
      }}
    >
      {error && <Alert tone="error" title="No pudimos cargar tus conductores">{error}</Alert>}
      {ctx && drivers.length === 0 ? (
        <Alert tone="info" title={relief ? 'No tienes otro conductor' : 'Aún no tienes conductores'}>Registra uno en <a className="font-medium underline" href="/transportista/flota">Flota</a> para asignarlo a este envío.</Alert>
      ) : (
        <Field label="Conductor" htmlFor="dr-driver" error={drvErr} required>
          <Combobox id="dr-driver" value={driverId} onValueChange={(v) => { setDriverId(v); setDrvErr(null); }} invalid={!!drvErr} disabled={!ctx} placeholder="Elige un conductor" searchPlaceholder="Buscar por nombre o licencia"
            options={drivers.map((d) => ({ value: d.id!, label: d.fullName ?? '', hint: d.licenseNumber ?? undefined }))} />
        </Field>
      )}
    </ReasonDialog>
  );
}

/**
 * Acciones de un envío (F06): cancelar con motivo (PYME y transportista antes del recojo, operador también en ruta),
 * y para el transportista cambiar el vehículo y asignar o relevar al conductor. Se monta en el detalle desde `ShipmentExtras`
 * (PYME/transportista) y en `/operador/envios/[id]`; no depende del mapa ni de `TrackingSplit`.
 */
export default function ShipmentActions({ shipment, perspective }: { shipment: ShipmentView; perspective: Perspective }) {
  const [dialog, setDialog] = useState<'cancel' | 'vehicle' | 'driver' | null>(null);
  const canOperate = useCan('OPERATE');
  const status = shipment.status;
  const open = status === 'PENDING' || status === 'IN_TRANSIT';
  if (!open) return null;
  if (!canOperate) return <ReadOnlyNotice>No puedes cancelar envíos ni asignar conductores o vehículos.</ReadOnlyNotice>;
  const operator = perspective === 'OPERATOR';
  const carrier = perspective === 'CARRIER';
  const canCancel = operator ? true : status === 'PENDING' && !shipment.hasActiveIncident;
  const blockedByIncident = !operator && shipment.hasActiveIncident;
  const noDriver = !shipment.driverId;
  if (!operator && !carrier && status !== 'PENDING') return null; // la PYME solo puede cancelar antes del recojo
  return (
    <section aria-label="Acciones del envío" className="space-y-2">
      <h3 className="text-sm font-semibold text-ink-900">Acciones del envío</h3>
      <div className="flex flex-wrap gap-2">
        {carrier && <Button variant={noDriver ? 'money' : 'outline'} onClick={() => setDialog('driver')}>{noDriver ? <UserRoundPlus /> : <UserRoundCog />}{noDriver ? 'Asignar conductor' : 'Relevo de conductor'}</Button>}
        {carrier && <Button variant="outline" onClick={() => setDialog('vehicle')}><Repeat />Cambiar vehículo</Button>}
        {(canCancel || blockedByIncident) && <Button variant="destructive" disabled={!canCancel} onClick={() => setDialog('cancel')}><Ban />Cancelar envío</Button>}
      </div>
      {blockedByIncident && <p className="text-xs text-ink-500">Con una incidencia activa no puedes cancelar ni avanzar el envío: el operador de OnCargo la resolverá.</p>}
      {carrier && status === 'IN_TRANSIT' && <p className="text-xs text-ink-500">En ruta, cambiar el vehículo o el conductor exige un motivo. Solo el operador de OnCargo cancela un envío en tránsito.</p>}
      {dialog === 'cancel' && <CancelShipmentDialog shipment={shipment} perspective={perspective} open onOpenChange={(o) => !o && setDialog(null)} />}
      {dialog === 'vehicle' && <ChangeVehicleDialog shipment={shipment} open onOpenChange={(o) => !o && setDialog(null)} />}
      {dialog === 'driver' && <DriverDialog shipment={shipment} open onOpenChange={(o) => !o && setDialog(null)} />}
    </section>
  );
}
