import { useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { CheckCircle2, MoreHorizontal, Plus, Route, SearchX, Truck, UserRound, Wrench } from 'lucide-react';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { NumberInput } from '@/components/ui/number-input';
import { Dialog } from '@/components/ui/dialog';
import { Alert } from '@/components/ui/alert';
import { Avatar } from '@/components/ui/avatar';
import { Tooltip } from '@/components/ui/tooltip';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { flash, toast } from '@/components/ui/toaster';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { QuickFilters } from '@/components/ui/quick-filters';
import { StatusPill } from '@/components/domain/StatusPill';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { EmptyState } from '@/components/domain/EmptyState';
import { formatNumber, VEHICLE_LABELS } from '@/lib/format';
import type { DriverView, VehicleView } from '@/lib/api/server';
/** campo de la API → clave de error local */
const FIELD_MAP: Record<string, string> = { plate: 'plate', email: 'email' };


const TYPE_OPTIONS = Object.entries(VEHICLE_LABELS).map(([value, label]) => ({ value, label }));
type VType = 'VAN' | 'TRUCK_SMALL' | 'TRUCK_MEDIUM' | 'TRUCK_LARGE' | 'PLATFORM' | 'REFRIGERATED' | 'TANKER';

function VehicleDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [plate, setPlate] = useState('');
  const [type, setType] = useState('TRUCK_MEDIUM');
  const [weight, setWeight] = useState('');
  const [volume, setVolume] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const errs: Record<string, string> = {};
    if (plate.trim().length < 5) errs.plate = 'Ingresa la placa del vehículo.';
    if (!(Number(weight) > 0)) errs.weight = 'Ingresa la capacidad en kg.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const vol = Number(volume);
    const { error } = await actions.fleet.createVehicle({ plate: plate.trim().toUpperCase(), type: type as VType, maxWeightKg: Number(weight), maxVolumeM3: vol > 0 ? vol : undefined });
    if (error) { const { message, field } = splitActionError(error); if (field && field in FIELD_MAP) setErrors({ [FIELD_MAP[field]]: message }); else setError(message); setBusy(false); return; }
    flash('Vehículo agregado');
    window.location.reload();
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !busy && onClose()} title="Agregar vehículo" description="Los vehículos disponibles te permiten enviar ofertas según su capacidad."
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="vehicle-form" loading={busy}>{busy ? 'Agregando vehículo…' : 'Agregar vehículo'}</Button></>}>
      <form id="vehicle-form" onSubmit={submit} noValidate className="space-y-4">
        {error && <Alert tone="error" title="No pudimos agregar el vehículo">{error}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Placa" htmlFor="v-plate" error={errors.plate}><Input id="v-plate" value={plate} onChange={(e) => setPlate(e.target.value)} placeholder="ABC-123" maxLength={10} aria-invalid={!!errors.plate} className="font-mono uppercase" /></Field>
          <Field label="Tipo de vehículo" htmlFor="v-type"><Select id="v-type" value={type} onValueChange={setType} options={TYPE_OPTIONS} /></Field>
          <Field label="Capacidad de carga" htmlFor="v-w" error={errors.weight}><NumberInput id="v-w" value={weight} onValueChange={setWeight} suffix="kg" placeholder="8,000" invalid={!!errors.weight} /></Field>
          <Field label="Volumen" htmlFor="v-vol" hint="Opcional"><NumberInput id="v-vol" value={volume} onValueChange={setVolume} decimals={1} suffix="m³" placeholder="30" /></Field>
        </div>
      </form>
    </Dialog>
  );
}

function DriverDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [v, setV] = useState({ fullName: '', licenseNumber: '', email: '', password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof v) => (e: React.ChangeEvent<HTMLInputElement>) => setV((s) => ({ ...s, [k]: e.target.value }));

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const errs: Record<string, string> = {};
    if (v.fullName.trim().length < 2) errs.fullName = 'Ingresa el nombre completo.';
    if (v.licenseNumber.trim().length < 3) errs.licenseNumber = 'Ingresa el número de licencia.';
    if (!/^\S+@\S+\.\S+$/.test(v.email.trim())) errs.email = 'Ingresa un correo válido, por ejemplo luis@empresa.pe.';
    if (v.password.length < 8) errs.password = 'La contraseña debe tener al menos 8 caracteres.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    const { error } = await actions.fleet.createDriver({ fullName: v.fullName.trim(), email: v.email.trim(), password: v.password, licenseNumber: v.licenseNumber.trim() });
    if (error) { const { message, field } = splitActionError(error); if (field && field in FIELD_MAP) setErrors({ [FIELD_MAP[field]]: message }); else setError(message); setBusy(false); return; }
    flash('Conductor registrado');
    window.location.reload();
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !busy && onClose()} title="Registrar conductor" description="Usará su correo y contraseña para ingresar a la app móvil."
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="driver-form" loading={busy}>{busy ? 'Registrando conductor…' : 'Registrar conductor'}</Button></>}>
      <form id="driver-form" onSubmit={submit} noValidate className="space-y-4">
        {error && <Alert tone="error" title="No pudimos registrar al conductor">{error}</Alert>}
        <Field label="Nombre completo" htmlFor="d-name" error={errors.fullName}><Input id="d-name" value={v.fullName} onChange={set('fullName')} placeholder="Luis Quispe Mamani" autoComplete="off" aria-invalid={!!errors.fullName} /></Field>
        <Field label="N.° de licencia" htmlFor="d-lic" error={errors.licenseNumber}><Input id="d-lic" value={v.licenseNumber} onChange={set('licenseNumber')} placeholder="Q12345678" className="font-mono" autoComplete="off" aria-invalid={!!errors.licenseNumber} /></Field>
        <Field label="Correo de acceso" htmlFor="d-email" error={errors.email}><Input id="d-email" type="email" value={v.email} onChange={set('email')} placeholder="luis@empresa.pe" autoComplete="off" aria-invalid={!!errors.email} /></Field>
        <Field label="Contraseña inicial" htmlFor="d-pass" error={errors.password} hint="Mínimo 8 caracteres. El conductor podrá cambiarla luego."><PasswordInput id="d-pass" value={v.password} onChange={set('password')} autoComplete="new-password" aria-invalid={!!errors.password} /></Field>
      </form>
    </Dialog>
  );
}

const STATUS_ACTIONS = [
  { status: 'AVAILABLE', label: 'Marcar como disponible', icon: CheckCircle2 },
  { status: 'IN_ROUTE', label: 'Marcar en ruta', icon: Route },
  { status: 'MAINTENANCE', label: 'Enviar a mantenimiento', icon: Wrench },
] as const;

function VehicleActions({ v }: { v: VehicleView }) {
  const [busy, setBusy] = useState(false);
  async function change(status: (typeof STATUS_ACTIONS)[number]['status']) {
    setBusy(true);
    const { error } = await actions.fleet.setVehicleStatus({ id: v.id!, status });
    if (error) { toast.error(error.message); setBusy(false); return; }
    flash('Estado actualizado');
    window.location.reload();
  }
  return (
    <DropdownMenu>
      <Tooltip content="Cambiar estado"><DropdownMenuTrigger asChild><Button variant="outline" size="sm" loading={busy} aria-label={`Acciones de ${v.plate}`} className="@min-[720px]:border-transparent @min-[720px]:px-2"><MoreHorizontal /><span className="@min-[720px]:sr-only">Acciones</span></Button></DropdownMenuTrigger></Tooltip>
      <DropdownMenuContent>
        <DropdownMenuLabel>Cambiar estado</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {STATUS_ACTIONS.filter((a) => a.status !== v.status).map((a) => (
          <DropdownMenuItem key={a.status} onSelect={() => change(a.status)}><a.icon aria-hidden="true" /> {a.label}</DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export default function FleetManager({ vehicles, drivers }: { vehicles: VehicleView[]; drivers: DriverView[] }) {
  const [dialog, setDialog] = useState<'vehicle' | 'driver' | null>(null);
  const [vf, setVf] = useState('');
  const count = (s: string) => vehicles.filter((v) => v.status === s).length;
  const close = () => setDialog(null);
  const shown = vf ? vehicles.filter((v) => v.status === vf) : vehicles;
  const maint = count('MAINTENANCE');
  const onDuty = drivers.filter((d) => d.status === 'ON_DUTY').length;
  return (
    <div className="space-y-6">
      <KpiGrid>
        <KpiTile label="Vehículos" value={vehicles.length} context="en tu flota" icon={Truck} tone="petrol" />
        <KpiTile label="Disponibles" value={count('AVAILABLE')} context="listos para ofertar" icon={CheckCircle2} tone="money" />
        <KpiTile label="En ruta" value={count('IN_ROUTE')} context={maint ? `${maint} en mantenimiento` : 'con un envío activo'} icon={Route} tone={maint ? 'warn' : 'default'} />
        <KpiTile label="Conductores" value={drivers.length} context={onDuty ? `${onDuty} en servicio` : 'con acceso a la app'} icon={UserRound} />
      </KpiGrid>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Truck className="size-4 text-petrol-700" aria-hidden="true" /> Vehículos <span className="font-mono text-ink-500">({vehicles.length})</span></CardTitle>
          <Button size="sm" onClick={() => setDialog('vehicle')}><Plus /> Agregar vehículo</Button>
        </CardHeader>
        {vehicles.length === 0 ? (
          <EmptyState title="Registra tu primer vehículo" description="Necesitas al menos un vehículo disponible para enviar ofertas." action={<Button onClick={() => setDialog('vehicle')}><Plus /> Agregar vehículo</Button>} />
        ) : (
          <>
            <div className="border-b border-steel-200 px-4 py-3 sm:px-5">
              <QuickFilters label="Estado del vehículo" value={vf} onValueChange={setVf} options={[
                { value: '', label: 'Todos', count: vehicles.length },
                { value: 'AVAILABLE', label: 'Disponibles', count: count('AVAILABLE') },
                { value: 'IN_ROUTE', label: 'En ruta', count: count('IN_ROUTE') },
                { value: 'MAINTENANCE', label: 'Mantenimiento', count: maint },
              ]} />
            </div>
            {shown.length === 0 ? (
              <EmptyState compact icon={SearchX} title="Ningún vehículo con este estado" description="Cambia el filtro para ver el resto de tu flota." action={<Button size="sm" variant="outline" onClick={() => setVf('')}>Ver todos los vehículos</Button>} />
            ) : (
              <Table label="Vehículos de tu flota" stack>
                <thead><tr><Th>Placa</Th><Th>Tipo</Th><Th className="text-right">Capacidad</Th><Th className="text-right">Volumen</Th><Th>Estado</Th><Th className="w-12"><span className="sr-only">Acciones</span></Th></tr></thead>
                <tbody>
                  {shown.map((v) => (
                    <Tr key={v.id}>
                      <Td primary className="font-mono text-[15px] font-semibold tracking-wide">{v.plate}</Td>
                      <Td label="Tipo">{VEHICLE_LABELS[v.type!]}</Td>
                      <Td label="Capacidad" className="whitespace-nowrap text-right font-mono tabular-nums">{formatNumber(v.maxWeightKg)} kg</Td>
                      <Td label="Volumen" className="whitespace-nowrap text-right font-mono tabular-nums">{v.maxVolumeM3 ? `${formatNumber(v.maxVolumeM3, 1)} m³` : '—'}</Td>
                      <Td label="Estado"><StatusPill status={v.status!} /></Td>
                      <Td actions className="text-right"><VehicleActions v={v} /></Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </>
        )}
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><UserRound className="size-4 text-petrol-700" aria-hidden="true" /> Conductores <span className="font-mono text-ink-500">({drivers.length})</span></CardTitle>
          <Button size="sm" variant="outline" onClick={() => setDialog('driver')}><Plus /> Registrar conductor</Button>
        </CardHeader>
        {drivers.length === 0 ? (
          <EmptyState title="Aún no tienes conductores" description="Registra conductores para asignarlos a tus envíos; usarán la app móvil." action={<Button onClick={() => setDialog('driver')}><Plus /> Registrar conductor</Button>} />
        ) : (
          <Table label="Conductores" stack>
            <thead><tr><Th>Conductor</Th><Th>Licencia</Th><Th>Correo</Th><Th>Estado</Th></tr></thead>
            <tbody>
              {drivers.map((d) => (
                <Tr key={d.id}>
                  <Td primary><div className="flex items-center gap-3"><Avatar name={d.fullName ?? '?'} /><div className="min-w-0 font-medium">{d.fullName}</div></div></Td>
                  <Td label="Licencia" className="font-mono">{d.licenseNumber}</Td>
                  <Td label="Correo" className="text-ink-700"><span className="min-w-0 truncate">{d.email}</span></Td>
                  <Td label="Estado"><StatusPill status={d.status!} /></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <VehicleDialog open={dialog === 'vehicle'} onClose={close} />
      <DriverDialog open={dialog === 'driver'} onClose={close} />
    </div>
  );
}
