package pe.logyx.marketplace.application.port.out;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Historial de precios adjudicados por ruta y tipo de carga (2.8), agregado y anonimizado. La vista de la BD
 * ({@code lane_price_stats}, V22) solo devuelve grupos con al menos 5 envíos de 3 transportistas distintos en los
 * últimos 90 días y los redondea a S/ 5: nunca un monto individual.
 */
public interface LanePriceStatsReader {

    /** {@code samples}: envíos que sustentan el dato (>= 5). Montos con IGV, como las ofertas. */
    record LaneStats(int samples, BigDecimal median, BigDecimal low, BigDecimal high) {}

    Optional<LaneStats> find(City origin, City destination, CargoType cargoType);
}
