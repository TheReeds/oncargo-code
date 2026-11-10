import { LogOut, ShieldCheck, ChevronDown } from 'lucide-react';
import { Avatar } from '@/components/ui/avatar';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';

/** Menú de usuario del topbar. El cierre de sesión es un POST a /logout. */
export default function UserMenu({ name, email, role, verified }: { name: string; email: string; role: string; verified?: boolean }) {
  function logout() {
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/logout';
    document.body.appendChild(form);
    form.submit();
  }
  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="flex items-center gap-2 rounded-input p-1 pr-2 outline-offset-2 transition-colors hover:bg-steel-100 data-[state=open]:bg-steel-100" aria-label={`Menú de ${name}`}>
        <Avatar name={name} />
        <span className="hidden text-left text-xs leading-tight sm:block">
          <span className="block max-w-40 truncate font-medium text-ink-900">{name}</span>
          <span className="block text-ink-500">{role}</span>
        </span>
        <ChevronDown className="size-4 text-ink-500" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-64">
        <div className="px-2.5 py-2">
          <p className="truncate text-sm font-semibold text-ink-900">{name}</p>
          <p className="truncate text-xs text-ink-500">{email}</p>
          {verified && (
            <p className="mt-1.5 inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
              <ShieldCheck className="size-3" aria-hidden="true" /> Empresa verificada
            </p>
          )}
        </div>
        <DropdownMenuSeparator />
        <DropdownMenuLabel>Cuenta</DropdownMenuLabel>
        <DropdownMenuItem onSelect={logout}>
          <LogOut aria-hidden="true" /> Cerrar sesión
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
