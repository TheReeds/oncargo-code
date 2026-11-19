package pe.logyx.marketplace.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.application.MarketplaceFilter;
import pe.logyx.marketplace.application.OperatorRequestFilter;
import pe.logyx.marketplace.domain.model.Auction;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;

import jakarta.inject.Inject;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.application.MyRequestFilter;
import pe.logyx.marketplace.application.RequestCounts;
import pe.logyx.marketplace.domain.RequestCode;
import pe.logyx.shared.domain.LikePatterns;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.tenancy.SystemTx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheShipmentRequestRepository implements ShipmentRequestRepository {

    private static final java.time.ZoneId LIMA = java.time.ZoneId.of("America/Lima");
    /** Ofertas por revisar de una solicitud (no retiradas): lo que cuenta la subasta. */
    private static final String LIVE_BIDS = "(select count(b.id) from BidEntity b where b.requestId = r.id and b.status <> ?%d)";

    @Inject SystemTx system;

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
        // F07: también las que ya cerraron su subasta sin ofertas (asignables al instante, RN10 alineada con RN04).
        return map(ShipmentRequestEntity.list("status = ?1 and id not in "
                + "(select b.requestId from BidEntity b where b.status <> ?3) and (createdAt <= ?2 or id in "
                + "(select a.requestId from AuctionEntity a where a.status = ?4)) order by createdAt",
                ShipmentRequest.Status.PUBLISHED, cutoff, Bid.Status.WITHDRAWN, Auction.Status.CLOSED));
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
    public Paged<ShipmentRequest> searchAll(OperatorRequestFilter f, PageRequest page) {
        StringBuilder where = new StringBuilder("1 = 1");
        List<Object> params = new ArrayList<>();
        if (f.status() != null) where.append(" and status = ?").append(add(params, f.status()));
        if (f.originCity() != null) where.append(" and originCity = ?").append(add(params, f.originCity()));
        if (f.destinationCity() != null) where.append(" and destinationCity = ?").append(add(params, f.destinationCity()));
        if (f.fromDate() != null) where.append(" and createdAt >= ?").append(add(params, f.fromDate().atStartOfDay(LIMA).toOffsetDateTime()));
        if (f.toDate() != null) where.append(" and createdAt < ?").append(add(params, f.toDate().plusDays(1).atStartOfDay(LIMA).toOffsetDateTime()));
        var query = ShipmentRequestEntity.<ShipmentRequestEntity>find(where + " order by createdAt desc, id", params.toArray())
                .page(page.page(), page.size());
        long total = ShipmentRequestEntity.count(where.toString(), params.toArray());
        return new Paged<>(map(query.list()), page, total);
    }

    @Override
    public Paged<ShipmentRequest> searchMine(UUID orgId, MyRequestFilter f, PageRequest page) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("r.requesterOrgId = ?").append(add(params, orgId));
        if (f.status() != null) where.append(" and r.status = ?").append(add(params, f.status()));
        if (f.originCity() != null) where.append(" and r.originCity = ?").append(add(params, f.originCity()));
        if (f.destinationCity() != null) where.append(" and r.destinationCity = ?").append(add(params, f.destinationCity()));
        if (f.pickupFrom() != null) where.append(" and r.pickupDate >= ?").append(add(params, f.pickupFrom()));
        if (f.pickupTo() != null) where.append(" and r.pickupDate <= ?").append(add(params, f.pickupTo()));
        if (f.withBids()) {
            where.append(" and r.status = ?").append(add(params, ShipmentRequest.Status.PUBLISHED));
            where.append(" and ").append(LIVE_BIDS.formatted(add(params, Bid.Status.WITHDRAWN))).append(" > 0");
        }
        if (f.q() != null) {
            String code = RequestCode.parse(f.q());
            int like = add(params, LikePatterns.contains(f.q()));
            where.append(" and (lower(r.customerReference) like ?").append(like).append(" escape '\\'")
                    .append(" or lower(r.cargoDescription) like ?").append(like).append(" escape '\\'")
                    .append(" or lower(r.code) like ?").append(like).append(" escape '\\'");
            if (code != null) where.append(" or r.code = ?").append(add(params, code));
            List<City> cities = java.util.Arrays.stream(City.values())
                    .filter(c -> c.label.toLowerCase().contains(f.q().toLowerCase())).toList();
            if (!cities.isEmpty()) {
                int p = add(params, cities);
                where.append(" or r.originCity in ?").append(p).append(" or r.destinationCity in ?").append(p);
            }
            where.append(")");
        }
        String order = switch (f.sort()) {
            case RECENT -> " order by r.createdAt desc, r.id";
            case PICKUP -> " order by r.pickupDate asc, r.createdAt desc, r.id";
            case PRICE_DESC -> " order by r.suggestedPrice desc, r.createdAt desc, r.id";
            case PRICE_ASC -> " order by r.suggestedPrice asc, r.createdAt desc, r.id";
            case BIDS -> {
                // solo las publicadas cuentan ofertas (igual que la lista)
                int w = add(params, Bid.Status.WITHDRAWN);
                int pub = add(params, ShipmentRequest.Status.PUBLISHED);
                yield " order by (case when r.status = ?" + pub + " then " + LIVE_BIDS.formatted(w)
                        + " else 0 end) desc, r.createdAt desc, r.id";
            }
        };
        var em = ShipmentRequestEntity.getEntityManager();
        var query = em.createQuery("select r from ShipmentRequestEntity r where " + where + order,
                ShipmentRequestEntity.class);
        var count = em.createQuery("select count(r) from ShipmentRequestEntity r where " + where, Long.class);
        // La cuenta no usa el orden: sus parámetros son los del WHERE (los del ORDER BY van al final).
        int whereParams = countParams(where.toString());
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
            if (i < whereParams) count.setParameter(i + 1, params.get(i));
        }
        query.setFirstResult(page.offset()).setMaxResults(page.size());
        return new Paged<>(query.getResultList().stream().map(ShipmentRequestEntity::toDomain).toList(), page,
                count.getSingleResult());
    }

    /** Cantidad de parámetros posicionales ({@code ?n}) usados en el WHERE: son los primeros, en orden. */
    private static int countParams(String where) {
        var m = java.util.regex.Pattern.compile("\\?(\\d+)").matcher(where);
        int max = 0;
        while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        return max;
    }

    @Override
    public RequestCounts countMine(UUID orgId) {
        var em = ShipmentRequestEntity.getEntityManager();
        long published = ShipmentRequestEntity.count("requesterOrgId = ?1 and status = ?2", orgId, ShipmentRequest.Status.PUBLISHED);
        long matched = ShipmentRequestEntity.count("requesterOrgId = ?1 and status = ?2", orgId, ShipmentRequest.Status.MATCHED);
        long cancelled = ShipmentRequestEntity.count("requesterOrgId = ?1 and status = ?2", orgId, ShipmentRequest.Status.CANCELLED);
        long withBids = em.createQuery("select count(r) from ShipmentRequestEntity r where r.requesterOrgId = ?1 and r.status = ?2 and "
                        + LIVE_BIDS.formatted(3) + " > 0", Long.class)
                .setParameter(1, orgId).setParameter(2, ShipmentRequest.Status.PUBLISHED)
                .setParameter(3, Bid.Status.WITHDRAWN).getSingleResult();
        return new RequestCounts(published + matched + cancelled, published, matched, cancelled, withBids);
    }

    @Override
    public List<ShipmentRequest> findSimilar(UUID orgId, City origin, City destination, CargoType type,
                                             java.math.BigDecimal weightKg, java.time.LocalDate pickupDate,
                                             java.time.OffsetDateTime since, int max) {
        return ShipmentRequestEntity.<ShipmentRequestEntity>find("requesterOrgId = ?1 and status = ?2 and originCity = ?3 "
                        + "and destinationCity = ?4 and cargoType = ?5 and weightKg = ?6 and pickupDate = ?7 and createdAt >= ?8 "
                        + "order by createdAt desc", orgId, ShipmentRequest.Status.PUBLISHED, origin, destination, type,
                        weightKg, pickupDate, since)
                .page(0, max).list().stream().map(ShipmentRequestEntity::toDomain).toList();
    }

    @Override
    public boolean codeExists(String code) {
        // Sistema: un código es único en toda la plataforma y bajo RLS la PYME solo vería los suyos.
        return system.call(() -> ShipmentRequestEntity.count("code", code) > 0);
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
