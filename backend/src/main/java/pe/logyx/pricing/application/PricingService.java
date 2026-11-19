package pe.logyx.pricing.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.pricing.application.port.out.DistanceProvider;
import pe.logyx.pricing.domain.PriceFloorPolicy;
import pe.logyx.pricing.domain.TariffCalculator;
import pe.logyx.pricing.domain.model.PriceQuote;
import pe.logyx.shared.config.SystemParameters;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

/**
 * Caso de uso: cotizar un flete con la versión vigente del tarifario ({@link TariffService}, 2.14) y el piso vigente
 * ({@code price_floor_ratio}, RN03). Lee datos de plataforma con caché; no necesita transacción.
 */
@ApplicationScoped
public class PricingService {

    @Inject DistanceProvider distance;
    @Inject TariffService tariffs;
    @Inject SystemParameters parameters;

    public PriceQuote quote(City origin, City destination, BigDecimal weightKg, CargoType cargo) {
        if (origin == destination) {
            throw BusinessException.badRequest("LOGYX-PRC-002", "Origen y destino deben ser distintos");
        }
        var calculator = new TariffCalculator(tariffs.current(), new PriceFloorPolicy(parameters.priceFloorRatio()));
        return calculator.quote(distance.roadDistanceKm(origin, destination), weightKg, cargo);
    }
}
