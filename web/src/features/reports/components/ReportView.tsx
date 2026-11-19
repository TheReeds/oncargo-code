import { useMemo, useState, type ReactNode } from 'react';
import { BarChart3, FileText } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { DateRangePicker } from '@/components/ui/date-picker';
import { ExportButton } from '@/components/ui/export-button';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { EmptyState } from '@/components/domain/EmptyState';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { CARGO_LABELS, formatKm, formatMoney, formatNumber, formatTons } from '@/lib/format';
import { addDays, limaToday } from '@/lib/lima-date';
import type { ReportGroup, ReportOverview, ReportTotals } from '../server';
import { formatPercent, monthLabel, routeLabel } from '../labels';
import MonthlyBars from './MonthlyBars';

type Perspective = 'PYME' | 'CARRIER' | 'OPERATOR';
type Col = { label: string; cell: (t: ReportTotals) => ReactNode };

const money = (n?: number | null) => <span className="font-mono tabular-nums">{formatMoney(n)}</span>;
const num = (n?: number | null, d = 0) => <span className="font-mono tabular-nums">{formatNumber(n, d)}</span>;

/**
 * Columnas por rol. La PYME nunca recibe ni ve comisión (ADR-0010): sus columnas no la incluyen y la API la manda nula.
 */
const COLS: Record<Perspective, { count: string; amount: string; cols: Col[] }> = {
  PYME: {
    count: 'Envíos', amount: 'Gasto',
    cols: [
      { label: 'Envíos', cell: (t) => num(t.shipments) },
      { label: 'Gasto', cell: (t) => money(t.finalTotal) },
      { label: 'IGV', cell: (t) => money(t.vat) },
      { label: 'Ahorro', cell: (t) => <span className="font-mono tabular-nums">{formatMoney(t.savings)} <span className="text-xs text-ink-500">({formatPercent(t.savingsRate)})</span></span> },
      { label: 'Costo por kg', cell: (t) => money(t.costPerKg) },
    ],
  },
  CARRIER: {
    count: 'Envíos', amount: 'Ingresos',
    cols: [
      { label: 'Envíos', cell: (t) => num(t.shipments) },
      { label: 'Ingresos', cell: (t) => money(t.finalTotal) },
      { label: 'Comisión', cell: (t) => money(t.commission) },
      { label: 'Neto', cell: (t) => money(t.carrierNet) },
      { label: 'Neto por km', cell: (t) => money(t.netPerKm) },
    ],
  },
  OPERATOR: {
    count: 'Tomas', amount: 'GMV',
    cols: [
      { label: 'Tomas', cell: (t) => num(t.shipments) },
      { label: 'GMV', cell: (t) => money(t.finalTotal) },
      { label: 'Comisión', cell: (t) => money(t.commission) },
      { label: 'IGV de la comisión', cell: (t) => money(t.commissionVat) },
    ],
  },
};

const TITLE: Record<Perspective, string> = { PYME: 'Gasto por mes', CARRIER: 'Ingresos por mes', OPERATOR: 'GMV por mes' };

function GroupTable({ rows, total, perspective, first, name, extra, label, extraHead }: {
  rows: ReportGroup[]; total?: ReportTotals; perspective: Perspective; first: string; name: (g: ReportGroup) => ReactNode;
  extra?: Col[]; label: string; extraHead?: { head: string; cell: (g: ReportGroup) => ReactNode };
}) {
  const cols = [...COLS[perspective].cols, ...(extra ?? [])];
  if (!rows.length) return <p className="rounded-card border border-dashed border-steel-200 px-4 py-6 text-center text-sm text-ink-500">Sin envíos en este período.</p>;
  return (
    <Table label={label} stack dense>
      <thead><tr><Th>{first}</Th>{cols.map((c) => <Th key={c.label} className="text-right">{c.label}</Th>)}{extraHead && <Th><span className="sr-only">{extraHead.head}</span></Th>}</tr></thead>
      <tbody>
        {rows.map((g) => (
          <Tr key={g.key} data-testid="report-row">
            <Td primary className="font-medium text-ink-900">{name(g)}</Td>
            {cols.map((c) => <Td key={c.label} label={c.label} className="text-right">{c.cell(g.totals ?? {})}</Td>)}
            {extraHead && <Td actions className="text-right">{extraHead.cell(g)}</Td>}
          </Tr>
        ))}
      </tbody>
      {total && (
        <tfoot>
          <Tr className="bg-steel-100/60 font-semibold" data-testid="report-total">
            <Td primary>Total</Td>
            {cols.map((c) => <Td key={c.label} label={c.label} className="text-right">{c.cell(total)}</Td>)}
            {extraHead && <Td aria-hidden="true" />}
          </Tr>
        </tfoot>
      )}
    </Table>
  );
}

/** Reporte del período (2.11) para PYME, transportista y operador. El rango va en la URL (`desde`, `hasta`). */
export default function ReportView({ perspective, overview, error, range, base }: {
  perspective: Perspective; overview: ReportOverview | null; error: string | null; range: { from: string; to: string }; base: string;
}) {
  const [r, setR] = useState(range);
  const [navigating, setNavigating] = useState(false);
  const months = useMemo(() => (overview?.byMonth ?? []).map((m) => m.key!).filter(Boolean), [overview]);
  const [stmtMonth, setStmtMonth] = useState(months[months.length - 1] ?? '');
  const t = overview?.totals;

  function go(from: string, to: string) {
    setNavigating(true);
    const q = new URLSearchParams();
    if (from) { q.set('desde', from); q.set('hasta', to || from); }
    const qs = q.toString();
    window.location.href = base + (qs ? `?${qs}` : '');
  }
  const preset = (months: number) => { const today = limaToday(); const from = addDays(today, -Math.round(months * 30.4)); setR({ from, to: today }); go(from, today); };
  const exportHref = `/exportar/reportes${r.from ? `?desde=${r.from}&hasta=${r.to || r.from}` : range.from ? `?desde=${range.from}&hasta=${range.to}` : ''}`;
  const stmtBase = (key: string, org?: string) => `/reportes/liquidacion/${key}${org ? `?org=${org}` : ''}`;

  const filters = (
    <Card className={navigating ? 'pointer-events-none opacity-60' : undefined} aria-busy={navigating || undefined}>
      <CardBody className="flex flex-wrap items-end gap-3">
        <Field label="Período" htmlFor="rep-range" hint="Por fecha de adjudicación. Máximo 400 días." className="w-full sm:w-72">
          <DateRangePicker id="rep-range" value={r} onValueChange={setR} placeholder="Últimos 6 meses" max={limaToday()} />
        </Field>
        <Button onClick={() => go(r.from, r.to)} disabled={!r.from}>Ver reporte</Button>
        <div className="flex flex-wrap gap-2 sm:ml-auto" role="group" aria-label="Períodos rápidos">
          <Button variant="outline" size="sm" onClick={() => preset(1)}>Último mes</Button>
          <Button variant="outline" size="sm" onClick={() => preset(3)}>3 meses</Button>
          <Button variant="outline" size="sm" onClick={() => { setR({ from: '', to: '' }); go('', ''); }}>6 meses</Button>
          <ExportButton href={exportHref} />
        </div>
      </CardBody>
    </Card>
  );

  if (error || !overview || !t) {
    return (
      <div className="space-y-4">
        {filters}
        <Alert tone="error" title="No pudimos armar el reporte">{error ?? 'Intenta nuevamente en unos segundos.'}</Alert>
      </div>
    );
  }

  const empty = !t.shipments;
  const covered = `${overview.from ? monthLabel(overview.from.slice(0, 7)) : ''} – ${overview.to ? monthLabel(overview.to.slice(0, 7)) : ''}`;
  const kpis = perspective === 'PYME' ? (
    <>
      <KpiGrid>
        <KpiTile label="Gasto" value={formatMoney(t.finalTotal)} tone="money" context={`${formatMoney(t.netOfVat)} sin IGV`} />
        <KpiTile label="IGV" value={formatMoney(t.vat)} context="Crédito fiscal" />
        <KpiTile label="Ahorro frente al sugerido" value={formatMoney(t.savings)} tone="money" context={`${formatPercent(t.savingsRate)} menos que el precio sugerido`} />
        <KpiTile label="Costo por kg" value={formatMoney(t.costPerKg)} context={`${formatTons(t.weightKg)} movidas`} />
      </KpiGrid>
      <KpiGrid cols={3} className="mt-3 sm:mt-4">
        <KpiTile label="Envíos" value={formatNumber(t.shipments)} context={`${formatNumber(t.delivered)} ${t.delivered === 1 ? 'entregado' : 'entregados'}`} />
        <KpiTile label="Carga movida" value={formatTons(t.weightKg)} />
        <KpiTile label="Distancia" value={formatKm(t.distanceKm)} />
      </KpiGrid>
    </>
  ) : perspective === 'CARRIER' ? (
    <>
      <KpiGrid>
        <KpiTile label="Ingresos" value={formatMoney(t.finalTotal)} tone="money" context={`${formatMoney(t.netOfVat)} sin IGV`} />
        <KpiTile label="Comisión de OnCargo" value={formatMoney(t.commission)} context={`+ ${formatMoney(t.commissionVat)} de IGV`} />
        <KpiTile label="Neto" value={formatMoney(t.carrierNet)} tone="money" context="Lo que te queda por tus fletes" />
        <KpiTile label="Neto por km" value={formatMoney(t.netPerKm)} context={`${formatKm(t.distanceKm)} recorridos`} />
      </KpiGrid>
      <KpiGrid cols={3} className="mt-3 sm:mt-4">
        <KpiTile label="Envíos" value={formatNumber(t.shipments)} context={`${formatNumber(t.delivered)} ${t.delivered === 1 ? 'entregado' : 'entregados'}`} />
        <KpiTile label="Ocupación media" value={formatPercent(t.occupancy, 0)} context="Carga frente a la capacidad del vehículo" />
        <KpiTile label="Carga movida" value={formatTons(t.weightKg)} />
      </KpiGrid>
    </>
  ) : (
    <KpiGrid>
      <KpiTile label="GMV" value={formatMoney(t.finalTotal)} tone="money" context="Precio final de los envíos" />
      <KpiTile label="Comisión" value={formatMoney(t.commission)} tone="money" context={`+ ${formatMoney(t.commissionVat)} de IGV`} />
      <KpiTile label="Tomas" value={formatNumber(t.shipments)} context={`${formatNumber(t.delivered)} ${t.delivered === 1 ? 'entregada' : 'entregadas'}`} />
      <KpiTile label="Carga movida" value={formatTons(t.weightKg)} context={`${formatKm(t.distanceKm)} en total`} />
    </KpiGrid>
  );

  const monthRows = overview.byMonth ?? [];
  const counterLabel = perspective === 'CARRIER' ? 'PYME' : 'Transportista';
  const counterTab = perspective === 'CARRIER' ? 'PYMEs' : 'Transportistas';
  const cargo = (g: ReportGroup) => CARGO_LABELS[g.key ?? ''] ?? g.label;
  const statements = perspective === 'PYME' || perspective === 'CARRIER';

  return (
    <div className="space-y-5" aria-busy={navigating || undefined}>
      {filters}
      {overview.truncated && <Alert tone="warning" title="El reporte quedó incompleto">Hay más envíos de los que se pueden sumar de una vez. Acota las fechas para ver cifras completas.</Alert>}
      {kpis}
      {empty ? (
        <Card><EmptyState icon={BarChart3} title="No hay envíos en este período" description="Prueba con otro rango de fechas. El reporte cuenta los envíos por fecha de adjudicación." /></Card>
      ) : (
        <>
          <Card>
            <CardHeader><div><CardTitle>{TITLE[perspective]}</CardTitle><CardDescription>Con IGV, por fecha de adjudicación · {covered}</CardDescription></div></CardHeader>
            <CardBody className="space-y-5">
              <MonthlyBars label={TITLE[perspective]} data={monthRows.map((m) => ({ key: m.key!, value: m.totals?.finalTotal ?? 0 }))} />
              <GroupTable rows={monthRows} total={t} perspective={perspective} first="Mes" name={(g) => monthLabel(g.key)} label="Por mes" />
            </CardBody>
          </Card>

          <Card>
            <CardBody>
              <Tabs defaultValue="rutas">
                <TabsList aria-label="Detalle del reporte">
                  <TabsTrigger value="rutas">Rutas</TabsTrigger>
                  <TabsTrigger value="contraparte">{counterTab}</TabsTrigger>
                  {perspective !== 'OPERATOR' && <TabsTrigger value="carga">Tipo de carga</TabsTrigger>}
                  {perspective === 'CARRIER' && <TabsTrigger value="vehiculos">Vehículos</TabsTrigger>}
                  {perspective === 'CARRIER' && <TabsTrigger value="adjudicacion">Adjudicación</TabsTrigger>}
                </TabsList>
                <TabsContent value="rutas">
                  <GroupTable rows={overview.byRoute ?? []} total={t} perspective={perspective} first="Ruta" name={(g) => routeLabel(g.key)} label="Por ruta" />
                </TabsContent>
                <TabsContent value="contraparte">
                  {perspective === 'OPERATOR' && months.length > 0 && (
                    <div className="mb-3 flex flex-wrap items-end gap-3">
                      <Field label="Mes de la liquidación" htmlFor="rep-stmt-month" className="w-48">
                        <Select id="rep-stmt-month" value={stmtMonth} onValueChange={setStmtMonth} options={months.map((m) => ({ value: m, label: monthLabel(m) }))} />
                      </Field>
                      <p className="pb-2 text-xs text-ink-500">La liquidación cuenta los envíos por fecha de entrega.</p>
                    </div>
                  )}
                  <GroupTable rows={overview.byCounterparty ?? []} total={t} perspective={perspective} first={counterLabel} name={(g) => g.label}
                    label={`Por ${counterLabel.toLowerCase()}`}
                    extraHead={perspective === 'OPERATOR' && stmtMonth ? { head: 'Liquidación', cell: (g) => <Button asChild variant="outline" size="sm"><a href={stmtBase(stmtMonth, g.key)}>Liquidación<span className="sr-only"> de {g.label}</span></a></Button> } : undefined} />
                </TabsContent>
                {perspective !== 'OPERATOR' && (
                  <TabsContent value="carga">
                    <GroupTable rows={overview.byCargoType ?? []} total={t} perspective={perspective} first="Tipo de carga" name={cargo} label="Por tipo de carga" />
                  </TabsContent>
                )}
                {perspective === 'CARRIER' && (
                  <TabsContent value="vehiculos">
                    <GroupTable rows={overview.byVehicle ?? []} total={t} perspective={perspective} first="Vehículo" name={(g) => <span className="font-mono">{g.label}</span>} label="Por vehículo"
                      extra={[{ label: 'Ocupación', cell: (x) => <span className="font-mono tabular-nums">{formatPercent(x.occupancy, 0)}</span> }]} />
                  </TabsContent>
                )}
                {perspective === 'CARRIER' && (
                  <TabsContent value="adjudicacion">
                    {(overview.bidsByRoute ?? []).length === 0 ? <p className="rounded-card border border-dashed border-steel-200 px-4 py-6 text-center text-sm text-ink-500">Aún no hiciste ofertas en este período.</p> : (
                      <Table label="Adjudicación por ruta" stack dense>
                        <thead><tr><Th>Ruta</Th><Th className="text-right">Ofertas</Th><Th className="text-right">Ganadas</Th><Th className="text-right">Tasa de adjudicación</Th></tr></thead>
                        <tbody>
                          {overview.bidsByRoute!.map((b) => (
                            <Tr key={`${b.originCity}-${b.destinationCity}`} data-testid="report-row">
                              <Td primary className="font-medium text-ink-900">{routeLabel(`${b.originCity}-${b.destinationCity}`)}</Td>
                              <Td label="Ofertas" className="text-right">{num(b.bids)}</Td>
                              <Td label="Ganadas" className="text-right">{num(b.won)}</Td>
                              <Td label="Tasa de adjudicación" className="text-right"><span className="font-mono tabular-nums">{formatPercent(b.winRate, 0)}</span></Td>
                            </Tr>
                          ))}
                        </tbody>
                      </Table>
                    )}
                  </TabsContent>
                )}
              </Tabs>
            </CardBody>
          </Card>
        </>
      )}

      {statements && (
        <Card>
          <CardHeader>
            <div>
              <CardTitle>{perspective === 'CARRIER' ? 'Liquidaciones de comisión' : 'Resúmenes de costos'}</CardTitle>
              <CardDescription>Documento interno del mes, por fecha de entrega. Se imprime o se guarda como PDF. No es un comprobante de SUNAT.</CardDescription>
            </div>
          </CardHeader>
          <CardBody>
            {months.length === 0 ? <p className="text-sm text-ink-500">Cuando tengas envíos en el período, verás aquí un documento por mes.</p> : (
              <ul className="flex flex-wrap gap-2" aria-label="Documentos por mes">
                {[...months].reverse().map((m) => (
                  <li key={m}>
                    <Button asChild variant="outline" size="sm"><a href={stmtBase(m)}><FileText aria-hidden="true" />{perspective === 'CARRIER' ? 'Liquidación' : 'Resumen'} de {monthLabel(m)}</a></Button>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
