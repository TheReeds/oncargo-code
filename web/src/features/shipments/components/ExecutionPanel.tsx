import { useId, useState } from 'react';
import { actions } from 'astro:actions';
import { Building2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { NumberInput } from '@/components/ui/number-input';
import { InlineLabel, Switch } from '@/components/ui/form-controls';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { toast } from '@/components/ui/toaster';
import { Can } from '@/lib/permissions/react';
import { splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { CITY_LABELS } from '@/lib/format';
import type { ShipmentView, StopView } from '@/lib/api/server';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';

/** Siguiente paso pendiente del envío: llegar o cerrar la siguiente parada (RN08). */
export function nextStep(shipment: ShipmentView): { stop: StopView; step: 'arrive' | 'complete' } | null {
  if (shipment.status !== 'PENDING' && shipment.status !== 'IN_TRANSIT') return null;
  const stop = [...(shipment.stops ?? [])].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).find((s) => s.status !== 'COMPLETED');
  if (!stop) return null;
  return { stop, step: stop.status === 'ARRIVED' ? 'complete' : 'arrive' };
}

/**
 * Ejecución del envío (roadmap 2.5): la PYME elige qué pide el recojo (foto de la carga y peso medido) y la oficina del
 * transportista registra una parada por el conductor cuando él no puede (motivo obligatorio, queda marcado y auditado).
 */
export default function ExecutionPanel({ shipment, perspective, onChanged }: { shipment: ShipmentView; perspective: Perspective; onChanged: (s: ShipmentView) => void }) {
  if (perspective === 'PYME' && shipment.status === 'PENDING') return <PickupRequirements shipment={shipment} onChanged={onChanged} />;
  if (perspective === 'CARRIER' && nextStep(shipment)) return <OfficeStop shipment={shipment} onChanged={onChanged} />;
  return null;
}

function PickupRequirements({ shipment, onChanged }: { shipment: ShipmentView; onChanged: (s: ShipmentView) => void }) {
  const uid = useId();
  const [busy, setBusy] = useState(false);
  const photo = !!shipment.pickupPhotoRequired;
  const weight = !!shipment.pickupWeightRequired;

  async function save(next: { photo: boolean; weight: boolean }) {
    setBusy(true);
    const { data, error } = await actions.shipments.pickupRequirements({ id: shipment.id!, photoRequired: next.photo, weightRequired: next.weight });
    setBusy(false);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Recojo actualizado');
    if (data) onChanged(data);
  }

  return (
    <section className="rounded-card border border-steel-200 bg-surface p-4" aria-labelledby={`${uid}-t`}>
      <h3 id={`${uid}-t`} className="text-[15px] font-semibold text-ink-900">Qué pide el recojo</h3>
      <p className="text-sm text-ink-500">El conductor no podrá cerrar el recojo sin esto.</p>
      <Can readOnly="No puedes cambiar lo que pide el recojo." fallback={<p className="mt-2 text-sm text-ink-700">Foto de la carga: {photo ? 'sí' : 'no'} · Peso medido: {weight ? 'sí' : 'no'}</p>}>
        <div className="mt-3 flex flex-wrap gap-x-6 gap-y-3">
          <div className="flex items-center gap-2">
            <Switch id={`${uid}-photo`} checked={photo} disabled={busy} onCheckedChange={(c) => save({ photo: c, weight })} />
            <InlineLabel htmlFor={`${uid}-photo`}>Foto de la carga</InlineLabel>
          </div>
          <div className="flex items-center gap-2">
            <Switch id={`${uid}-weight`} checked={weight} disabled={busy} onCheckedChange={(c) => save({ photo, weight: c })} />
            <InlineLabel htmlFor={`${uid}-weight`}>Peso medido</InlineLabel>
          </div>
        </div>
      </Can>
    </section>
  );
}

function OfficeStop({ shipment, onChanged }: { shipment: ShipmentView; onChanged: (s: ShipmentView) => void }) {
  const uid = useId();
  const next = nextStep(shipment)!;
  const [open, setOpen] = useState(false);
  const [weight, setWeight] = useState('');
  const [receiver, setReceiver] = useState('');
  const [doc, setDoc] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const pickup = next.stop.type === 'PICKUP';
  const where = `${pickup ? 'recojo' : 'entrega'} en ${CITY_LABELS[next.stop.city!] ?? next.stop.city}`;
  const label = next.step === 'arrive' ? `Registrar llegada al ${where}` : pickup ? 'Registrar carga recogida' : 'Registrar entrega';

  return (
    <section className="flex flex-wrap items-center gap-3 rounded-card border border-steel-200 bg-surface p-4" aria-labelledby={`${uid}-t`}>
      <div className="min-w-0 flex-1">
        <h3 id={`${uid}-t`} className="text-[15px] font-semibold text-ink-900">¿El conductor no puede registrar la parada?</h3>
        <p className="text-sm text-ink-500">Regístrala desde aquí con el motivo. Queda marcada en el envío. La foto de entrega solo la sube el conductor.</p>
      </div>
      <Can>
        <Button variant="outline" onClick={() => { setFieldErrors({}); setOpen(true); }}><Building2 aria-hidden="true" /> {label}</Button>
      </Can>
      <ReasonDialog
        open={open} onOpenChange={setOpen} title={label} required maxLength={LIMITS.officeReason}
        description="La PYME verá que lo registró la oficina y por qué."
        placeholder="Celular sin batería" confirmLabel={label}
        onFailure={(f) => { if (f.field && ['weightKg', 'receivedByName', 'receivedByDoc'].includes(f.field)) { setFieldErrors({ [f.field]: f.message }); return true; } return false; }}
        onSubmit={async (reason) => {
          const { data, error } = await actions.shipments.officeStop({
            id: shipment.id!, stopId: next.stop.id!, step: next.step, reason,
            weightKg: pickup && next.step === 'complete' && weight ? Number(weight) : undefined,
            receivedByName: !pickup && next.step === 'complete' ? receiver : undefined,
            receivedByDoc: !pickup && next.step === 'complete' ? doc : undefined,
          });
          if (error) return splitActionError(error);
          toast.success('Parada registrada');
          if (data) onChanged(data);
          return null;
        }}
      >
        {next.step === 'complete' && pickup && (
          <Field label="Peso medido" htmlFor={`${uid}-w`} error={fieldErrors.weightKg} required={!!shipment.pickupWeightRequired} hint={shipment.pickupWeightRequired ? 'La PYME lo pidió.' : 'Opcional.'}>
            <NumberInput id={`${uid}-w`} value={weight} onValueChange={setWeight} suffix="kg" invalid={!!fieldErrors.weightKg} />
          </Field>
        )}
        {next.step === 'complete' && !pickup && (
          <>
            <Field label="Quién recibió" htmlFor={`${uid}-r`} error={fieldErrors.receivedByName}>
              <Input id={`${uid}-r`} value={receiver} maxLength={120} onChange={(e) => setReceiver(e.target.value)} placeholder="Ana Quispe" />
            </Field>
            <Field label="DNI o carné de extranjería" htmlFor={`${uid}-d`} error={fieldErrors.receivedByDoc}>
              <Input id={`${uid}-d`} value={doc} maxLength={20} inputMode="numeric" onChange={(e) => setDoc(e.target.value)} placeholder="45678901" />
            </Field>
          </>
        )}
      </ReasonDialog>
    </section>
  );
}
