package pe.logyx.marketplace.domain;

/**
 * Posición aproximada de la oferta de un transportista entre las ofertas no retiradas de la subasta (2.8), sin
 * revelar montos (RN04): {@code TOP_TWO} entre las 2 más bajas; {@code LOWER_HALF} en la mediana o por debajo;
 * {@code UPPER_HALF} por encima de la mediana.
 */
public enum BidPosition { TOP_TWO, LOWER_HALF, UPPER_HALF }
