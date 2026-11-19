import { useState } from 'react';
import { actions } from 'astro:actions';
import { BookmarkPlus, Handshake, Send, Star, UserCheck } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { confirm } from '@/components/ui/confirm-dialog';
import { flash, toast } from '@/components/ui/toaster';
import { TrustScore } from '@/components/domain/TrustScore';
import { reportFailure } from '@/components/domain/VerificationBlock';
import { Can } from '@/lib/permissions/react';
import { VEHICLE_LABELS, formatDateTime, formatMoney } from '@/lib/format';
import { splitActionError } from '@/lib/action-error';
import { useLiveRefresh } from '@/lib/realtime';
import { SaveTemplateDialog } from '@/features/templates/components/SaveTemplateDialog';
import type { BackupOptionsView } from '@/features/preferences/server';
import { useRequestState } from './RequestLive';

/** Acciones del encabezado del detalle: guardar como plantilla e invitar a ofertar a los preferidos (roadmap 2.1 y 2.9). */
export function RequestDemandActions({ requestId, status, name }: { requestId: string; status: string; name: string }) {
  const { request } = useRequestState({ request: { status }, bids: [] });
  const [saving, setSaving] = useState(false);
  const [inviting, setInviting] = useState(false);

  async function invite() {
    setInviting(true);
    const { data, error } = await actions.preferences.invite({ requestId });
    setInviting(false);
    if (error) return void toast.error('No pudimos enviar la invitación', splitActionError(error).message);
    const n = data?.invited ?? 0;
    if (n === 0) toast.info('Aún no tienes transportistas preferidos', 'Márcalos desde las ofertas o desde Transportistas.');
    else toast.success(n === 1 ? 'Invitamos a 1 transportista' : `Invitamos a ${n} transportistas`, 'Les avisamos que tienes una carga para ofertar.');
  }

  return (
    <Can fallback={null}>
      <Button variant="outline" onClick={() => setSaving(true)}><BookmarkPlus />Guardar como plantilla</Button>
      {(request.status ?? status) === 'PUBLISHED' && <Button variant="outline" loading={inviting} onClick={() => void invite()}><Send />Invitar a ofertar</Button>}
      <SaveTemplateDialog open={saving} onOpenChange={setSaving} suggested={name}
        onSave={async (n, invitePreferred) => {
          const { error } = await actions.templates.fromRequest({ requestId, name: n, invitePreferred });
          if (error) return splitActionError(error);
          toast.success('Plantilla guardada', 'La encuentras en Plantillas.');
          return null;
        }} />
    </Can>
  );
}

const STATE_LABEL: Record<string, string> = { AVAILABLE: 'Disponible', REQUESTED: 'Esperando su respuesta', CONFIRMED: 'Confirmó', DECLINED: 'No puede llevarla', USED: 'Adjudicada' };

/**
 * Segunda oferta (roadmap 2.9): si el envío adjudicado se canceló en las 24 h siguientes, la PYME le pide confirmar al
 * siguiente transportista y, cuando él confirma, le adjudica la carga. Solo aparece en solicitudes canceladas.
 */
export function BackupBidsPanel({ requestId, initial }: { requestId: string; initial: BackupOptionsView | null }) {
  const [opts, setOpts] = useState<BackupOptionsView | null>(initial);
  const [busy, setBusy] = useState<string | null>(null);

  async function reload() {
    const { data } = await actions.backup.options({ requestId });
    if (data) setOpts(data);
  }
  useLiveRefresh({ types: ['notification.created'], refresh: reload, fallbackMs: 60_000, enabled: !!opts && opts.items!.length > 0 });

  if (!opts || (opts.items ?? []).length === 0) return null;
  const items = opts.items!;

  async function ask(bidId: string, name?: string | null) {
    setBusy(bidId);
    const { error } = await actions.backup.request({ bidId });
    setBusy(null);
    if (error) { toast.error('No pudimos pedir la confirmación', splitActionError(error).message); await reload(); return; }
    toast.success('Le pedimos confirmar', `${name ?? 'El transportista'} recibirá un aviso.`);
    await reload();
  }
  async function award(bidId: string, name: string | null | undefined, amount: number | undefined) {
    if (!(await confirm({
      title: `¿Adjudicar a ${name ?? 'este transportista'} por ${formatMoney(amount)}?`,
      description: 'Se crea un envío nuevo con esta oferta. El transportista ya confirmó que puede llevar la carga.',
      confirmLabel: 'Adjudicar carga',
    }))) return;
    setBusy(bidId);
    const { data, error } = await actions.backup.award({ bidId });
    if (error) { reportFailure(splitActionError(error)); setBusy(null); await reload(); return; }
    flash('Carga adjudicada a la segunda oferta', 'success');
    window.location.href = `/pyme/envios?id=${data.shipmentId}`;
  }

  return (
    <Card data-testid="backup-bids">
      <CardHeader><CardTitle>Segunda oferta</CardTitle>{opts.deadline && opts.eligible && <span className="text-xs text-ink-500">Disponible porque el envío se canceló dentro de las 24 h</span>}</CardHeader>
      <CardBody className="space-y-4">
        {!opts.eligible && <Alert tone="info">{opts.blockerMessage}</Alert>}
        {opts.eligible && <p className="text-sm text-ink-700">El envío se canceló. Estas ofertas quedaron de respaldo: pide confirmación al transportista y, cuando acepte, adjudícale la carga.</p>}
        <ul className="divide-y divide-steel-200 rounded-card border border-steel-200">
          {items.map((i) => {
            const b = i.bid!;
            return (
              <li key={b.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5 font-medium"><span className="truncate">{b.carrierName}</span><TrustScore score={b.carrierTrustScore} /><Badge tone="neutral">{i.rank === 1 ? 'Siguiente mejor' : `Posición ${(i.rank ?? 1) + 1}`}</Badge></div>
                  <div className="text-xs text-ink-500">{b.vehicleType ? `${VEHICLE_LABELS[b.vehicleType] ?? b.vehicleType} · ` : ''}<span className="font-mono">{b.vehiclePlate}</span> · {formatDateTime(b.createdAt)}</div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="money text-lg">{formatMoney(b.amount)}</span>
                  <Can fallback={null}>
                    {opts.eligible && i.state === 'AVAILABLE' && <Button size="sm" variant="outline" loading={busy === b.id} onClick={() => void ask(b.id!, b.carrierName)}><Handshake />Pedir confirmación</Button>}
                    {opts.eligible && i.state === 'CONFIRMED' && <Button size="sm" variant="money" loading={busy === b.id} onClick={() => void award(b.id!, b.carrierName, b.amount)}><UserCheck />Adjudicar carga</Button>}
                  </Can>
                  {(i.state !== 'AVAILABLE' || !opts.eligible) && i.state !== 'CONFIRMED' && <Badge tone={i.state === 'REQUESTED' ? 'amber' : i.state === 'DECLINED' ? 'red' : 'neutral'}>{STATE_LABEL[i.state!] ?? i.state}</Badge>}
                  {i.state === 'CONFIRMED' && !opts.eligible && <Badge tone="emerald"><Star className="size-3" aria-hidden="true" />Confirmó</Badge>}
                </div>
              </li>
            );
          })}
        </ul>
      </CardBody>
    </Card>
  );
}
