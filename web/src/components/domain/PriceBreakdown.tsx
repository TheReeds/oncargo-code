import { ChevronRight } from 'lucide-react';
import { formatMoney } from '@/lib/format';
import { formatRate } from '@/lib/pricing';
import { cn } from '@/lib/utils';
import type { Schemas } from '@/lib/api/server';

/**
 * Precio adjudicado con el IGV desglosado (ADR-0010). Detalle plegable (`<details>` nativo, sin JS).
 * La PYME ve valor de venta + IGV; el transportista y el operador ven además la comisión de OnCargo con la tasa que quedó
 * guardada al adjudicar, su IGV y «Cobras neto» (los campos de comisión llegan nulos para la PYME y el conductor).
 */
export function PriceBreakdown({ breakdown, className, totalLabel = 'Precio acordado' }: { breakdown?: Schemas['PriceBreakdownView'] | null; className?: string; /** «Precio final» cuando incluye los ajustes aceptados (2.6). */ totalLabel?: string }) {
  if (!breakdown || breakdown.amount == null) return null;
  const b = breakdown;
  const withCommission = b.commission != null && b.carrierNet != null;
  return (
    <details className={cn('group text-xs text-ink-700', className)} data-testid="price-breakdown">
      <summary className="inline-flex w-fit cursor-pointer list-none items-center gap-1 rounded-sm font-medium text-petrol-700 hover:underline [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3.5 transition-transform group-open:rotate-90 motion-reduce:transition-none" aria-hidden="true" />
        {withCommission ? 'Ver IGV y comisión' : 'Ver IGV'}
      </summary>
      <dl className="mt-2 grid min-w-56 grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-left">
        <dt>Valor de venta (sin IGV)</dt><dd className="text-right font-mono tabular-nums">{formatMoney(b.netOfVat)}</dd>
        <dt>IGV ({formatRate(b.vatRate ?? 0.18)})</dt><dd className="text-right font-mono tabular-nums">{formatMoney(b.vat)}</dd>
        <dt className="border-t border-steel-200 pt-1 font-medium text-ink-900">{totalLabel}</dt><dd className="border-t border-steel-200 pt-1 text-right font-mono font-medium tabular-nums text-ink-900">{formatMoney(b.amount)}</dd>
        {withCommission && (
          <>
            <dt>Comisión de OnCargo ({formatRate(b.commissionRate ?? 0)} del valor de venta)</dt><dd className="text-right font-mono tabular-nums">− {formatMoney(b.commission)}</dd>
            <dt>IGV de la comisión ({formatRate(b.vatRate ?? 0.18)})</dt><dd className="text-right font-mono tabular-nums">− {formatMoney(b.commissionVat)}</dd>
            <dt className="border-t border-steel-200 pt-1 font-medium text-emerald-700">Cobras neto</dt><dd className="border-t border-steel-200 pt-1 text-right font-mono font-semibold tabular-nums text-emerald-700">{formatMoney(b.carrierNet)}</dd>
          </>
        )}
      </dl>
    </details>
  );
}
