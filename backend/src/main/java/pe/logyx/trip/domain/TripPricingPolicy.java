package pe.logyx.trip.domain;

import pe.logyx.trip.domain.model.TripPricingMode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Precio de una reserva en un viaje publicado.
 * <ul>
 *   <li>{@link TripPricingMode#PER_KG}: {@code precio = tarifa × pesoKg}.</li>
 *   <li>{@link TripPricingMode#PER_TRIP}: la tarifa cubre todo el tramo con la capacidad publicada completa; la
 *       reserva paga lo proporcional: {@code precio = tarifa × pesoKg / capacidadPublicadaKg}.</li>
 * </ul>
 * Se redondea a 2 decimales (HALF_UP) y nunca baja de {@link #MIN_PRICE}.
 */
public final class TripPricingPolicy {

    public static final BigDecimal MIN_PRICE = new BigDecimal("1.00");

    private TripPricingPolicy() {}

    public static BigDecimal price(TripPricingMode mode, BigDecimal rate, BigDecimal capacityKg, BigDecimal weightKg) {
        BigDecimal raw = switch (mode) {
            case PER_KG -> rate.multiply(weightKg);
            case PER_TRIP -> rate.multiply(weightKg).divide(capacityKg, 6, RoundingMode.HALF_UP);
        };
        return raw.setScale(2, RoundingMode.HALF_UP).max(MIN_PRICE);
    }
}
