import { useId, useState } from 'react';
import { actions } from 'astro:actions';
import { Landmark, Plus } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox, InlineLabel } from '@/components/ui/form-controls';
import { confirm } from '@/components/ui/confirm-dialog';
import { CopyButton } from '@/components/ui/copy-button';
import { Dialog } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { toast } from '@/components/ui/toaster';
import { EmptyState } from '@/components/domain/EmptyState';
import { Can } from '@/lib/permissions/react';
import { splitActionError, toFormErrors } from '@/lib/action-error';
import type { PayoutAccount } from '../server';
import { PAYOUT_KIND_LABELS } from '../labels';

const KIND_OPTIONS = Object.entries(PAYOUT_KIND_LABELS).map(([value, label]) => ({ value, label }));

/** Datos de cobro del transportista (2.12): lo que ve la PYME para pagarle. Escribe solo el ADMIN; los demás ven los últimos 4 dígitos. */
export default function PayoutAccountsManager({ initial }: { initial: PayoutAccount[] }) {
  const [items, setItems] = useState(initial);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  async function reload() {
    const { data } = await actions.settlements.payoutAccounts({});
    if (data) setItems(data as PayoutAccount[]);
  }
  async function makePrimary(a: PayoutAccount) {
    setBusy(a.id!);
    const { error } = await actions.settlements.primaryPayoutAccount({ id: a.id! });
    setBusy(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Cuenta principal actualizada');
    reload();
  }
  async function archive(a: PayoutAccount) {
    const ok = await confirm({ title: '¿Archivar este dato de cobro?', description: 'Deja de mostrarse a las PYMEs en los cobros nuevos. No se puede deshacer.', confirmLabel: 'Archivar', tone: 'destructive' });
    if (!ok) return;
    setBusy(a.id!);
    const { error } = await actions.settlements.archivePayoutAccount({ id: a.id! });
    setBusy(null);
    if (error) { toast.error(splitActionError(error).message); return; }
    toast.success('Dato de cobro archivado');
    reload();
  }

  return (
    <div className="space-y-4">
      <Alert tone="info" title="Qué ve la PYME">Cuando entregas un envío, la PYME ve estos datos (con el principal primero) para pagarte. Máximo 5 vigentes.</Alert>
      <Card>
        <CardHeader>
          <div><CardTitle>Cuentas y billeteras</CardTitle><CardDescription>CCI de tu banco, Yape o Plin.</CardDescription></div>
          <Can permission="MANAGE_ORGANIZATION" readOnly="Solo el administrador puede cambiar los datos de cobro."><Button variant="money" onClick={() => setAdding(true)}><Plus aria-hidden="true" />Agregar dato de cobro</Button></Can>
        </CardHeader>
        <CardBody>
          {items.length === 0 ? (
            <EmptyState icon={Landmark} title="Aún no cargas datos de cobro" description="Agrega tu CCI, Yape o Plin para que la PYME sepa dónde pagarte." />
          ) : (
            <ul className="space-y-3" aria-label="Datos de cobro">
              {items.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-card border border-steel-200 bg-surface px-4 py-3" data-testid="payout-row">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-ink-900">
                      {a.kind === 'BANK' ? a.bankName : PAYOUT_KIND_LABELS[a.kind ?? '']}
                      {a.primary && <span className="ml-2 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">Principal</span>}
                    </p>
                    <p className="text-xs text-ink-500">{a.kind === 'BANK' ? 'CCI' : 'Celular'} · titular {a.holderName}</p>
                    <p className="mt-0.5 font-mono text-sm tabular-nums text-ink-900">{a.number ?? `•••• ${a.last4}`}</p>
                  </div>
                  <div className="ml-auto flex flex-wrap gap-2">
                    {a.number && <CopyButton text={a.number} label="Copiar" />}
                    <Can permission="MANAGE_ORGANIZATION">
                      {!a.primary && <Button size="sm" variant="outline" loading={busy === a.id} onClick={() => makePrimary(a)}>Marcar como principal</Button>}
                      <Button size="sm" variant="ghost" disabled={busy === a.id} onClick={() => archive(a)}>Archivar<span className="sr-only"> {a.kind === 'BANK' ? a.bankName : PAYOUT_KIND_LABELS[a.kind ?? '']}</span></Button>
                    </Can>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardBody>
      </Card>
      {adding && <AddDialog hasAny={items.length > 0} onClose={() => setAdding(false)} onDone={() => { setAdding(false); reload(); }} />}
    </div>
  );
}

function AddDialog({ hasAny, onClose, onDone }: { hasAny: boolean; onClose: () => void; onDone: () => void }) {
  const uid = useId();
  const [kind, setKind] = useState('BANK');
  const [bankName, setBankName] = useState('');
  const [holder, setHolder] = useState('');
  const [number, setNumber] = useState('');
  const [primary, setPrimary] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const bank = kind === 'BANK';

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const digits = number.replace(/[\s-]/g, '');
    const next: Record<string, string> = {};
    if (bank && !bankName.trim()) next.bankName = 'Ingresa el banco.';
    if (!holder.trim()) next.holderName = 'Ingresa el nombre del titular.';
    if (bank ? !/^\d{20}$/.test(digits) : !/^\d{9}$/.test(digits)) next.number = bank ? 'El CCI tiene 20 dígitos.' : 'El celular tiene 9 dígitos.';
    setErrors(next); setFormError(null);
    if (Object.keys(next).length) return;
    setBusy(true);
    const { error } = await actions.settlements.addPayoutAccount({ kind: kind as 'BANK', bankName: bank ? bankName.trim() : undefined, holderName: holder.trim(), number: digits, primary: primary || undefined });
    setBusy(false);
    if (error) { const r = toFormErrors(error, ['bankName', 'holderName', 'number']); setErrors(r.errors); setFormError(r.formError); return; }
    toast.success('Dato de cobro agregado');
    onDone();
  }

  return (
    <Dialog open onOpenChange={(o) => !o && !busy && onClose()} title="Agregar dato de cobro" description="La PYME lo ve para pagarte. El número se guarda cifrado.">
      <form onSubmit={submit} method="post" noValidate className="space-y-4">
        <Field label="Tipo" htmlFor={`${uid}-k`} required><Select id={`${uid}-k`} value={kind} onValueChange={(v) => { setKind(v); setErrors({}); }} options={KIND_OPTIONS} /></Field>
        {bank && <Field label="Banco" htmlFor={`${uid}-b`} error={errors.bankName} required><Input id={`${uid}-b`} value={bankName} maxLength={80} onChange={(e) => setBankName(e.target.value)} aria-invalid={!!errors.bankName} placeholder="BCP" autoComplete="off" /></Field>}
        <Field label="Titular" htmlFor={`${uid}-h`} error={errors.holderName} required><Input id={`${uid}-h`} value={holder} maxLength={120} onChange={(e) => setHolder(e.target.value)} aria-invalid={!!errors.holderName} placeholder="Transportes Sur Andino SRL" autoComplete="off" /></Field>
        <Field label={bank ? 'CCI' : 'Celular'} htmlFor={`${uid}-n`} error={errors.number} required hint={bank ? '20 dígitos, sin espacios.' : '9 dígitos.'}>
          <Input id={`${uid}-n`} value={number} inputMode="numeric" maxLength={24} onChange={(e) => setNumber(e.target.value)} aria-invalid={!!errors.number} className="font-mono" autoComplete="off" placeholder={bank ? '00219300123456789012' : '987654321'} />
        </Field>
        {hasAny && <div className="flex items-center gap-2"><Checkbox id={`${uid}-p`} checked={primary} onCheckedChange={(c) => setPrimary(c === true)} /><InlineLabel htmlFor={`${uid}-p`}>Usar como principal</InlineLabel></div>}
        {formError && <Alert tone="error">{formError}</Alert>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Volver</Button>
          <Button type="submit" variant="money" loading={busy}>{busy ? 'Agregando…' : 'Agregar dato de cobro'}</Button>
        </div>
      </form>
    </Dialog>
  );
}
