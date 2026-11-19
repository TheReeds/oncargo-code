import { useMemo, useState, type ReactNode } from 'react';
import { Banknote, BarChart3, FileText, Fuel, Gauge, Landmark, Layers, PackageCheck, Percent, PiggyBank, Receipt, Route, Scale, Truck, Wallet, Weight } from 'lucide-react';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardBody } from '@/components/ui/card';
import { QuickFilters } from '@/components/ui/quick-filters';
import { DateRangePicker } from '@/components/ui/date-picker';
import { Field } from '@/components/ui/field';
import { Select } from '@/components/ui/select';
import { Table, Td, Th, Tr } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { EmptyState } from '@/components/domain/EmptyState';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { SectionCard } from '@/components/domain/SectionCard';
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
  const stmtBase = (key: string, org?: string) => `/reportes/liquidacion/${key}${org ? `?org=${org}` : ''}`;
  const today = limaToday();
  const presetFrom = (m: number) => addDays(today, -Math.round(m * 30.4));
  const active = !range.from ? '6' : range.to === today && range.from === presetFrom(1) ? '1' : range.to === today && range.from === presetFrom(3) ? '3' : '';
  const choose = (v: string) => {
    if (v === '6') { setR({ from: '', to: '' }); go('', ''); return; }
    const from = presetFrom(Number(v)); setR({ from, to: today }); go(from, today);
  };
  // El rango se aplica solo al completar las dos fechas (o al quitarlas): no hay botón «Ver reporte» que parezca deshabilitado.
  const pick = (v: { from: string; to: string }) => {
    setR(v);
    if (!v.from && range.from) go('', '');
    else if (v.from && v.to && (v.from !== range.from || v.to !== range.to)) go(v.from, v.to);
  };

  const filters = (
    <Card className={navigating ? 'pointer-events-none opacity-60' : undefined} aria-busy={navigating || undefined}>
      <CardBody className="flex flex-wrap items-end gap-x-4 gap-y-3 py-4">
        <Field label="Período" htmlFor="rep-range" hint="Por fecha de adjudicación. Máximo 400 días." className="w-full sm:w-72">
          <DateRangePicker id="rep-range" value={r} onValueChange={pick} placeholder="Últimos 6 meses" max={limaToday()} />
        </Field>
        <div className="pb-5">
          <QuickFilters label="Períodos rápidos" value={active} onValueChange={choose}
            options={[{ value: '1', label: 'Último mes' }, { value: '3', label: '3 meses' }, { value: '6', label: '6 meses' }]} />
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
  const deliveredTxt = `${formatNumber(t.delivered)} ${t.delivered === 1 ? 'entregado' : 'entregados'}`;
  const kpis = perspective === 'PYME' ? (
    <KpiGrid>
      <KpiTile label="Gasto" value={formatMoney(t.finalTotal)} tone="money" icon={Wallet} context={`${formatMoney(t.netOfVat)} sin IGV`} />
      <KpiTile label="IGV" value={formatMoney(t.vat)} tone="petrol" icon={Receipt} context="Crédito fiscal" />
      <KpiTile label="Ahorro frente al sugerido" value={formatMoney(t.savings)} tone={(t.savings ?? 0) < 0 ? 'warn' : 'money'} icon={PiggyBank} context={`${formatPercent(t.savingsRate)} menos que el precio sugerido`} />
      <KpiTile label="Costo por kg" value={formatMoney(t.costPerKg)} tone="petrol" icon={Scale} context={`${formatTons(t.weightKg)} movidas`} />
      <KpiTile label="Envíos" value={formatNumber(t.shipments)} tone="petrol" icon={Truck} context="adjudicados en el período" />
      <KpiTile label="Entregados" value={formatNumber(t.delivered)} tone="money" icon={PackageCheck} context={`de ${formatNumber(t.shipments)} ${t.shipments === 1 ? 'envío' : 'envíos'}`} />
      <KpiTile label="Carga movida" value={formatTons(t.weightKg)} tone="petrol" icon={Weight} context="peso total transportado" />
      <KpiTile label="Distancia" value={formatKm(t.distanceKm)} tone="petrol" icon={Route} context="recorrida por tus cargas" />
    </KpiGrid>
  ) : perspective === 'CARRIER' ? (
    <KpiGrid>
      <KpiTile label="Ingresos" value={formatMoney(t.finalTotal)} tone="money" icon={Wallet} context={`${formatMoney(t.netOfVat)} sin IGV`} />
      <KpiTile label="Comisión de OnCargo" value={formatMoney(t.commission)} tone="petrol" icon={Percent} context={`+ ${formatMoney(t.commissionVat)} de IGV`} />
      <KpiTile label="Neto" value={formatMoney(t.carrierNet)} tone="money" icon={Landmark} context="Lo que te queda por tus fletes" />
      <KpiTile label="Neto por km" value={formatMoney(t.netPerKm)} tone="petrol" icon={Fuel} context="por kilómetro recorrido" />
      <KpiTile label="Envíos" value={formatNumber(t.shipments)} tone="petrol" icon={Truck} context={deliveredTxt} />
      <KpiTile label="Ocupación media" value={formatPercent(t.occupancy, 0)} tone="petrol" icon={Gauge} context="Carga frente a la capacidad del vehículo" />
      <KpiTile label="Carga movida" value={formatTons(t.weightKg)} tone="petrol" icon={Weight} context="peso total transportado" />
      <KpiTile label="Distancia" value={formatKm(t.distanceKm)} tone="petrol" icon={Route} context="recorrida en tus envíos" />
    </KpiGrid>
  ) : (
    <KpiGrid>
      <KpiTile label="GMV" value={formatMoney(t.finalTotal)} tone="money" icon={Banknote} context="Precio final de los envíos" />
      <KpiTile label="Comisión" value={formatMoney(t.commission)} tone="money" icon={Percent} context={`+ ${formatMoney(t.commissionVat)} de IGV`} />
      <KpiTile label="Tomas" value={formatNumber(t.shipments)} tone="petrol" icon={Truck} context={`${formatNumber(t.delivered)} ${t.delivered === 1 ? 'entregada' : 'entregadas'}`} />
      <KpiTile label="Carga movida" value={formatTons(t.weightKg)} tone="petrol" icon={Weight} context={`${formatKm(t.distanceKm)} en total`} />
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
        <Card><EmptyState icon={BarChart3} title="No hay envíos en este período" description="Prueba con otro rango de fechas. El reporte cuenta los envíos por fecha de adjudicación."
          action={active !== '6' ? <Button variant="outline" onClick={() => choose('6')}>Ver los últimos 6 meses</Button> : undefined} /></Card>
      ) : (
        <>
          <SectionCard icon={BarChart3} tone="petrol" title={TITLE[perspective]} description={`Con IGV, por fecha de adjudicación · ${covered}`} bodyClassName="space-y-5">
            <MonthlyBars label={TITLE[perspective]} from={overview.from} to={overview.to} data={monthRows.map((m) => ({ key: m.key!, value: m.totals?.finalTotal ?? 0 }))} />
            <div className="border-t border-steel-200 pt-4">
              <GroupTable rows={monthRows} total={t} perspective={perspective} first="Mes" name={(g) => monthLabel(g.key)} label="Por mes" />
            </div>
          </SectionCard>

          <SectionCard icon={Layers} tone="petrol" title="Detalle del período" description={perspective === 'OPERATOR' ? 'Por ruta y por transportista' : 'Por ruta, ' + (perspective === 'CARRIER' ? 'PYME, tipo de carga y vehículo' : 'transportista y tipo de carga')}>
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
          </SectionCard>
        </>
      )}

      {statements && (
        <SectionCard icon={FileText} tone="petrol" title={perspective === 'CARRIER' ? 'Liquidaciones de comisión' : 'Resúmenes de costos'}
          description="Documento interno del mes, por fecha de entrega. Se imprime o se guarda como PDF. No es un comprobante de SUNAT.">
          {months.length === 0 ? <p className="text-sm text-ink-500">Cuando tengas envíos en el período, verás aquí un documento por mes.</p> : (
            <ul className="flex flex-wrap gap-2" aria-label="Documentos por mes">
              {[...months].reverse().map((m) => (
                <li key={m}>
                  <Button asChild variant="outline" size="sm"><a href={stmtBase(m)}><FileText aria-hidden="true" />{perspective === 'CARRIER' ? 'Liquidación' : 'Resumen'} de {monthLabel(m)}</a></Button>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      )}
    </div>
  );
}
