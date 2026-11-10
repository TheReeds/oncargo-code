package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.application.MarketplaceFilter;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.ArrayList;
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
    public Paged<ShipmentRequest> searchOpenMarketplace(MarketplaceFilter f, PageRequest page) {
        StringBuilder where = new StringBuilder(
                "status = ?1 and id in (select a.requestId from AuctionEntity a where a.status = ?2)");
        List<Object> params = new ArrayList<>(List.of(ShipmentRequest.Status.PUBLISHED, Auction.Status.OPEN));
        if (f.originCity() != null) where.append(" and originCity = ?").append(add(params, f.originCity()));
        if (f.destinationCity() != null) where.append(" and destinationCity = ?").append(add(params, f.destinationCity()));
        if (f.pickupFrom() != null) where.append(" and pickupDate >= ?").append(add(params, f.pickupFrom()));
        if (f.pickupTo() != null) where.append(" and pickupDate <= ?").append(add(params, f.pickupTo()));
        if (f.maxWeightKg() != null) where.append(" and weightKg <= ?").append(add(params, f.maxWeightKg()));
        if (f.cargoType() != null) where.append(" and cargoType = ?").append(add(params, f.cargoType()));
        String order = switch (f.sort()) {
            case PICKUP -> " order by pickupDate asc, createdAt desc, id";
            case PRICE_DESC -> " order by suggestedPrice desc, createdAt desc, id";
            case RECENT -> " order by createdAt desc, id";
        };
        var query = ShipmentRequestEntity.<ShipmentRequestEntity>find(where + order, params.toArray())
                .page(page.page(), page.size());
        long total = ShipmentRequestEntity.count(where.toString(), params.toArray());
        return new Paged<>(map(query.list()), page, total);
    }

    @Override
    public List<ShipmentRequest> listPublishedWithoutBidsCreatedBefore(java.time.OffsetDateTime cutoff) {
        return map(ShipmentRequestEntity.list("status = ?1 and createdAt <= ?2 and id not in "
                + "(select b.requestId from BidEntity b) order by createdAt", ShipmentRequest.Status.PUBLISHED, cutoff));
    }

    @Override
    public List<ShipmentRequest> listWithOpenAuctionClosingBefore(java.time.OffsetDateTime until) {
        return ShipmentRequestEntity.getEntityManager().createQuery(
                        "select r from ShipmentRequestEntity r, AuctionEntity a where a.requestId = r.id "
                                + "and r.status = :published and a.status = :open and a.closesAt <= :until "
                                + "order by a.closesAt", ShipmentRequestEntity.class)
                .setParameter("published", ShipmentRequest.Status.PUBLISHED)
                .setParameter("open", Auction.Status.OPEN)
                .setParameter("until", until)
                .getResultList().stream().map(ShipmentRequestEntity::toDomain).toList();
    }

    @Override
    public long countByStatus(ShipmentRequest.Status status) {
        return ShipmentRequestEntity.count("status", status);
    }

    @Override
    public long countPublishedByRequester(UUID requesterOrgId) {
        return ShipmentRequestEntity.count("requesterOrgId = ?1 and status = ?2", requesterOrgId,
                ShipmentRequest.Status.PUBLISHED);
    }

    private static int add(List<Object> params, Object value) {
        params.add(value);
        return params.size();
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
