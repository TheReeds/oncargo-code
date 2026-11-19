import { useId, useState } from 'react';
import { actions } from 'astro:actions';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { DatePicker } from '@/components/ui/date-picker';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { FileDropzone } from '@/components/ui/file-dropzone';
import { Input } from '@/components/ui/input';
import { MoneyInput } from '@/components/ui/number-input';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { Select } from '@/components/ui/select';
import { toast } from '@/components/ui/toaster';
import { splitActionError, toFormErrors } from '@/lib/action-error';
import { formatMoney } from '@/lib/format';
import { limaToday } from '@/lib/lima-date';
import type { SettlementView } from '../server';
import { PAYMENT_METHOD_OPTIONS, SETTLEMENT_STATUS_LABELS } from '../labels';
import { uploadProof } from '../upload';

type Done = (s: SettlementView) => void;

/** El transportista registra su comprobante (factura o boleta). Desde aquí el precio final queda fijo. */
export function InvoiceDialog({ shipmentId, finalPrice, onClose, onDone }: { shipmentId: string; finalPrice?: number; onClose: () => void; onDone: Done }) {
  const uid = useId();
  const [number, setNumber] = useState('');
  const [issuedOn, setIssuedOn] = useState(limaToday());
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!number.trim()) next.invoiceNumber = 'Ingresa el número del comprobante.';
    if (!issuedOn) next.issuedOn = 'Elige la fecha de emisión.';
    setErrors(next); setFormError(null);
    if (Object.keys(next).length) return;
    setBusy(true);
    let documentId: string | undefined;
    if (file) {
      const up = await uploadProof(shipmentId, 'CARRIER_INVOICE', file);
      if ('error' in up) { setErrors({ file: up.error }); setBusy(false); return; }
      documentId = up.id;
    }
    const { data, error } = await actions.settlements.invoice({ id: shipmentId, invoiceNumber: number.trim(), issuedOn, documentId });
    setBusy(false);
    if (error) { const r = toFormErrors(error, { invoiceNumber: 'invoiceNumber', issuedOn: 'issuedOn', documentId: 'file' }); setErrors(r.errors); setFormError(r.formError); return; }
    toast.success('Comprobante registrado', 'El precio final ya no cambia y la PYME ve cuánto pagar.');
    onDone(data as SettlementView);
  }

  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} title="Registrar comprobante" description={`Factura o boleta por ${formatMoney(finalPrice)}. Desde aquí el precio final ya no cambia.`}>
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <Field label="Número del comprobante" htmlFor={`${uid}-n`} error={errors.invoiceNumber} required>
          <Input id={`${uid}-n`} value={number} maxLength={40} onChange={(e) => setNumber(e.target.value)} aria-invalid={!!errors.invoiceNumber} placeholder="F001-00231" autoComplete="off" />
        </Field>
        <Field label="Fecha de emisión" htmlFor={`${uid}-d`} error={errors.issuedOn} required>
          <DatePicker id={`${uid}-d`} value={issuedOn} onValueChange={setIssuedOn} max={limaToday()} invalid={!!errors.issuedOn} />
        </Field>
        <Field label="Archivo del comprobante" htmlFor={`${uid}-f`} hint="Opcional. PDF o foto de hasta 10 MB; la PYME lo ve en el envío." error={errors.file}>
          <FileDropzone id={`${uid}-f`} file={file} onFileChange={setFile} disabled={busy} error={errors.file} />
        </Field>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy}>{busy ? 'Registrando…' : 'Registrar comprobante'}</Button>
        </div>
      </form>
    </Dialog>
  );
}

/** La PYME registra el pago hecho fuera de la plataforma, con su constancia. */
export function PaymentDialog({ shipmentId, finalPrice, onClose, onDone }: { shipmentId: string; finalPrice?: number; onClose: () => void; onDone: Done }) {
  const uid = useId();
  const [method, setMethod] = useState('TRANSFER');
  const [operation, setOperation] = useState('');
  const [paidOn, setPaidOn] = useState(limaToday());
  const [amount, setAmount] = useState(finalPrice != null ? String(finalPrice) : '');
  const [file, setFile] = useState<File | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(amount);
    const next: Record<string, string> = {};
    if (method !== 'CASH' && !operation.trim()) next.operationNumber = 'Ingresa el número de operación.';
    if (!paidOn) next.paidOn = 'Elige la fecha del pago.';
    if (!amount || !Number.isFinite(n) || n <= 0) next.amount = 'Ingresa el monto pagado.';
    setErrors(next); setFormError(null);
    if (Object.keys(next).length) return;
    setBusy(true);
    let documentId: string | undefined;
    if (file) {
      const up = await uploadProof(shipmentId, 'PAYMENT_PROOF', file);
      if ('error' in up) { setErrors({ file: up.error }); setBusy(false); return; }
      documentId = up.id;
    }
    const { data, error } = await actions.settlements.payment({ id: shipmentId, method: method as 'TRANSFER', operationNumber: operation.trim() || undefined, paidOn, amount: n, documentId });
    setBusy(false);
    if (error) { const r = toFormErrors(error, { operationNumber: 'operationNumber', paidOn: 'paidOn', amount: 'amount', documentId: 'file' }); setErrors(r.errors); setFormError(r.formError); return; }
    toast.success('Pago registrado', 'El transportista lo confirma cuando lo recibe.');
    onDone(data as SettlementView);
  }

  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} title="Registrar pago" description="Registra el pago que hiciste fuera de OnCargo. El transportista lo confirma al recibirlo.">
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <Field label="Forma de pago" htmlFor={`${uid}-m`} required>
          <Select id={`${uid}-m`} value={method} onValueChange={(v) => { setMethod(v); setErrors((x) => ({ ...x, operationNumber: '' })); }} options={PAYMENT_METHOD_OPTIONS} />
        </Field>
        <Field label="Número de operación" htmlFor={`${uid}-o`} error={errors.operationNumber} required={method !== 'CASH'} hint={method === 'CASH' ? 'En efectivo no hace falta.' : undefined}>
          <Input id={`${uid}-o`} value={operation} maxLength={40} onChange={(e) => setOperation(e.target.value)} aria-invalid={!!errors.operationNumber} placeholder="00458123" autoComplete="off" />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Fecha del pago" htmlFor={`${uid}-d`} error={errors.paidOn} required>
            <DatePicker id={`${uid}-d`} value={paidOn} onValueChange={setPaidOn} max={limaToday()} invalid={!!errors.paidOn} />
          </Field>
          <Field label="Monto pagado" htmlFor={`${uid}-a`} error={errors.amount} required>
            <MoneyInput id={`${uid}-a`} value={amount} onValueChange={setAmount} invalid={!!errors.amount} />
          </Field>
        </div>
        <Field label="Constancia de pago" htmlFor={`${uid}-f`} hint="Opcional. Captura o PDF de hasta 10 MB." error={errors.file}>
          <FileDropzone id={`${uid}-f`} file={file} onFileChange={setFile} disabled={busy} error={errors.file} />
        </Field>
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy}>{busy ? 'Registrando…' : 'Registrar pago'}</Button>
        </div>
      </form>
    </Dialog>
  );
}

/** Disputa del cobro (facturado o pagado, sin confirmar). */
export function DisputeDialog({ shipmentId, open, onOpenChange, onDone }: { shipmentId: string; open: boolean; onOpenChange: (o: boolean) => void; onDone: Done }) {
  return (
    <ReasonDialog open={open} onOpenChange={onOpenChange} title="Disputar el cobro" required tone="destructive" maxLength={500} confirmLabel="Abrir disputa"
      description="El equipo de OnCargo revisa el caso y lo resuelve. Cuenta qué no cuadra: monto, pago no recibido o comprobante."
      placeholder="El monto facturado no coincide con lo acordado"
      onSubmit={async (reason) => {
        const { data, error } = await actions.settlements.dispute({ id: shipmentId, reason });
        if (error) return splitActionError(error);
        toast.success('Disputa abierta', 'OnCargo revisará el caso.');
        onDone(data as SettlementView);
        return null;
      }} />
  );
}

/** El operador cierra la disputa: elige el estado en que queda el cobro y deja una nota. */
export function ResolveDialog({ shipmentId, previous, open, onOpenChange, onDone }: { shipmentId: string; previous?: string; open: boolean; onOpenChange: (o: boolean) => void; onDone: Done }) {
  const uid = useId();
  const options = ['PENDING_INVOICE', 'INVOICED', 'PAID', 'CONFIRMED'].map((v) => ({ value: v, label: SETTLEMENT_STATUS_LABELS[v] }));
  const [status, setStatus] = useState(previous && previous !== 'DISPUTED' ? previous : 'INVOICED');
  return (
    <ReasonDialog open={open} onOpenChange={onOpenChange} title="Resolver la disputa" required label="Nota de la resolución" maxLength={500} reasonField="note" confirmLabel="Resolver disputa"
      description="Elige en qué estado queda el cobro. Ambas partes verán tu nota."
      onSubmit={async (note) => {
        const { data, error } = await actions.settlements.resolve({ id: shipmentId, status: status as 'INVOICED', note });
        if (error) return splitActionError(error);
        toast.success('Disputa resuelta');
        onDone(data as SettlementView);
        return null;
      }}>
      <Field label="El cobro queda como" htmlFor={`${uid}-s`}>
        <Select id={`${uid}-s`} value={status} onValueChange={setStatus} options={options} />
      </Field>
    </ReasonDialog>
  );
}
