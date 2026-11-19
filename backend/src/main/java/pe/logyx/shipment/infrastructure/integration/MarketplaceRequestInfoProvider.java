package pe.logyx.shipment.infrastructure.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.marketplace.application.MarketplaceQueries;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.domain.model.StopContact;

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
                .map(r -> {
                    var d = r.details() == null ? ShipmentRequest.Details.NONE : r.details();
                    return new RequestInfo(r.id(), r.cargo().originCity(), r.cargo().destinationCity(),
                            r.cargo().cargoDescription(), r.cargo().weightKg(), r.cargo().cargoType(),
                            r.cargo().volumeM3(), r.cargo().pickupDate(), r.pricing().distanceKm(), r.code(),
                            d.customerReference(), contact(d.pickup()), contact(d.delivery()), d.declaredValue());
                })
                .collect(Collectors.toMap(RequestInfo::id, Function.identity()));
    }

    private static StopContact contact(ShipmentRequest.StopDetails s) {
        if (s == null) return StopContact.NONE;
        return new StopContact(s.contactName(), s.contactPhone(), s.windowFrom(), s.windowTo(), s.instructions());
    }
}
