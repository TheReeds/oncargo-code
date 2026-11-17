package pe.logyx.shipment.application.port.out;

import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Datos de la solicitud de origen que muestra un envío. Los provee marketplace
 * (adaptador en infrastructure/integration); el puerto evita que la capa
 * application de shipment dependa de marketplace, que ya depende de shipment.
 */
public interface RequestInfoProvider {

    record RequestInfo(UUID id, City originCity, City destinationCity, String cargoDescription,
                       BigDecimal weightKg, pe.logyx.catalog.domain.CargoType cargoType, BigDecimal volumeM3,
                       java.time.LocalDate pickupDate, BigDecimal distanceKm) {}

    Map<UUID, RequestInfo> byIds(Collection<UUID> requestIds);
}
