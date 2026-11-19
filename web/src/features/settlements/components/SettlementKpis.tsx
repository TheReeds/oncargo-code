import { AlarmClock, BadgeCheck, HandCoins, Percent, Scale, Wallet } from 'lucide-react';
import { KpiGrid, KpiTile } from '@/components/domain/KpiTile';
import { formatMoney } from '@/lib/format';
import type { SettlementSummary } from '../server';

/** Tarjetas de totales del cobro según el rol (máx. 4). */
export default function SettlementKpis({ summary, perspective }: { summary: SettlementSummary; perspective: 'PYME' | 'CARRIER' | 'OPERATOR' }) {
  const s = summary;
  const noun = perspective === 'PYME' ? 'Por pagar' : 'Por cobrar';
  const count = (n?: number) => `${n ?? 0} ${n === 1 ? 'envío' : 'envíos'}`;
  return (
    <KpiGrid>
      <KpiTile label={noun} value={formatMoney(s.awaitingAmount)} tone="money" icon={perspective === 'PYME' ? Wallet : HandCoins} context={count(s.awaiting)} />
      <KpiTile label="Vencidos" value={formatMoney(s.overdueAmount)} tone={s.overdue ? 'alert' : 'petrol'} icon={AlarmClock} context={count(s.overdue)} />
      {perspective === 'PYME' && <KpiTile label="Pagos por confirmar" value={formatMoney(s.toConfirmAmount)} tone={s.toConfirm ? 'warn' : 'petrol'} icon={BadgeCheck} context={`${count(s.toConfirm)} esperan al transportista`} />}
      {perspective === 'PYME' && <KpiTile label="En disputa" value={String(s.disputed ?? 0)} tone={s.disputed ? 'warn' : 'petrol'} icon={Scale} context="Los resuelve OnCargo" />}
      {perspective === 'CARRIER' && <KpiTile label="Pagos por confirmar" value={formatMoney(s.toConfirmAmount)} context={`${count(s.toConfirm)}: confirma si ya los recibiste`} tone={s.toConfirm ? 'warn' : 'petrol'} icon={BadgeCheck} />}
      {perspective === 'CARRIER' && <KpiTile label="Comisión de OnCargo" value={formatMoney(s.commissionPendingAmount)} tone="petrol" icon={Percent} context={`${count(s.commissionPending)} por pagar, con IGV`} />}
      {perspective === 'OPERATOR' && <KpiTile label="En disputa" value={String(s.disputed ?? 0)} tone={s.disputed ? 'alert' : 'petrol'} icon={Scale} context="Por resolver" />}
      {perspective === 'OPERATOR' && <KpiTile label="Comisión pendiente" value={formatMoney(s.commissionPendingAmount)} tone="money" icon={Percent} context={`${count(s.commissionPending)}, con IGV`} />}
    </KpiGrid>
  );
}

