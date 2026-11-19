import { useEffect, useMemo, useState } from 'react';
import { actions } from 'astro:actions';
import { Dialog } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/form-controls';
import { Alert } from '@/components/ui/alert';
import { toast } from '@/components/ui/toaster';
import { toFormErrors } from '@/lib/action-error';
import { CARGO_LABELS, VEHICLE_LABELS } from '@/lib/format';
import type { VehicleView } from '@/lib/api/server';
import { cargoTypesFor } from '../compatibility';
import { EQUIPMENT } from '../documents';

/** Casilla con su etiqueta (la etiqueta es el nombre accesible, no un aria-label aparte). */
function Check({ id, checked, onChange, label, hint }: { id: string; checked: boolean; onChange: (v: boolean) => void; label: string; hint?: string }) {
  return (
    <label htmlFor={id} className="flex cursor-pointer items-start gap-2.5 rounded-input border border-steel-200 px-3 py-2 text-sm text-ink-900 has-[[data-state=checked]]:border-petrol-700/40 has-[[data-state=checked]]:bg-petrol-50">
      <Checkbox id={id} checked={checked} onCheckedChange={(c) => onChange(c === true)} className="mt-0.5" />
      <span className="min-w-0"><span className="block font-medium">{label}</span>{hint && <span className="block text-xs text-ink-500">{hint}</span>}</span>
    </label>
  );
}

/**
 * «Configurar» un vehículo (2.4): equipamiento (GPS, rampa, plataforma hidráulica, toldo, frío certificado, cintas) y «Tipos de
 * carga que aceptas». Solo se pueden restringir los tipos que la matriz RN02 permite para ese tipo de vehículo (`cargoTypesFor`);
 * con todos marcados no se restringe nada. El servidor responde `LOGYX-FLT-009` si se intenta ampliar.
 */
export default function VehicleConfigDialog({ vehicle, onClose, onSaved }: { vehicle: VehicleView | null; onClose: () => void; onSaved: (v: VehicleView) => void }) {
  const allowed = useMemo(() => cargoTypesFor(vehicle?.type), [vehicle?.type]);
  const [equipment, setEquipment] = useState<string[]>([]);
  const [accepted, setAccepted] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!vehicle) return;
    setEquipment(vehicle.equipment ?? []);
    const saved = (vehicle.acceptedCargoTypes ?? []).filter((c) => allowed.includes(c));
    setAccepted(saved.length ? saved : allowed);
    setError(null); setBusy(false);
  }, [vehicle?.id]);

  const toggle = (list: string[], v: string, on: boolean) => (on ? [...list.filter((x) => x !== v), v] : list.filter((x) => x !== v));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!vehicle || busy) return;
    if (accepted.length === 0) { setError('Deja al menos un tipo de carga. Si no quieres usar este vehículo, ponlo en mantenimiento.'); return; }
    setError(null);
    setBusy(true);
    const unrestricted = accepted.length === allowed.length;
    const { data, error: err } = await actions.fleet.vehicleConfig({
      id: vehicle.id!,
      equipment: equipment as never,
      acceptedCargoTypes: (unrestricted ? [] : accepted) as never,
    });
    setBusy(false);
    if (err) { const r = toFormErrors(err, ['acceptedCargoTypes']); setError(r.errors.acceptedCargoTypes ?? r.formError ?? 'No pudimos guardar la configuración.'); return; }
    toast.success('Configuración guardada', `${vehicle.plate} quedó actualizado.`);
    onSaved(data as VehicleView);
    onClose();
  }

  return (
    <Dialog open={!!vehicle} onOpenChange={(o) => !o && !busy && onClose()} title={vehicle ? `Configurar ${vehicle.plate}` : 'Configurar vehículo'}
      description={vehicle ? `${VEHICLE_LABELS[vehicle.type!] ?? vehicle.type}. Lo que declares aquí lo usamos para ofrecerte cargas y avisarte de las que coinciden.` : undefined}
      className="max-w-xl"
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="vehicle-config-form" loading={busy}>{busy ? 'Guardando…' : 'Guardar configuración'}</Button></>}>
      {vehicle && (
        <form id="vehicle-config-form" onSubmit={submit} noValidate method="post" className="space-y-5">
          {error && <Alert tone="error">{error}</Alert>}
          <fieldset>
            <legend className="text-sm font-semibold text-ink-900">Equipamiento</legend>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              {EQUIPMENT.map((e) => <Check key={e.value} id={`eq-${e.value}`} label={e.label} checked={equipment.includes(e.value)} onChange={(on) => setEquipment((l) => toggle(l, e.value, on))} />)}
            </div>
          </fieldset>
          <fieldset>
            <legend className="text-sm font-semibold text-ink-900">Tipos de carga que aceptas</legend>
            <p className="mt-0.5 text-xs text-ink-500">Un vehículo {(VEHICLE_LABELS[vehicle.type!] ?? '').toLowerCase()} solo puede llevar estos tipos. Desmarca los que no quieres llevar.</p>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              {allowed.map((c) => <Check key={c} id={`cg-${c}`} label={CARGO_LABELS[c] ?? c} checked={accepted.includes(c)} onChange={(on) => setAccepted((l) => toggle(l, c, on))} />)}
            </div>
          </fieldset>
        </form>
      )}
    </Dialog>
  );
}
