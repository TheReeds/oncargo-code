import { useState } from 'react';
import { actions } from 'astro:actions';
import { Ban, Star, StarOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { Badge } from '@/components/ui/badge';
import { Can } from '@/lib/permissions/react';
import type { PreferenceKind } from '../server';

export type Mark = PreferenceKind | null;

/** Etiqueta «Preferido» / «Bloqueado» junto al nombre del transportista. */
export function MarkBadge({ kind }: { kind: Mark | undefined }) {
  if (kind === 'PREFERRED') return <Badge tone="emerald"><Star className="size-3" aria-hidden="true" />Preferido</Badge>;
  if (kind === 'BLOCKED') return <Badge tone="red"><Ban className="size-3" aria-hidden="true" />Bloqueado</Badge>;
  return null;
}

/**
 * Marca a un transportista como preferido o bloqueado, o quita la marca (roadmap 2.9). La marca es privada de la PYME:
 * el transportista no se entera. Bloquear le oculta tus solicitudes y le impide ofertar; no cancela envíos en curso.
 */
export function CarrierMarkMenu({ carrierOrgId, carrierName, kind, onChange }: { carrierOrgId: string; carrierName?: string | null; kind: Mark | undefined; onChange: (k: Mark) => void }) {
  const [busy, setBusy] = useState(false);
  const name = carrierName ?? 'este transportista';

  async function apply(next: Mark) {
    if (next === 'BLOCKED' && !(await confirm({
      title: `¿Bloquear a ${name}?`,
      description: 'No verá tus solicitudes nuevas ni podrá ofertar en ellas, y no podrás aceptar sus ofertas pendientes. Sus envíos en curso no cambian. Puedes quitar el bloqueo cuando quieras.',
      confirmLabel: 'Bloquear transportista', tone: 'destructive',
    }))) return;
    setBusy(true);
    const { error } = next ? await actions.preferences.mark({ carrierOrgId, kind: next }) : await actions.preferences.unmark({ carrierOrgId });
    setBusy(false);
    if (error) return void toast.error('No pudimos guardar la marca', error.message);
    onChange(next);
    toast.success(next === 'PREFERRED' ? 'Transportista marcado como preferido' : next === 'BLOCKED' ? 'Transportista bloqueado' : 'Marca quitada');
  }

  return (
    <Can fallback={null}>
      <DropdownMenu>
        <DropdownMenuTrigger asChild><Button size="sm" variant="ghost" loading={busy}><Star />Marcar<span className="sr-only"> a {name}</span></Button></DropdownMenuTrigger>
        <DropdownMenuContent>
          {kind !== 'PREFERRED' && <DropdownMenuItem onSelect={() => void apply('PREFERRED')}><Star />Marcar como preferido</DropdownMenuItem>}
          {kind !== 'BLOCKED' && <DropdownMenuItem destructive onSelect={() => void apply('BLOCKED')}><Ban />Bloquear transportista</DropdownMenuItem>}
          {kind && <DropdownMenuItem onSelect={() => void apply(null)}><StarOff />Quitar marca</DropdownMenuItem>}
        </DropdownMenuContent>
      </DropdownMenu>
    </Can>
  );
}
