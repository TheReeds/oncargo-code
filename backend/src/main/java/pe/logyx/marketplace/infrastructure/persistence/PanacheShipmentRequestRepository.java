package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheShipmentRequestRepository implements ShipmentRequestRepository {

    @Override
    public Optional<ShipmentRequest> findById(UUID id) {
        return ShipmentRequestEntity.<ShipmentRequestEntity>findByIdOptional(id).map(ShipmentRequestEntity::toDomain);
    }

    @Override
    public Optional<ShipmentRequest> findOwned(UUID id, UUID requesterOrgId) {
        return ShipmentRequestEntity.<ShipmentRequestEntity>find("id = ?1 and requesterOrgId = ?2", id, requesterOrgId)
                .firstResultOptional().map(ShipmentRequestEntity::toDomain);
    }

    @Override
    public List<ShipmentRequest> listByRequester(UUID requesterOrgId) {
        return map(ShipmentRequestEntity.list("requesterOrgId = ?1 order by createdAt desc", requesterOrgId));
    }

    @Override
    public List<ShipmentRequest> listOpenMarketplace() {
        return map(ShipmentRequestEntity.list(
                "status = ?1 and id in (select a.requestId from AuctionEntity a where a.status = ?2) "
                        + "order by pickupDate asc, createdAt desc",
                ShipmentRequest.Status.PUBLISHED, Auction.Status.OPEN));
    }

    @Override
    public List<ShipmentRequest> listByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return map(ShipmentRequestEntity.list("id in ?1 order by createdAt desc", ids));
    }

    @Override
    public void save(ShipmentRequest request) {
        ShipmentRequestEntity e = ShipmentRequestEntity.findById(request.id());
        if (e == null) {
            e = new ShipmentRequestEntity();
            e.apply(request);
            e.persist();
        } else {
            e.apply(request);
        }
    }

    private static List<ShipmentRequest> map(List<ShipmentRequestEntity> list) {
        return list.stream().map(ShipmentRequestEntity::toDomain).toList();
    }
}
