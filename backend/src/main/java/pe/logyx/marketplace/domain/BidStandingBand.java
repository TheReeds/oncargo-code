package pe.logyx.marketplace.domain;

/**
 * Banda cualitativa de la oferta de un transportista frente a la mejor (más baja) de la subasta. Las ofertas son
 * selladas (RN04): a un transportista nunca se le muestra el monto ni el porcentaje exacto de otra oferta.
 */
public enum BidStandingBand {
    /** Igual a la mejor oferta (o la mejor). */
    BEST,
    /** Hasta 5 % por encima de la mejor. */
    WITHIN_5_PCT,
    /** Entre 5 % y 15 % por encima de la mejor. */
    WITHIN_15_PCT,
    /** Más de 15 % por encima de la mejor. */
    ABOVE_15_PCT
}
