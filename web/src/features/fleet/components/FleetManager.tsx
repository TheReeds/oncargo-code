import { useState } from 'react';
import { actions } from 'astro:actions';
import { Plus, Truck, UserRound } from 'lucide-react';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field, Input, Select } from '@/components/ui/input';
import { Sheet } from '@/components/ui/sheet';
import { Alert } from '@/components/ui/alert';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { formatNumber, VEHICLE_LABELS } from '@/lib/format';
import type { DriverView, VehicleView } from '@/lib/api/server';

function VehicleForm() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setError(null);
    setBusy(true);
    const vol = Number(f.get('maxVolumeM3'));
    const { error } = await actions.fleet.createVehicle({
      plate: String(f.get('plate')).trim().toUpperCase(), type: String(f.get('type')) as any,
      maxWeightKg: Number(f.get('maxWeightKg')), maxVolumeM3: vol > 0 ? vol : undefined,
    });
    if (error) { setError(error.message); setBusy(false); return; }
    window.location.reload();
  }
  return (
    <form onSubmit={submit} className="space-y-4 p-5">
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="Placa" htmlFor="v-plate"><Input id="v-plate" name="plate" required placeholder="ABC-123" className="font-mono uppercase" /></Field>
      <Field label="Tipo de vehículo" htmlFor="v-type"><Select id="v-type" name="type" defaultValue="TRUCK_MEDIUM">{Object.entries(VEHICLE_LABELS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</Select></Field>
      <Field label="Capacidad (kg)" htmlFor="v-w"><Input id="v-w" name="maxWeightKg" type="number" min="1" step="any" required className="font-mono" /></Field>
      <Field label="Volumen (m³)" htmlFor="v-vol" hint="Opcional"><Input id="v-vol" name="maxVolumeM3" type="number" min="0" step="any" className="font-mono" /></Field>
      <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Guardando…' : 'Agregar vehículo'}</Button>
    </form>
  );
}

function DriverForm() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setError(null);
    setBusy(true);
    const { error } = await actions.fleet.createDriver({
      fullName: String(f.get('fullName')).trim(), email: String(f.get('email')).trim(), password: String(f.get('password')), licenseNumber: String(f.get('licenseNumber')).trim(),
    });
    if (error) { setError(error.message); setBusy(false); return; }
    window.location.reload();
  }
  return (
    <form onSubmit={submit} className="space-y-4 p-5">
      {error && <Alert tone="error">{error}</Alert>}
      <Field label="Nombre completo" htmlFor="d-name"><Input id="d-name" name="fullName" required /></Field>
      <Field label="N.° de licencia" htmlFor="d-lic"><Input id="d-lic" name="licenseNumber" required className="font-mono" /></Field>
      <Field label="Correo (acceso a la app)" htmlFor="d-email"><Input id="d-email" name="email" type="email" required /></Field>
      <Field label="Contraseña inicial" htmlFor="d-pass" hint="Mínimo 8 caracteres"><Input id="d-pass" name="password" type="password" minLength={8} required autoComplete="new-password" /></Field>
      <Button type="submit" className="w-full" disabled={busy}>{busy ? 'Guardando…' : 'Registrar conductor'}</Button>
    </form>
  );
}

function VehicleStatus({ v }: { v: VehicleView }) {
  const [busy, setBusy] = useState(false);
  async function change(status: string) {
    setBusy(true);
    const { error } = await actions.fleet.setVehicleStatus({ id: v.id!, status: status as any });
    if (error) { alert(error.message); setBusy(false); return; }
    window.location.reload();
  }
  return (
    <div className="flex items-center justify-end gap-2">
      <StatusPill status={v.status!} />
      <label className="sr-only" htmlFor={`vs-${v.id}`}>Cambiar estado de {v.plate}</label>
      <select id={`vs-${v.id}`} disabled={busy} value={v.status} onChange={(e) => change(e.target.value)} className="h-8 rounded-input border border-steel-200 bg-surface px-2 text-xs">
        <option value="AVAILABLE">Disponible</option>
        <option value="IN_ROUTE">En ruta</option>
        <option value="MAINTENANCE">Mantenimiento</option>
      </select>
    </div>
  );
}

export default function FleetManager({ vehicles, drivers }: { vehicles: VehicleView[]; drivers: DriverView[] }) {
  const [sheet, setSheet] = useState<'vehicle' | 'driver' | null>(null);
  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Truck className="size-4 text-petrol-700" aria-hidden="true" /> Vehículos <span className="font-mono text-ink-500">({vehicles.length})</span></CardTitle>
          <Button size="sm" onClick={() => setSheet('vehicle')}><Plus /> Agregar vehículo</Button>
        </CardHeader>
        {vehicles.length === 0 ? (
          <EmptyState title="Registra tu primer vehículo" description="Necesitas al menos un vehículo disponible para enviar ofertas." action={<Button onClick={() => setSheet('vehicle')}>Agregar vehículo</Button>} />
        ) : (
          <Table>
            <thead><tr><Th>Placa</Th><Th>Tipo</Th><Th className="text-right">Capacidad</Th><Th className="text-right">Volumen</Th><Th className="text-right">Estado</Th></tr></thead>
            <tbody>
              {vehicles.map((v) => (
                <Tr key={v.id}>
                  <Td className="font-mono font-semibold">{v.plate}</Td>
                  <Td>{VEHICLE_LABELS[v.type!]}</Td>
                  <Td className="text-right font-mono tabular-nums">{formatNumber(v.maxWeightKg)} kg</Td>
                  <Td className="text-right font-mono tabular-nums">{v.maxVolumeM3 ? `${formatNumber(v.maxVolumeM3, 1)} m³` : '—'}</Td>
                  <Td><VehicleStatus v={v} /></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><UserRound className="size-4 text-petrol-700" aria-hidden="true" /> Conductores <span className="font-mono text-ink-500">({drivers.length})</span></CardTitle>
          <Button size="sm" variant="outline" onClick={() => setSheet('driver')}><Plus /> Registrar conductor</Button>
        </CardHeader>
        {drivers.length === 0 ? (
          <EmptyState title="Aún no tienes conductores" description="Registra conductores para asignarlos a tus envíos; usarán la app móvil." action={<Button onClick={() => setSheet('driver')}>Registrar conductor</Button>} />
        ) : (
          <Table>
            <thead><tr><Th>Nombre</Th><Th>Licencia</Th><Th>Correo</Th><Th className="text-right">Estado</Th></tr></thead>
            <tbody>
              {drivers.map((d) => (
                <Tr key={d.id}><Td className="font-medium">{d.fullName}</Td><Td className="font-mono">{d.licenseNumber}</Td><Td className="text-ink-700">{d.email}</Td><Td className="text-right"><StatusPill status={d.status!} /></Td></Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
      <Sheet open={sheet === 'vehicle'} onOpenChange={(o) => !o && setSheet(null)} title="Agregar vehículo"><VehicleForm /></Sheet>
      <Sheet open={sheet === 'driver'} onOpenChange={(o) => !o && setSheet(null)} title="Registrar conductor"><DriverForm /></Sheet>
    </div>
  );
}
