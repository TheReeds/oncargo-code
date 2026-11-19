package pe.logyx.settlement.domain.model;

/**
 * Estado del cobro de un envío entregado (roadmap 2.12): {@code PENDING_INVOICE} (falta el comprobante del
 * transportista) → {@code INVOICED} → {@code PAID} (la PYME registró el pago) → {@code CONFIRMED} (el transportista
 * confirmó que lo recibió). {@code DISPUTED}: alguna parte lo objetó; lo resuelve el operador (o lo retira quien lo abrió).
 */
public enum SettlementStatus {
    PENDING_INVOICE("Falta comprobante"), INVOICED("Facturado"), PAID("Pagado"), CONFIRMED("Cobrado"),
    DISPUTED("En disputa");

    public final String label;

    SettlementStatus(String label) { this.label = label; }

    /** La PYME aún debe pagar. */
    public boolean awaitingPayment() { return this == PENDING_INVOICE || this == INVOICED; }
}
