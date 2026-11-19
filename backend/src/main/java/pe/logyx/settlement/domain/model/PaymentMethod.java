package pe.logyx.settlement.domain.model;

/** Medio de pago fuera de la plataforma (2.12; RNF20: no se tocan tarjetas). */
public enum PaymentMethod {
    TRANSFER("Transferencia"), DEPOSIT("Depósito"), YAPE("Yape"), PLIN("Plin"), CASH("Efectivo"), OTHER("Otro");

    public final String label;

    PaymentMethod(String label) { this.label = label; }

    /** El efectivo no tiene número de operación. */
    public boolean requiresOperationNumber() { return this != CASH; }
}
