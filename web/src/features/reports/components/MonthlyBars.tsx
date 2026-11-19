import { BarChart } from '@/components/domain/BarChart';
import { formatMoney } from '@/lib/format';
import { monthLabel, monthsBetween } from '../labels';

export type BarDatum = { key: string; value: number };

/**
 * Valor por mes del reporte sobre `BarChart`. Cubre todos los meses del período (los que no tienen envíos quedan en 0) para que con
 * 1 a 3 meses el gráfico no sea una barra suelta en un lienzo vacío. La tabla «Por mes» de al lado trae los mismos datos.
 */
export default function MonthlyBars({ data, label, from, to }: { data: BarDatum[]; label: string; from?: string | null; to?: string | null }) {
  const byKey = new Map(data.map((d) => [d.key, d.value]));
  const keys = monthsBetween(from, to);
  const all = (keys.length ? Array.from(new Set([...keys, ...data.map((d) => d.key)])).sort() : data.map((d) => d.key));
  return (
    <BarChart
      testId="monthly-bars"
      label={label}
      data={all.map((k) => ({ key: k, label: monthLabel(k), shortLabel: monthLabel(k).slice(0, 3), value: byKey.get(k) ?? 0 }))}
      formatValue={formatMoney}
    />
  );
}
