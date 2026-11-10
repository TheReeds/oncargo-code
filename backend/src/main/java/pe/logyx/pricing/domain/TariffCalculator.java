package pe.logyx.pricing.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.pricing.domain.model.PriceQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor de costos basado en reglas (OE04 / E3 sección 3.2):
 * <pre>
 *   combustible = km × 0.65
 *   transporte  = km × toneladas × 0.80 × multiplicador(tipo de carga)
 *   peajes      = ceil(km / 120) × 12
 *   manipuleo   = 5% del transporte
 *   sugerido    = suma;  piso (RN03) = PriceFloorPolicy(sugerido)
 * </pre>
 */
public final class TariffCalculator {

    static final BigDecimal FUEL_PER_KM = new BigDecimal("0.65");
    static final BigDecimal RATE_PER_KM_TON = new BigDecimal("0.80");
    static final BigDecimal TOLL_EVERY_KM = new BigDecimal("120");
    static final BigDecimal TOLL_PRICE = new BigDecimal("12");
    static final BigDecimal HANDLING_RATE = new BigDecimal("0.05");

    private final PriceFloorPolicy floorPolicy;

    public TariffCalculator(PriceFloorPolicy floorPolicy) {
        this.floorPolicy = floorPolicy;
    }

    /** Recargo por tipo de carga sobre el costo de transporte. */
    public static BigDecimal multiplier(CargoType cargo) {
        return switch (cargo) {
            case GENERAL -> new BigDecimal("1.00");
            case FRAGILE -> new BigDecimal("1.30");
            case PERISHABLE -> new BigDecimal("1.25");
            case HAZARDOUS -> new BigDecimal("1.50");
            case BULK -> new BigDecimal("0.95");
            case OVERSIZED -> new BigDecimal("1.40");
        };
    }

    public PriceQuote quote(double roadDistanceKm, BigDecimal weightKg, CargoType cargo) {
        BigDecimal km = BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tons = weightKg.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);

        BigDecimal fuel = km.multiply(FUEL_PER_KM);
        BigDecimal transport = km.multiply(tons).multiply(RATE_PER_KM_TON).multiply(multiplier(cargo));
        BigDecimal tolls = km.divide(TOLL_EVERY_KM, 0, RoundingMode.CEILING).multiply(TOLL_PRICE);
        BigDecimal handling = transport.multiply(HANDLING_RATE);

        BigDecimal suggested = fuel.add(transport).add(tolls).add(handling).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = floorPolicy.floorFor(suggested);
        return new PriceQuote(km, money(fuel), money(transport), money(tolls), money(handling), suggested, floor);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
