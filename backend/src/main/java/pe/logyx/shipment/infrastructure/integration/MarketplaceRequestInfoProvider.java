package pe.logyx.shipment.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adaptador hacia marketplace (a través de su capa application). */
@ApplicationScoped
public class MarketplaceRequestInfoProvider implements RequestInfoProvider {

    @Inject MarketplaceQueries marketplace;

    @Override
    public Map<UUID, RequestInfo> byIds(Collection<UUID> requestIds) {
        return marketplace.requestsByIds(requestIds).stream()
                .map(r -> new RequestInfo(r.id(), r.cargo().originCity(), r.cargo().destinationCity(),
                        r.cargo().cargoDescription(), r.cargo().weightKg(), r.cargo().cargoType(),
                        r.cargo().volumeM3(), r.cargo().pickupDate(), r.pricing().distanceKm()))
                .collect(Collectors.toMap(RequestInfo::id, Function.identity()));
    }
}
