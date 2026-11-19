import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Link2, MessageCircle, Share2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Alert } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { CopyButton } from '@/components/ui/copy-button';
import { SkeletonRows } from '@/components/ui/skeleton';
import { confirm } from '@/components/ui/confirm-dialog';
import { toast } from '@/components/ui/toaster';
import { Can, useCan } from '@/lib/permissions/react';
import { formatDateTime } from '@/lib/format';
import type { ShipmentView } from '@/lib/api/server';
import type { ShareLinkView } from '../server';

const EXPIRY_OPTIONS = [
  { value: 'auto', label: 'Hasta 7 días después de la entrega' },
  { value: '1', label: '1 día' },
  { value: '7', label: '7 días' },
  { value: '30', label: '30 días' },
];
const STATUS: Record<string, { label: string; tone: 'emerald' | 'neutral' | 'red' }> = {
  ACTIVE: { label: 'Activo', tone: 'emerald' },
  EXPIRED: { label: 'Vencido', tone: 'neutral' },
  REVOKED: { label: 'Revocado', tone: 'red' },
};

/** Texto para el cliente final (WhatsApp): sin precio ni datos internos. */
export function shareMessage(url: string, s: Pick<ShipmentView, 'code'>) {
  return `Sigue tu envío${s.code ? ` ${s.code}` : ''} en tiempo real: ${url}`;
}

/**
 * «Compartir seguimiento» (roadmap 1.6, ADR-0011): la PYME crea un enlace público para su cliente final, lo copia o lo
 * manda por WhatsApp, y lo revoca cuando quiera. El enlace se ve **una sola vez** (en la base de datos solo queda su
 * hash). El Lector ve la lista de enlaces y sus visitas, pero no crea ni revoca.
 */
export function ShareTrackingButton({ shipment }: { shipment: ShipmentView }) {
  const [open, setOpen] = useState(false);
  const closed = shipment.status === 'CANCELLED';
  if (closed) return null;
  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}><Share2 aria-hidden="true" />Compartir seguimiento</Button>
      {open && <ShareTrackingDialog shipment={shipment} open={open} onOpenChange={setOpen} />}
    </>
  );
}

function ShareTrackingDialog({ shipment, open, onOpenChange }: { shipment: ShipmentView; open: boolean; onOpenChange: (o: boolean) => void }) {
  const canOperate = useCan('OPERATE');
  const [links, setLinks] = useState<ShareLinkView[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [expiry, setExpiry] = useState('auto');
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState<{ url: string; hint: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const { data, error } = await actions.shipments.shareLinks({ id: shipment.id! });
    if (error) { setLoadError(error.message); return; }
    setLoadError(null);
    setLinks(data ?? []);
  }, [shipment.id]);
  useEffect(() => { void load(); }, [load]);

  async function create() {
    setBusy(true);
    setError(null);
    const { data, error } = await actions.shipments.createShareLink({ id: shipment.id!, expiresInDays: expiry === 'auto' ? undefined : Number(expiry) });
    setBusy(false);
    if (error) { setError(error.message); return; }
    setCreated({ url: data!.url!, hint: data!.link?.tokenHint ?? '' });
    toast.success('Enlace de seguimiento creado');
    void load();
  }

  async function revoke(l: ShareLinkView) {
    const ok = await confirm({
      title: `¿Revocar el enlace …${l.tokenHint}?`,
      description: 'Quien lo tenga dejará de ver el seguimiento de inmediato. No se puede deshacer: si lo necesitas, crea otro.',
      confirmLabel: 'Revocar enlace',
      tone: 'destructive',
    });
    if (!ok) return;
    const { error } = await actions.shipments.revokeShareLink({ id: shipment.id!, linkId: l.id! });
    if (error) { toast.error(error.message); return; }
    if (created?.hint === l.tokenHint) setCreated(null);
    toast.success('Enlace revocado');
    void load();
  }

  const whatsapp = created ? `https://wa.me/?text=${encodeURIComponent(shareMessage(created.url, shipment))}` : '';

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="Compartir seguimiento"
      description="Tu cliente verá el estado, las ciudades, los hitos con hora, una llegada estimada y la ruta. Nunca el precio, los contactos, la placa ni los datos de las empresas."
      footer={<Button type="button" variant="outline" onClick={() => onOpenChange(false)}>Volver</Button>}
    >
      <div className="space-y-5">
        {created ? (
          <div className="space-y-3">
            <Alert tone="warning" title="Copia el enlace ahora">Por seguridad no lo volveremos a mostrar. Si lo pierdes, crea otro.</Alert>
            <Field label="Enlace de seguimiento" htmlFor="share-url">
              <Input id="share-url" readOnly value={created.url} onFocus={(e) => e.currentTarget.select()} className="font-mono text-xs" />
            </Field>
            <div className="flex flex-wrap items-center gap-2">
              <CopyButton text={created.url} label="Copiar enlace" />
              <Button asChild variant="outline" size="sm"><a href={whatsapp} target="_blank" rel="noopener noreferrer"><MessageCircle aria-hidden="true" />Enviar por WhatsApp</a></Button>
            </div>
          </div>
        ) : (
          <Can readOnly="Tu rol solo permite ver los enlaces: pide a un administrador u operador que cree uno.">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <div className="flex-1">
                <Field label="Vence" htmlFor="share-expiry" hint="Siempre deja de funcionar 7 días después de la entrega.">
                  <Select id="share-expiry" value={expiry} onValueChange={setExpiry} options={EXPIRY_OPTIONS} />
                </Field>
              </div>
              <Button type="button" className="sm:mb-[1.375rem]" loading={busy} onClick={create}><Link2 aria-hidden="true" />Crear enlace</Button>
            </div>
            {error && <Alert tone="error" className="mt-3">{error}</Alert>}
          </Can>
        )}

        <section aria-labelledby="share-links-title" className="space-y-2">
          <h3 id="share-links-title" className="text-sm font-semibold text-ink-900">Enlaces creados</h3>
          {loadError && <Alert tone="error">{loadError}</Alert>}
          {!links && !loadError && <SkeletonRows rows={2} />}
          {links && links.length === 0 && <p className="text-sm text-ink-500">Aún no compartiste este envío.</p>}
          {links && links.length > 0 && (
            <ul className="divide-y divide-steel-200 rounded-input border border-steel-200">
              {links.map((l) => {
                const st = STATUS[l.status ?? ''] ?? STATUS.EXPIRED;
                return (
                  <li key={l.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5 text-sm">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2"><span className="font-mono text-xs text-ink-700">…{l.tokenHint}</span><Badge tone={st.tone}>{st.label}</Badge></div>
                      <div className="mt-0.5 text-xs text-ink-500">
                        Creado {formatDateTime(l.createdAt)}
                        {l.status === 'ACTIVE' && l.expiresAt && <> · vence {formatDateTime(l.expiresAt)}</>}
                        {l.status === 'REVOKED' && l.revokedAt && <> · revocado {formatDateTime(l.revokedAt)}</>}
                        {' · '}{l.views === 1 ? '1 visita' : `${l.views ?? 0} visitas`}
                        {l.lastViewedAt && <> (última {formatDateTime(l.lastViewedAt)})</>}
                      </div>
                    </div>
                    {canOperate && l.status === 'ACTIVE' && (
                      <Button type="button" variant="destructive" size="sm" onClick={() => revoke(l)} aria-label={`Revocar el enlace …${l.tokenHint}`}>Revocar</Button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>
    </Dialog>
  );
}
