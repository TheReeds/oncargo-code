package pe.logyx.shipment.domain.model;

/**
 * Tipo de ajuste del precio de un envío (roadmap 2.6). Enum con nombre propio: es un schema estable del contrato.
 * {@code WEIGHT_DIFF} peso real distinto del declarado (puede ser un descuento), {@code DETENTION} estadía o tiempo de
 * espera, {@code TOLL} peajes, {@code HANDLING} maniobras (carga, descarga, estiba), {@code OTHER} otro (con nota).
 */
public enum AdjustmentType {
    WEIGHT_DIFF, DETENTION, TOLL, HANDLING, OTHER;

    /** Solo el peso y «otro» pueden bajar el precio (monto negativo). */
    public boolean allowsDiscount() { return this == WEIGHT_DIFF || this == OTHER; }
}
