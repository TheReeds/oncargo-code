package pe.logyx.pricing.domain.model;

import java.math.BigDecimal;

/** Cotización de un flete: desglose de costos, precio sugerido y piso (RN03). */
public record PriceQuote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                         BigDecimal tollCost, BigDecimal handlingFee,
                         BigDecimal suggestedPrice, BigDecimal priceFloor) {}
