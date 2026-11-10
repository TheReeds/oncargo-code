package pe.logyx.pricing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.City;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Motor de costos basado en reglas (OE04 / E3 sección 3.2):
 * <pre>
 *   combustible = km × 0.65
 *   transporte  = km × toneladas × 0.80 × multiplicador(tipo de carga)
 *   peajes      = ceil(km / 120) × 12
 *   manipuleo   = 5% del transporte
 *   sugerido    = suma;  piso (RN03) = 75% del sugerido
 * </pre>
 */
@ApplicationScoped
public class PricingService {

    static final BigDecimal FUEL_PER_KM = new BigDecimal("0.65");
    static final BigDecimal RATE_PER_KM_TON = new BigDecimal("0.80");
    static final BigDecimal TOLL_EVERY_KM = new BigDecimal("120");
    static final BigDecimal TOLL_PRICE = new BigDecimal("12");
    static final BigDecimal HANDLING_RATE = new BigDecimal("0.05");

    @Inject DistanceProvider distance;

    @ConfigProperty(name = "logyx.pricing.floor-ratio")
    BigDecimal floorRatio;

    public record Quote(BigDecimal distanceKm, BigDecimal fuelCost, BigDecimal transportCost,
                        BigDecimal tollCost, BigDecimal handlingFee,
                        BigDecimal suggestedPrice, BigDecimal priceFloor) {}

    public Quote quote(City origin, City destination, BigDecimal weightKg, CargoType cargo) {
        BigDecimal km = BigDecimal.valueOf(distance.roadDistanceKm(origin, destination))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tons = weightKg.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);

        BigDecimal fuel = km.multiply(FUEL_PER_KM);
        BigDecimal transport = km.multiply(tons).multiply(RATE_PER_KM_TON).multiply(cargo.multiplier);
        BigDecimal tolls = km.divide(TOLL_EVERY_KM, 0, RoundingMode.CEILING).multiply(TOLL_PRICE);
        BigDecimal handling = transport.multiply(HANDLING_RATE);

        BigDecimal suggested = fuel.add(transport).add(tolls).add(handling).setScale(2, RoundingMode.HALF_UP);
        BigDecimal floor = suggested.multiply(floorRatio).setScale(2, RoundingMode.HALF_UP);
        return new Quote(km, money(fuel), money(transport), money(tolls), money(handling), suggested, floor);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
