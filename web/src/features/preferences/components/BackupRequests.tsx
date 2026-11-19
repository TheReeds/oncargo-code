import { useState } from 'react';
import { actions } from 'astro:actions';
import { Handshake } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { Can } from '@/lib/permissions/react';
import { useLiveRefresh } from '@/lib/realtime';
import { CITY_LABELS, formatDate, formatDateTime, formatMoney, formatTons } from '@/lib/format';
import type { BackupRequestView } from '../server';

const STATE: Record<string, { label: string; tone: 'amber' | 'emerald' | 'neutral' }> = {
  REQUESTED: { label: 'Por responder', tone: 'amber' }, CONFIRMED: { label: 'Confirmada', tone: 'emerald' },
  DECLINED: { label: 'Declinada', tone: 'neutral' }, USED: { label: 'Adjudicada', tone: 'emerald' },
};

/**
 * Confirmaciones (roadmap 2.9): una PYME perdió a su transportista y te pide confirmar que puedes llevar su carga con tu
 * oferta. Si confirmas, ella decide si te la adjudica. Solo ves tu propia oferta (RN04).
 */
export default function BackupRequests({ initial }: { initial: BackupRequestView[] }) {
  const [items, setItems] = useState(initial);
  const [busy, setBusy] = useState<string | null>(null);

  async function reload() {
    const { data } = await actions.backup.mine();
    if (data) setItems(data);
  }
  useLiveRefresh({ types: ['notification.created'], refresh: reload, fallbackMs: 60_000 });

  async function respond(r: BackupRequestView, accept: boolean) {
    const route = `${CITY_LABELS[r.originCity!]} → ${CITY_LABELS[r.destinationCity!]}`;
    if (!(await confirm({
      title: accept ? '¿Confirmar que llevas esta carga?' : '¿Declinar esta carga?',
      description: accept
        ? `${r.requesterName} podrá adjudicarte ${route} (${r.requestCode}) por ${formatMoney(r.amount)}, con tu oferta y tu vehículo ${r.vehiclePlate}. Confirma solo si el vehículo está libre el ${formatDate(r.pickupDate)}.`
        : `${r.requesterName} dejará de contar contigo para ${route}. No se puede deshacer.`,
      confirmLabel: accept ? 'Confirmar carga' : 'Declinar carga', tone: accept ? 'default' : 'destructive',
    }))) return;
    setBusy(r.bidId!);
    const { error } = accept ? await actions.backup.confirm({ bidId: r.bidId! }) : await actions.backup.decline({ bidId: r.bidId! });
    setBusy(null);
    if (error) { toast.error('No pudimos enviar tu respuesta', error.message); await reload(); return; }
    toast.success(accept ? 'Confirmaste la carga' : 'Declinaste la carga', accept ? 'La PYME decide si te la adjudica.' : undefined);
    await reload();
  }

  if (items.length === 0)
    return <Card><EmptyState icon={Handshake} title="No tienes confirmaciones pendientes" description="Cuando una PYME quiera adjudicarte una carga como segunda opción, te avisamos y la verás aquí." /></Card>;

  return (
    <div className="space-y-3">
      <ReadOnlyNotice>No puedes confirmar ni declinar cargas.</ReadOnlyNotice>
      <ul className="space-y-3">
        {items.map((r) => {
          const st = STATE[r.state ?? ''] ?? { label: r.state ?? '', tone: 'neutral' as const };
          return (
            <li key={r.bidId}>
              <Card className="flex flex-wrap items-center justify-between gap-4 p-4">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2"><span className="font-medium">{CITY_LABELS[r.originCity!]} → {CITY_LABELS[r.destinationCity!]}</span><span className="font-mono text-xs font-semibold text-ink-700">{r.requestCode}</span><Badge tone={st.tone}>{st.label}</Badge></div>
                  <p className="text-sm text-ink-700">{r.requesterName} · {formatTons(r.weightKg)} · Recojo {formatDate(r.pickupDate)}</p>
                  <p className="text-xs text-ink-500">Tu oferta <span className="money">{formatMoney(r.amount)}</span> con <span className="font-mono">{r.vehiclePlate}</span> · Pedido {formatDateTime(r.requestedAt)}</p>
                </div>
                {r.state === 'REQUESTED' && (
                  <Can fallback={null}>
                    <div className="flex gap-2">
                      <Button variant="outline" disabled={busy === r.bidId} onClick={() => void respond(r, false)}>Declinar carga</Button>
                      <Button variant="money" loading={busy === r.bidId} onClick={() => void respond(r, true)}>Confirmar carga</Button>
                    </div>
                  </Can>
                )}
              </Card>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
