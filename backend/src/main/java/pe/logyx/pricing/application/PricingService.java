package pe.logyx.pricing.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.pricing.application.port.out.DistanceProvider;
import pe.logyx.pricing.domain.PriceFloorPolicy;
import pe.logyx.pricing.domain.TariffCalculator;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

/** Caso de uso: cotizar un flete. Sin estado ni BD (no necesita transacción). */
@ApplicationScoped
public class PricingService {

    private final DistanceProvider distance;
    private final TariffCalculator calculator;

    @Inject
    public PricingService(DistanceProvider distance,
                          @ConfigProperty(name = "logyx.pricing.floor-ratio") BigDecimal floorRatio) {
        this.distance = distance;
        this.calculator = new TariffCalculator(new PriceFloorPolicy(floorRatio));
    }

    public PriceQuote quote(City origin, City destination, BigDecimal weightKg, CargoType cargo) {
        if (origin == destination) {
            throw BusinessException.badRequest("LOGYX-PRC-002", "Origen y destino deben ser distintos");
        }
        return calculator.quote(distance.roadDistanceKm(origin, destination), weightKg, cargo);
    }
}
