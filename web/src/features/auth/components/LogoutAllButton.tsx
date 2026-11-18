import { useState } from 'react';
import { actions } from 'astro:actions';
import { LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';

/** "Cerrar sesión en todos los dispositivos" (`logout-all`): invalida todos los tokens, también el de este navegador. */
export default function LogoutAllButton() {
  const [busy, setBusy] = useState(false);
  async function go() {
    const ok = await confirm({
      title: '¿Cerrar sesión en todos los dispositivos?',
      description: 'Saldrás de OnCargo en este navegador y en tu celular o cualquier otro equipo donde hayas ingresado. Tendrás que ingresar de nuevo en cada uno.',
      confirmLabel: 'Cerrar todas las sesiones',
      tone: 'destructive',
    });
    if (!ok) return;
    setBusy(true);
    const { error } = await actions.password.logoutAll();
    if (error) { toast.error(error.message); setBusy(false); return; }
    window.location.href = '/login?expired=1';
  }
  return (
    <Button variant="outline" loading={busy} onClick={go}><LogOut aria-hidden="true" /> {busy ? 'Cerrando sesiones…' : 'Cerrar sesión en todos los dispositivos'}</Button>
  );
}
