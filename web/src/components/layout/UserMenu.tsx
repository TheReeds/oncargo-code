import { LogOut, ShieldCheck, ChevronDown, UserRound, BellRing, KeyRound } from 'lucide-react';
import { openRequestAccess, roleLabel as orgRoleLabel } from '@/lib/permissions';
import { Avatar } from '@/components/ui/avatar';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';

/**
 * Menú de usuario del topbar. Muestra el rol de la persona («Administrador», «Operaciones», «Lector»), sus avisos y, a
 * quien solo consulta, «Pedir acceso». El cierre de sesión es un POST a /logout.
 */
export default function UserMenu({ name, email, role, org, roleName, verified }: {
  name: string;
  email: string;
  /** Etiqueta del rol de la persona (subtítulo del topbar). */
  role: string;
  /** Tipo de cuenta («Cuenta PYME», «Transportista»…). */
  org?: string;
  /** Rol de la organización (ADMIN, OPS, VIEWER). */
  roleName?: string;
  verified?: boolean;
}) {
  function logout() {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/logout';
    document.body.appendChild(form);
    form.submit();
  }
  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex items-center gap-2 rounded-input p-1 sm:pr-2 outline-offset-2 transition-colors hover:bg-steel-100 data-[state=open]:bg-steel-100">
        <span aria-hidden="true"><Avatar name={name} /></span>
        <span className="hidden text-left text-xs leading-tight sm:block">
          <span className="block max-w-40 truncate font-medium text-ink-900">{name}</span>
          <span className="block text-ink-500" data-testid="topbar-role">{role}</span>
        </span>
        <span className="sr-only">Menú de cuenta</span>
        <ChevronDown className="hidden size-4 text-ink-500 sm:block" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-64">
        <div className="px-2.5 py-2">
          <p className="truncate text-sm font-semibold text-ink-900">{name}</p>
          <p className="truncate text-xs text-ink-500">{email}</p>
          {roleName && <p className="mt-0.5 text-xs text-ink-500">Rol: <span className="font-medium text-ink-700" data-testid="user-role">{orgRoleLabel(roleName)}</span>{org ? <> · {org}</> : null}</p>}
          {verified && (
            <p className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
              <ShieldCheck className="size-3" aria-hidden="true" /> Verificado por OnCargo
            </p>
          )}
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>Cuenta</DropdownMenuLabel>
        <DropdownMenuItem asChild>
          <a href="/cuenta"><UserRound aria-hidden="true" /> Mi cuenta</a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a href="/cuenta/avisos"><BellRing aria-hidden="true" /> Mis avisos</a>
        </DropdownMenuItem>
        {roleName === 'VIEWER' && (
          <DropdownMenuItem onSelect={() => openRequestAccess('OPERATE')}>
            <KeyRound aria-hidden="true" /> Pedir acceso
          </DropdownMenuItem>
        )}
        <DropdownMenuItem onSelect={logout}>
          <LogOut aria-hidden="true" /> Cerrar sesión
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
