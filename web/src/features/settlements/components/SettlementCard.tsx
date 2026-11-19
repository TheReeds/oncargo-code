import { useCallback, useEffect, useState } from 'react';
import { actions } from 'astro:actions';
import { Banknote, FileText, Landmark, Lock } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { confirm } from '@/components/ui/confirm-dialog';
import { CopyButton } from '@/components/ui/copy-button';
import { SkeletonRows } from '@/components/ui/skeleton';
import { toast } from '@/components/ui/toaster';
import { StatusPill } from '@/components/domain/StatusPill';
import { Can } from '@/lib/permissions/react';
import { splitActionError } from '@/lib/action-error';
import { formatDate, formatDateTime, formatMoney } from '@/lib/format';
import type { PayoutAccount, SettlementView } from '../server';
import { PAYOUT_KIND_LABELS, SETTLEMENT_STATUS_LABELS } from '../labels';
import { DisputeDialog, InvoiceDialog, PaymentDialog, ResolveDialog } from './SettlementDialogs';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';

/** Datos de cobro del transportista para que la PYME pague: banco + CCI o Yape/Plin, con botón para copiar. */
export function PayoutAccountsList({ accounts }: { accounts: PayoutAccount[] }) {
  if (!accounts.length) return <p className="text-sm text-ink-500">El transportista aún no cargó sus datos de cobro. Pídeselos por el chat o por correo.</p>;
  return (
    <ul className="space-y-2" aria-label="Datos de cobro del transportista">
      {accounts.map((a) => {
        const full = a.number ?? '';
        return (
          <li key={a.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-card border border-steel-200 bg-surface px-4 py-3 text-sm" data-testid="payout-account">
            <div className="min-w-0">
              <p className="font-medium text-ink-900">
                {a.kind === 'BANK' ? a.bankName : PAYOUT_KIND_LABELS[a.kind ?? '']}
                {a.primary && <span className="ml-2 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">Principal</span>}
              </p>
              <p className="text-xs text-ink-500">{a.kind === 'BANK' ? 'CCI' : 'Celular'} · titular {a.holderName}</p>
              <p className="mt-0.5 font-mono text-sm tabular-nums text-ink-900">{full || `•••• ${a.last4}`}</p>
            </div>
            {full && <div className="ml-auto"><CopyButton text={full} label={a.kind === 'BANK' ? 'Copiar CCI' : 'Copiar número'} /></div>}
          </li>
        );
      })}
    </ul>
  );
}

const Row = ({ label, children, strong }: { label: string; children: React.ReactNode; strong?: boolean }) => (
  <>
    <dt className={strong ? 'font-medium text-ink-900' : 'text-ink-700'}>{label}</dt>
    <dd className={`text-right font-mono tabular-nums ${strong ? 'font-semibold text-ink-900' : 'text-ink-900'}`}>{children}</dd>
  </>
);

const docLink = (id?: string | null, label = 'Ver archivo') => id ? <a href={`/documentos/${id}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-petrol-700 hover:underline"><FileText className="size-3.5" aria-hidden="true" />{label}</a> : null;

/**
 * Tarjeta «Cobro» del detalle de un envío entregado (2.12): estado, vencimiento, precio final con IGV, comprobante, pago,
 * disputa, historial y las acciones que `SettlementView.actions` permite. La PYME nunca ve la comisión (ADR-0010).
 * No pinta nada si el envío no está entregado (`LOGYX-STL-001`).
 */
export default function SettlementCard({ shipmentId, perspective, shipmentStatus, initial }: { shipmentId: string; perspective: Perspective; shipmentStatus?: string; initial?: SettlementView | null }) {
  const [view, setView] = useState<SettlementView | null | undefined>(initial);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<'invoice' | 'pay' | 'dispute' | 'resolve' | null>(null);
  const [busy, setBusy] = useState(false);
  const delivered = shipmentStatus === 'DELIVERED' || shipmentStatus === undefined;

  const load = useCallback(async () => {
    const { data, error: err } = await actions.settlements.get({ id: shipmentId });
    if (err) { setError(splitActionError(err).message); return; }
    setError(null);
    setView(data as SettlementView | null);
  }, [shipmentId]);
  useEffect(() => { if (delivered && initial === undefined) load(); }, [delivered, initial, load]);
  // Un envío que acaba de entregarse (en vivo) abre su cobro sin recargar la página.
  useEffect(() => { if (delivered && view === null) load(); }, [delivered]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!delivered) return null;
  if (view === null) return null;
  if (view === undefined && !error) return <Card data-testid="settlement-card"><CardBody><SkeletonRows rows={3} /></CardBody></Card>;
  if (error || !view) return <Alert tone="error" title="No pudimos cargar el cobro" action={<Button size="sm" variant="outline" onClick={load}>Reintentar</Button>}>{error}</Alert>;

  const v = view;
  const acts = new Set(v.actions ?? []);
  const showCommission = perspective !== 'PYME' && v.commission != null;
  const overdue = (v.daysOverdue ?? 0) > 0;
  const done = (s: SettlementView) => setView(s);

  async function confirmCollected() {
    const ok = await confirm({ title: '¿Confirmar que recibiste el pago?', description: `Se marca como cobrado el pago de ${formatMoney(v.payment?.amount ?? v.finalPrice)}. Después ya no se puede disputar.`, confirmLabel: 'Confirmar cobro' });
    if (!ok) return;
    setBusy(true);
    const { data, error: err } = await actions.settlements.confirm({ id: shipmentId });
    setBusy(false);
    if (err) { toast.error(splitActionError(err).message); return; }
    toast.success('Cobro confirmado');
    setView(data as SettlementView);
  }
  async function withdraw() {
    const ok = await confirm({ title: '¿Retirar la disputa?', description: 'El cobro vuelve al estado en que estaba antes de la disputa.', confirmLabel: 'Retirar disputa' });
    if (!ok) return;
    setBusy(true);
    const { data, error: err } = await actions.settlements.resolve({ id: shipmentId });
    setBusy(false);
    if (err) { toast.error(splitActionError(err).message); return; }
    toast.success('Disputa retirada');
    setView(data as SettlementView);
  }

  const hasActions = ['INVOICE', 'PAY', 'CONFIRM', 'DISPUTE', 'WITHDRAW_DISPUTE', 'RESOLVE'].some((a) => acts.has(a));
  return (
    <Card data-testid="settlement-card">
      <CardHeader>
        <div>
          <CardTitle className="flex flex-wrap items-center gap-2"><Banknote className="size-4 text-ink-500" aria-hidden="true" />Cobro <StatusPill status={v.status!} kind="settlement" /></CardTitle>
          <CardDescription>
            {v.dueOn ? <>Vence el {formatDate(v.dueOn)}</> : 'Sin fecha de vencimiento'}
            {overdue && <span className="ml-2 font-medium text-red-700" data-testid="overdue">Vencido hace {v.daysOverdue} {v.daysOverdue === 1 ? 'día' : 'días'}</span>}
          </CardDescription>
        </div>
      </CardHeader>
      <CardBody className="space-y-4">
        {!!v.pendingAdjustments && <Alert tone="warning" title="Hay ajustes por responder">{v.pendingAdjustments === 1 ? 'Un ajuste espera respuesta' : `${v.pendingAdjustments} ajustes esperan respuesta`}. Hasta resolverlos no se puede facturar ni registrar el pago.</Alert>}
        {v.status === 'DISPUTED' && v.dispute && (
          <Alert tone="error" title="Cobro en disputa">
            <p>{v.dispute.reason}</p>
            <p className="mt-1 text-xs">Abierta el {formatDateTime(v.dispute.openedAt)}{v.dispute.openedBy ? ` por ${v.dispute.openedBy}` : ''}. OnCargo la resuelve.</p>
          </Alert>
        )}

        <dl className="grid grid-cols-[1fr_auto] gap-x-6 gap-y-1.5 text-sm" data-testid="settlement-amounts">
          <Row label="Precio acordado">{formatMoney(v.agreedPrice)}</Row>
          {!!v.adjustmentsTotal && <Row label="Ajustes aceptados">{formatMoney(v.adjustmentsTotal)}</Row>}
          <Row label="Valor de venta (sin IGV)">{formatMoney(v.netOfVat)}</Row>
          <Row label="IGV">{formatMoney(v.vat)}</Row>
          <Row label="Precio final" strong>{formatMoney(v.finalPrice)}</Row>
          {showCommission && <>
            <Row label={`Comisión de OnCargo${v.commissionRate != null ? ` (${(v.commissionRate * 100).toFixed(1)} %)` : ''}`}>− {formatMoney(v.commission)}</Row>
            <Row label="IGV de la comisión">− {formatMoney(v.commissionVat)}</Row>
            <Row label={perspective === 'CARRIER' ? 'Cobras neto' : 'Neto del transportista'} strong>{formatMoney(v.carrierNet)}</Row>
          </>}
        </dl>
        {v.adjustmentsLocked && <p className="flex items-center gap-1.5 text-xs text-ink-500"><Lock className="size-3.5" aria-hidden="true" />Desde la factura el precio final ya no cambia.</p>}
        {showCommission && v.commissionStatus && (
          <p className="flex items-center gap-2 text-xs text-ink-500">Comisión: <StatusPill status={v.commissionStatus} kind="commission" /></p>
        )}

        {(v.invoice || v.payment) && (
          <div className="grid gap-3 sm:grid-cols-2">
            {v.invoice && (
              <div className="rounded-card border border-steel-200 p-3 text-sm" data-testid="settlement-invoice">
                <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Comprobante</p>
                <p className="mt-1 font-mono font-medium text-ink-900">{v.invoice.number}</p>
                <p className="text-xs text-ink-500">Emitido el {formatDate(v.invoice.issuedOn)}</p>
                <p className="mt-1">{docLink(v.invoice.documentId)}</p>
              </div>
            )}
            {v.payment && (
              <div className="rounded-card border border-steel-200 p-3 text-sm" data-testid="settlement-payment">
                <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Pago</p>
                <p className="mt-1 font-medium text-ink-900">{v.payment.methodLabel} · <span className="money">{formatMoney(v.payment.amount)}</span></p>
                <p className="text-xs text-ink-500">{v.payment.operationNumber ? <>Op. <span className="font-mono">{v.payment.operationNumber}</span> · </> : null}{formatDate(v.payment.paidOn)}</p>
                <p className="mt-1">{docLink(v.payment.documentId, 'Ver constancia')}</p>
              </div>
            )}
          </div>
        )}
        {v.status === 'CONFIRMED' && v.confirmedAt && <p className="text-xs text-ink-500">Cobro confirmado el {formatDateTime(v.confirmedAt)}.</p>}

        {perspective === 'PYME' && v.status !== 'CONFIRMED' && (
          <section aria-labelledby={`payout-${shipmentId}`} className="space-y-2">
            <h3 id={`payout-${shipmentId}`} className="flex items-center gap-1.5 text-[13px] font-semibold text-ink-900"><Landmark className="size-4 text-ink-500" aria-hidden="true" />Dónde pagar</h3>
            <PayoutAccountsList accounts={v.payoutAccounts ?? []} />
          </section>
        )}

        {hasActions && (
          <Can readOnly="No puedes registrar ni confirmar cobros.">
            <div className="flex flex-wrap gap-2">
              {acts.has('INVOICE') && <Button variant="money" onClick={() => setDialog('invoice')}>Registrar comprobante</Button>}
              {acts.has('PAY') && <Button variant="money" onClick={() => setDialog('pay')}>Registrar pago</Button>}
              {acts.has('CONFIRM') && <Button variant="money" loading={busy} onClick={confirmCollected}>Confirmar cobro</Button>}
              {acts.has('RESOLVE') && <Button variant="default" onClick={() => setDialog('resolve')}>Resolver disputa</Button>}
              {acts.has('WITHDRAW_DISPUTE') && <Button variant="outline" loading={busy} onClick={withdraw}>Retirar disputa</Button>}
              {acts.has('DISPUTE') && <Button variant="destructive" onClick={() => setDialog('dispute')}>Disputar cobro</Button>}
            </div>
          </Can>
        )}

        {!!v.history?.length && (
          <details className="group text-sm" data-testid="settlement-history">
            <summary className="cursor-pointer list-none font-medium text-petrol-700 hover:underline [&::-webkit-details-marker]:hidden">Historial ({v.history.length})</summary>
            <ol className="mt-2 space-y-2 border-l border-steel-200 pl-4">
              {v.history.map((h, i) => (
                <li key={i} className="text-xs text-ink-700">
                  <span className="font-medium text-ink-900">{h.toLabel ?? SETTLEMENT_STATUS_LABELS[h.to ?? ''] ?? h.to}</span> · {h.actorName ?? 'OnCargo'} · {formatDateTime(h.at)}
                  {h.note && <span className="block text-ink-500">{h.note}</span>}
                </li>
              ))}
            </ol>
          </details>
        )}
      </CardBody>

      {dialog === 'invoice' && <InvoiceDialog shipmentId={shipmentId} finalPrice={v.finalPrice} onClose={() => setDialog(null)} onDone={(s) => { setDialog(null); done(s); }} />}
      {dialog === 'pay' && <PaymentDialog shipmentId={shipmentId} finalPrice={v.finalPrice} onClose={() => setDialog(null)} onDone={(s) => { setDialog(null); done(s); }} />}
      <DisputeDialog shipmentId={shipmentId} open={dialog === 'dispute'} onOpenChange={(o) => !o && setDialog(null)} onDone={done} />
      <ResolveDialog shipmentId={shipmentId} previous={v.dispute?.previousStatus} open={dialog === 'resolve'} onOpenChange={(o) => !o && setDialog(null)} onDone={done} />
    </Card>
  );
}
