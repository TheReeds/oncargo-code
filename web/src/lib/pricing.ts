/**
 * Comisión de OnCargo e IGV sobre el precio adjudicado (ADR-0010, decisión del dueño 2026-11-18). Espeja
 * `pricing/domain/CommissionPolicy` del backend, con aritmética en céntimos para redondear igual (HALF_UP):
 *   valor de venta = precio / 1.18 · IGV = precio − valor de venta
 *   comisión = valor de venta × tasa (sin IGV) · IGV de la comisión = comisión × 0.18
 *   neto del transportista = precio − comisión − IGV de la comisión
 * Ejemplo: S/ 1,180 al 8 %: valor de venta 1,000.00 · IGV 180.00 · comisión 80.00 · IGV de la comisión 14.40 · neto 1,085.60.
 * La fuente de verdad sigue siendo la API (`GET /pricing/commission?amount=`, `Shipment.priceBreakdown`).
 */
export const VAT_RATE = 0.18;

export type PriceBreakdown = {
  /** Precio adjudicado con IGV. */
  amount: number;
  netOfVat: number;
  vat: number;
  commissionRate: number;
  /** Comisión sin IGV (sobre el valor de venta). */
  commission: number;
  commissionVat: number;
  /** Lo que cobra el transportista. */
  carrierNet: number;
};

const halfUpDiv = (n: number, d: number) => Math.floor((2 * n + d) / (2 * d));

/** Desglose de `amount` (soles, IGV incluido) con la tasa de comisión (0–1). Valores en soles con 2 decimales. */
export function priceBreakdown(amount: number, commissionRate: number): PriceBreakdown {
  const cents = Math.max(0, Math.round(amount * 100));
  const netCents = halfUpDiv(cents * 100, 118);
  const vatCents = cents - netCents;
  const rateBp = Math.round(commissionRate * 10_000);
  const commissionCents = halfUpDiv(netCents * rateBp, 10_000);
  const commissionVatCents = halfUpDiv(commissionCents * 18, 100);
  return {
    amount: cents / 100,
    netOfVat: netCents / 100,
    vat: vatCents / 100,
    commissionRate,
    commission: commissionCents / 100,
    commissionVat: commissionVatCents / 100,
    carrierNet: (cents - commissionCents - commissionVatCents) / 100,
  };
}

/** Solo el IGV (lo que ve la PYME): valor de venta e IGV de un precio con IGV incluido. */
export function vatSplit(amount: number): { amount: number; netOfVat: number; vat: number } {
  const b = priceBreakdown(amount, 0);
  return { amount: b.amount, netOfVat: b.netOfVat, vat: b.vat };
}

/** «8 %», «8.25 %» a partir de 0.08 / 0.0825. */
export const formatRate = (rate: number) => `${Number((rate * 100).toFixed(2))} %`;
