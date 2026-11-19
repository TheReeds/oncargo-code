export const SETTLEMENT_STATUS_LABELS: Record<string, string> = {
  PENDING_INVOICE: 'Falta comprobante', INVOICED: 'Facturado', PAID: 'Pagado', CONFIRMED: 'Cobrado', DISPUTED: 'En disputa',
};
export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  TRANSFER: 'Transferencia', DEPOSIT: 'Depósito', YAPE: 'Yape', PLIN: 'Plin', CASH: 'Efectivo', OTHER: 'Otro',
};
export const PAYMENT_METHOD_OPTIONS = Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => ({ value, label }));
export const COMMISSION_STATUS_LABELS: Record<string, string> = { PENDING: 'Por cobrar', COLLECTED: 'Cobrada', NOT_APPLICABLE: 'No aplica' };
export const PAYOUT_KIND_LABELS: Record<string, string> = { BANK: 'Cuenta bancaria', YAPE: 'Yape', PLIN: 'Plin' };
