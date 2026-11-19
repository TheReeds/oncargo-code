package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.application.port.out.LanePriceStatsReader;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Lee la vista {@code lane_price_stats} (V22). La vista corre con los privilegios de su dueño (cruza organizaciones) y
 * por diseño solo contiene agregados con k >= 5: por eso se puede consultar con la RLS del transportista sin SystemTx.
 */
@ApplicationScoped
public class NativeLanePriceStatsReader implements LanePriceStatsReader {

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<LaneStats> find(City origin, City destination, CargoType cargoType) {
        List<Object[]> rows = em.createNativeQuery("SELECT samples, median_price, low_price, high_price "
                        + "FROM lane_price_stats WHERE origin_city = :o AND destination_city = :d AND cargo_type = :c")
                .setParameter("o", origin.name()).setParameter("d", destination.name())
                .setParameter("c", cargoType.name()).getResultList();
        return rows.stream().findFirst().map(r -> new LaneStats(((Number) r[0]).intValue(), (BigDecimal) r[1],
                (BigDecimal) r[2], (BigDecimal) r[3]));
    }
}
