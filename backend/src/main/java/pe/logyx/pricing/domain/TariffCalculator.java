package pe.logyx.pricing.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.pricing.domain.model.Tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor de costos basado en reglas (OE04 / E3 sección 3.2) con los valores de una versión del tarifario
 * ({@link Tariff}, editable por el operador desde V24, roadmap 2.14):
 * <pre>
 *   combustible = km × combustible_km
 *   transporte  = km × toneladas × tarifa_t_km × multiplicador(tipo de carga)
 *   peajes      = ceil(km / cada_km) × peaje
 *   manipuleo   = manipuleo% del transporte
 *   sugerido    = base + suma;  piso (RN03) = PriceFloorPolicy(sugerido)
 * </pre>
 * Con {@link Tariff#STANDARD} (versión 1, base 0) da lo mismo que antes de V24.
 */
public final class TariffCalculator {

    private final Tariff tariff;
    private final PriceFloorPolicy floorPolicy;

    public TariffCalculator(Tariff tariff, PriceFloorPolicy floorPolicy) {
        this.tariff = tariff;
        this.floorPolicy = floorPolicy;
    }

    /** Con la tarifa de la versión 1. */
    public TariffCalculator(PriceFloorPolicy floorPolicy) {
        this(Tariff.STANDARD, floorPolicy);
    }

    /** Recargo por tipo de carga de la tarifa de la versión 1. */
    public static BigDecimal multiplier(CargoType cargo) {
        return Tariff.STANDARD.multiplier(cargo);
    }

    public PriceQuote quote(double roadDistanceKm, BigDecimal weightKg, CargoType cargo) {
        BigDecimal km = BigDecimal.valueOf(roadDistanceKm).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tons = weightKg.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);

        BigDecimal fuel = km.multiply(tariff.fuelPerKm());
        BigDecimal transport = km.multiply(tons).multiply(tariff.ratePerKmTon()).multiply(tariff.multiplier(cargo));
        BigDecimal tolls = km.divide(tariff.tollEveryKm(), 0, RoundingMode.CEILING).multiply(tariff.tollPrice());
        BigDecimal handling = transport.multiply(tariff.handlingRate());
        BigDecimal base = tariff.baseFee();

        BigDecimal suggested = base.add(fuel).add(transport).add(tolls).add(handling).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = floorPolicy.floorFor(suggested);
        return new PriceQuote(km, money(fuel), money(transport), money(tolls), money(handling), suggested, floor,
                money(base), tariff.version());
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
