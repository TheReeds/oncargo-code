import { useMemo, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Ban, MailPlus, MoreHorizontal, RotateCcw, SearchX, Send, ShieldCheck, UserCog, UserPlus, Users } from 'lucide-react';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Dialog } from '@/components/ui/dialog';
import { Alert } from '@/components/ui/alert';
import { Avatar } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/tooltip';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { QuickFilters } from '@/components/ui/quick-filters';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { StatusPill } from '@/components/domain/StatusPill';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { EmptyState } from '@/components/domain/EmptyState';
import { toFormErrors, splitActionError } from '@/lib/action-error';
import { LIMITS } from '@/lib/limits';
import { ROLE_LABEL, type OrgRole } from '@/lib/permissions';
import { formatDateTime, formatRelative } from '@/lib/format';
import type { OrganizationUser } from '../server';
import { RoleChoice, type AssignableRole } from './RoleChoice';

const stateOf = (u: OrganizationUser) => (!u.active ? 'INACTIVE' : u.invitationPending ? 'INVITED' : 'ACTIVE');
const roleTone = (r?: string) => (r === 'ADMIN' ? 'petrol' : 'neutral') as 'petrol' | 'neutral';
const EMAIL_RE = /^\S+@\S+\.\S+$/;

function InviteDialog({ open, onClose, onInvited }: { open: boolean; onClose: () => void; onInvited: (u: OrganizationUser) => void }) {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<AssignableRole>('OPS');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);

  function reset() { setFullName(''); setEmail(''); setRole('OPS'); setErrors({}); setFormError(null); }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
    setFormError(null);
    const errs: Record<string, string> = {};
    if (fullName.trim().length < 2) errs.fullName = 'Ingresa el nombre completo.';
    if (!EMAIL_RE.test(email.trim())) errs.email = 'Ingresa un correo válido, por ejemplo ana@empresa.pe.';
    setErrors(errs);
    if (errs.fullName) { nameRef.current?.focus(); return; }
    if (errs.email) { emailRef.current?.focus(); return; }
    setBusy(true);
    const { data, error } = await actions.organization.invite({ fullName: fullName.trim(), email: email.trim(), role });
    setBusy(false);
    if (error) {
      const r = toFormErrors(error, ['email', 'fullName', 'role']);
      setErrors(r.errors);
      setFormError(r.formError);
      return;
    }
    toast.success('Invitación enviada', `Enviamos un correo a ${data.email}. El enlace vence en 72 horas.`);
    onInvited(data);
    reset();
    onClose();
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && !busy && onClose()} title="Invitar usuario" description="Le enviaremos un correo para que cree su contraseña y active su acceso a tu empresa."
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="invite-form" loading={busy}><MailPlus aria-hidden="true" /> {busy ? 'Enviando invitación…' : 'Enviar invitación'}</Button></>}>
      <form id="invite-form" onSubmit={submit} noValidate className="space-y-4">
        {formError && <Alert tone="error" title="No pudimos enviar la invitación">{formError}</Alert>}
        <Field label="Nombre completo" htmlFor="inv-name" error={errors.fullName}>
          <Input ref={nameRef} id="inv-name" value={fullName} maxLength={LIMITS.fullName} autoComplete="off" placeholder="Ana Torres Quispe" aria-invalid={!!errors.fullName} onChange={(e) => { setFullName(e.currentTarget.value); setErrors((p) => ({ ...p, fullName: '' })); }} />
        </Field>
        <Field label="Correo electrónico" htmlFor="inv-email" error={errors.email}>
          <Input ref={emailRef} id="inv-email" type="email" value={email} autoComplete="off" autoCapitalize="none" spellCheck={false} placeholder="ana@empresa.pe" aria-invalid={!!errors.email} onChange={(e) => { setEmail(e.currentTarget.value); setErrors((p) => ({ ...p, email: '' })); setFormError(null); }} />
        </Field>
        <RoleChoice value={role} onValueChange={setRole} idPrefix="inv-role" />
      </form>
    </Dialog>
  );
}

function RoleDialog({ user, self, onClose, onChanged }: { user: OrganizationUser | null; self: boolean; onClose: () => void; onChanged: (u: OrganizationUser) => void }) {
  const [role, setRole] = useState<AssignableRole>('OPS');
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [forId, setForId] = useState<string | null>(null);
  // Reinicia la selección al abrir para otro usuario.
  if (user && forId !== user.id) { setForId(user.id ?? null); setRole((user.role as AssignableRole) ?? 'OPS'); setFormError(null); }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!user || busy) return;
    if (role === user.role) { onClose(); return; }
    setBusy(true);
    setFormError(null);
    const { data, error } = await actions.organization.changeRole({ id: user.id!, role });
    setBusy(false);
    if (error) { setFormError(splitActionError(error).message); return; }
    // Cambiar el propio rol sube la versión de sesión: el siguiente pedido da 401, así que vamos directo al login.
    if (self) { window.location.href = '/login?expired=1'; return; }
    toast.success('Rol actualizado', `${user.fullName} ahora es ${ROLE_LABEL[data.role as OrgRole]}. Cerramos sus sesiones para aplicar el cambio.`);
    onChanged(data);
    onClose();
  }

  return (
    <Dialog open={!!user} onOpenChange={(o) => !o && !busy && onClose()} title="Cambiar rol" description={user ? `${user.fullName} · ${user.email}` : undefined}
      footer={<><Button variant="outline" onClick={onClose} disabled={busy}>Volver</Button><Button type="submit" form="role-form" loading={busy}>{busy ? 'Guardando…' : 'Guardar rol'}</Button></>}>
      <form id="role-form" onSubmit={submit} className="space-y-4">
        {self && <Alert tone="warning" title="Estás cambiando tu propio rol">Se cerrará tu sesión y tendrás que ingresar de nuevo. Si te quitas el rol de administrador, otra persona tendrá que devolvértelo.</Alert>}
        {formError && <Alert tone="error">{formError}</Alert>}
        <RoleChoice value={role} onValueChange={setRole} idPrefix="chg-role" />
      </form>
    </Dialog>
  );
}

export default function UsersManager({ users: initial, selfId }: { users: OrganizationUser[]; selfId: string }) {
  const [users, setUsers] = useState(initial);
  const [filter, setFilter] = useState('');
  const [invite, setInvite] = useState(false);
  const [roleFor, setRoleFor] = useState<OrganizationUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const count = (s: string) => users.filter((u) => stateOf(u) === s).length;
  const activeAdmins = users.filter((u) => u.role === 'ADMIN' && u.active && !u.invitationPending).length;
  const shown = useMemo(() => (filter ? users.filter((u) => stateOf(u) === filter) : users), [users, filter]);
  const replace = (u: OrganizationUser) => setUsers((list) => list.map((x) => (x.id === u.id ? u : x)));

  async function run(u: OrganizationUser, fn: () => Promise<{ data?: OrganizationUser; error?: { message: string } }>, ok: string, detail?: string) {
    setBusyId(u.id!);
    setError(null);
    const { data, error: err } = await fn();
    setBusyId(null);
    if (err) { setError(splitActionError(err).message); return; }
    if (data) replace(data);
    toast.success(ok, detail);
  }

  async function deactivate(u: OrganizationUser) {
    const okay = await confirm({ title: `¿Desactivar a ${u.fullName}?`, description: 'Perderá el acceso de inmediato y se cerrarán sus sesiones. Podrás reactivarlo cuando quieras.', confirmLabel: 'Desactivar usuario', tone: 'destructive' });
    if (okay) await run(u, () => actions.organization.deactivateUser({ id: u.id! }), 'Usuario desactivado');
  }

  return (
    <div className="space-y-5">
      <KpiGrid cols={3}>
        <KpiTile label="Usuarios activos" value={count('ACTIVE')} context="con acceso hoy" icon={Users} tone="petrol" />
        <KpiTile label="Administradores" value={activeAdmins} context={activeAdmins <= 1 ? 'debe quedar al menos uno' : 'activos'} icon={ShieldCheck} tone={activeAdmins <= 1 ? 'warn' : 'default'} />
        <KpiTile label="Invitaciones pendientes" value={count('INVITED')} context="aún no definen su contraseña" icon={MailPlus} tone={count('INVITED') ? 'warn' : 'default'} />
      </KpiGrid>

      {error && <Alert tone="error" title="No pudimos completar la acción">{error}</Alert>}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Users className="size-4 text-petrol-700" aria-hidden="true" /> Usuarios <span className="font-mono text-ink-500">({users.length})</span></CardTitle>
          <Button size="sm" onClick={() => setInvite(true)}><UserPlus /> Invitar usuario</Button>
        </CardHeader>
        <div className="border-b border-steel-200 px-4 py-3 sm:px-5">
          <QuickFilters label="Estado del usuario" value={filter} onValueChange={setFilter} options={[
            { value: '', label: 'Todos', count: users.length },
            { value: 'ACTIVE', label: 'Activos', count: count('ACTIVE') },
            { value: 'INVITED', label: 'Invitación pendiente', count: count('INVITED') },
            { value: 'INACTIVE', label: 'Desactivados', count: count('INACTIVE') },
          ]} />
        </div>
        {shown.length === 0 ? (
          <EmptyState compact icon={SearchX} title="Ningún usuario con este estado" description="Cambia el filtro para ver al resto del equipo." action={<Button size="sm" variant="outline" onClick={() => setFilter('')}>Ver todos los usuarios</Button>} />
        ) : (
          <Table label="Usuarios de tu empresa" stack>
            <thead><tr><Th>Usuario</Th><Th>Rol</Th><Th>Estado</Th><Th>Último acceso</Th><Th className="w-12"><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {shown.map((u) => {
                const self = u.id === selfId;
                return (
                  <Tr key={u.id} data-testid="user-row">
                    <Td primary>
                      <div className="flex items-center gap-3">
                        <span aria-hidden="true"><Avatar name={u.fullName ?? '?'} /></span>
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 font-medium text-ink-900">{u.fullName}{self && <Badge tone="petrol">Tú</Badge>}</div>
                          <div className="truncate text-xs text-ink-500">{u.email}</div>
                        </div>
                      </div>
                    </Td>
                    <Td label="Rol"><Badge tone={roleTone(u.role)}>{ROLE_LABEL[u.role as OrgRole] ?? u.role}</Badge></Td>
                    <Td label="Estado"><StatusPill kind="account" status={stateOf(u)} /></Td>
                    <Td label="Último acceso" className="whitespace-nowrap text-ink-700">
                      {u.lastLoginAt ? <span title={formatDateTime(u.lastLoginAt)}>{formatRelative(u.lastLoginAt)}</span> : <span className="text-ink-500">Aún no ingresa</span>}
                    </Td>
                    <Td actions className="text-right">
                      <DropdownMenu>
                        <Tooltip content="Acciones del usuario"><DropdownMenuTrigger asChild><Button variant="outline" size="sm" loading={busyId === u.id} aria-label={`Acciones de ${u.fullName}`} className="@min-[720px]:border-transparent @min-[720px]:px-2"><MoreHorizontal /><span className="@min-[720px]:sr-only">Acciones</span></Button></DropdownMenuTrigger></Tooltip>
                        <DropdownMenuContent>
                          <DropdownMenuLabel>{u.fullName}</DropdownMenuLabel>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem onSelect={() => setRoleFor(u)}><UserCog aria-hidden="true" /> Cambiar rol</DropdownMenuItem>
                          {u.active && u.invitationPending && (
                            <DropdownMenuItem onSelect={() => run(u, () => actions.organization.resendInvitation({ id: u.id! }), 'Invitación reenviada', `Enviamos un enlace nuevo a ${u.email}; el anterior dejó de valer.`)}><Send aria-hidden="true" /> Reenviar invitación</DropdownMenuItem>
                          )}
                          {u.active ? (
                            <DropdownMenuItem destructive disabled={self} onSelect={() => deactivate(u)}><Ban aria-hidden="true" /> {self ? 'No puedes desactivarte' : 'Desactivar usuario'}</DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem onSelect={() => run(u, () => actions.organization.reactivateUser({ id: u.id! }), 'Usuario reactivado')}><RotateCcw aria-hidden="true" /> Reactivar usuario</DropdownMenuItem>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>

      <InviteDialog open={invite} onClose={() => setInvite(false)} onInvited={(u) => setUsers((list) => [...list, u])} />
      <RoleDialog user={roleFor} self={roleFor?.id === selfId} onClose={() => setRoleFor(null)} onChanged={replace} />
    </div>
  );
}
