package pe.logyx.pricing.domain.model;

import java.math.BigDecimal;

/**
 * Cotización de un flete: desglose de costos, precio sugerido y piso (RN03). {@code baseFee} y {@code tariffVersion}
 * (V24, 2.14): tarifa base y versión del tarifario con que se calculó.
 */
public record PriceQuote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                         BigDecimal tollCost, BigDecimal handlingFee,
                         BigDecimal suggestedPrice, BigDecimal priceFloor,
                         BigDecimal baseFee, int tariffVersion) {}
