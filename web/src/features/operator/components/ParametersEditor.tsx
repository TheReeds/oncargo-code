import { useState } from 'react';
import { actions } from 'astro:actions';
import { FileWarning, Gauge, Pencil, Percent, Timer, TriangleAlert } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { MoneyInput, NumberInput } from '@/components/ui/number-input';
import { Select } from '@/components/ui/select';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { flash } from '@/components/ui/toaster';
import { splitActionError } from '@/lib/action-error';
import { formatRelative } from '@/lib/format';
import { LIMITS } from '@/lib/limits';
import { formatParam, percentToRate, toInput } from '../parameters';
import type { ParameterRow } from '../server';

const ICON: Record<string, typeof Gauge> = { commission_rate: Percent, assignment_sla_hours: Timer, incident_warn_hours: TriangleAlert, incident_breach_hours: TriangleAlert, fleet_docs_block: FileWarning };

/**
 * Parámetros de sistema editables (V18). Una tarjeta por parámetro: valor vigente, de dónde viene y "Cambiar" con motivo
 * obligatorio. La comisión se edita en % (8 → 0.08 para la API); las horas, enteras. El cambio se aplica al instante.
 */
export default function ParametersEditor({ rows, now }: { rows: ParameterRow[]; now: number }) {
  const [editing, setEditing] = useState<ParameterRow | null>(null);
  const [value, setValue] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const isRate = editing?.kind === 'RATE';
  const isFlag = editing?.kind === 'FLAG';
  const isAmount = editing?.kind === 'AMOUNT';

  function open(r: ParameterRow) {
    setEditing(r);
    setValue(toInput(r));
    setFieldError(null);
  }

  function validValue(): boolean {
    if (!editing) return false;
    if (isFlag) { if (value !== '0' && value !== '1') { setFieldError('Elige Activado o Desactivado.'); return false; } setFieldError(null); return true; }
    const n = Number(value);
    const min = Number(editing.min), max = Number(editing.max);
    const lo = isRate ? min * 100 : min, hi = isRate ? max * 100 : max;
    if (!value || Number.isNaN(n)) { setFieldError('Indica el valor.'); return false; }
    if (isAmount ? !/^\d+(\.\d{1,2})?$/.test(value) : isRate ? percentToRate(value) === null : !/^\d+$/.test(value)) { setFieldError(isAmount ? 'Usa soles con hasta 2 decimales.' : isRate ? 'Usa un porcentaje con hasta 2 decimales, como 8 o 8.25.' : 'Indica horas enteras.'); return false; }
    if (n < lo || n > hi) { setFieldError(isAmount ? `Debe estar entre S/ ${lo} y S/ ${hi}.` : isRate ? `Debe estar entre ${lo} % y ${hi} %.` : `Debe estar entre ${lo} y ${hi} horas.`); return false; }
    setFieldError(null);
    return true;
  }

  async function submit(reason: string) {
    if (!editing) return null;
    const { error } = await actions.operator.updateParameter({
      key: editing.key!, value: isRate ? percentToRate(value)! : value, reason, expectedVersion: editing.version,
    });
    if (error) return splitActionError(error);
    flash('Parámetro actualizado', 'success');
    window.location.reload();
    return null;
  }

  return (
    <>
      <ul className="grid gap-4 md:grid-cols-2" aria-label="Parámetros del sistema">
        {rows.map((r) => {
          const Icon = ICON[r.key!] ?? Gauge;
          return (
            <li key={r.key}>
              <Card className="h-full" data-testid={`param-${r.key}`}>
                <CardBody className="flex h-full flex-col gap-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5">
                      <span className="grid size-9 shrink-0 place-items-center rounded-input bg-petrol-50 text-petrol-700"><Icon className="size-[18px]" aria-hidden="true" /></span>
                      <h2 className="text-[15px] font-semibold text-ink-900">{r.label}</h2>
                    </div>
                    <Badge tone={r.overridden ? 'petrol' : 'neutral'}>{r.overridden ? `Versión ${r.version}` : 'Valor por omisión'}</Badge>
                  </div>
                  <p className="font-mono text-3xl font-semibold tabular-nums text-ink-900" data-testid="param-value">{formatParam(r.kind, r.value)}</p>
                  <p className="text-sm text-ink-700">{r.description}</p>
                  <p className="text-xs text-ink-500">
                    {r.overridden
                      ? <>Cambiado {formatRelative(r.updatedAt, now)}. Motivo: {r.reason}</>
                      : <>Nadie lo cambió: rige el valor de la configuración ({formatParam(r.kind, r.defaultValue)}).</>}
                  </p>
                  <div className="mt-auto pt-1">
                    <Button variant="outline" size="sm" onClick={() => open(r)}><Pencil aria-hidden="true" /> Cambiar<span className="sr-only"> {r.label}</span></Button>
                  </div>
                </CardBody>
              </Card>
            </li>
          );
        })}
      </ul>

      <ReasonDialog
        open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)}
        title={editing ? `Cambiar: ${editing.label}` : 'Cambiar parámetro'}
        description={editing?.key === 'commission_rate'
          ? 'Rige para las adjudicaciones desde ahora. Los envíos ya creados conservan la tasa con la que se adjudicaron.'
          : 'El cambio se aplica de inmediato y queda en el historial y en la auditoría.'}
        required minLength={3} maxLength={LIMITS.parameterReason}
        label="Motivo del cambio" hint="Obligatorio. Queda en el historial junto con tu nombre y la fecha."
        placeholder="Ej. Acuerdo comercial del piloto, aprobado por el dueño del producto."
        confirmLabel="Guardar cambio"
        canSubmit={validValue}
        onFailure={(f) => { if (f.field === 'value') { setFieldError(f.message); return true; } return false; }}
        onSubmit={submit}
      >
        {editing && (
          isFlag ? (
            <Field label="Nuevo valor" htmlFor="param-value-input" error={fieldError} hint="Activado: un documento de flota vencido bloquea la oferta. Desactivado: solo avisa al transportista.">
              <Select id="param-value-input" value={value} onValueChange={(v) => { setValue(v); if (fieldError) setFieldError(null); }} invalid={!!fieldError}
                options={[{ value: '1', label: 'Activado' }, { value: '0', label: 'Desactivado' }]} />
            </Field>
          ) : (
          isAmount ? (
            <Field label="Nuevo monto" htmlFor="param-value-input" error={fieldError} hint={`Soles con IGV, entre S/ ${editing.min} y S/ ${editing.max}.`}>
              <MoneyInput id="param-value-input" value={value} onValueChange={(v) => { setValue(v); if (fieldError) setFieldError(null); }} invalid={!!fieldError} />
            </Field>
          ) : (
          <Field label={isRate ? 'Nueva comisión' : 'Nuevo valor'} htmlFor="param-value-input" error={fieldError} hint={isRate ? `Porcentaje del precio adjudicado, entre ${Number(editing.min) * 100} % y ${Number(editing.max) * 100} %.` : `Horas enteras, entre ${editing.min} y ${editing.max}.`}>
            <NumberInput id="param-value-input" value={value} onValueChange={(v) => { setValue(v); if (fieldError) setFieldError(null); }} decimals={isRate ? 2 : 0} suffix={isRate ? '%' : 'h'} invalid={!!fieldError} aria-describedby={fieldError ? 'param-value-input-error' : undefined} />
          </Field>
          )
          )
        )}
      </ReasonDialog>
    </>
  );
}
