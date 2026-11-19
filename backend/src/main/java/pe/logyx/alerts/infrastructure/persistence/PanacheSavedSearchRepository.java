package pe.logyx.alerts.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.alerts.application.port.out.SavedSearchRepository;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.catalog.domain.City;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheSavedSearchRepository implements SavedSearchRepository {

    @Inject EntityManager em;

    @Override
    public List<SavedSearch> listByProfile(UUID profileId) {
        return SavedSearchEntity.<SavedSearchEntity>list("profileId = ?1 order by createdAt desc, id", profileId)
                .stream().map(SavedSearchEntity::toDomain).toList();
    }

    @Override
    public Optional<SavedSearch> findOwned(UUID id, UUID profileId) {
        return SavedSearchEntity.<SavedSearchEntity>find("id = ?1 and profileId = ?2", id, profileId)
                .firstResultOptional().map(SavedSearchEntity::toDomain);
    }

    @Override
    public long countByProfile(UUID profileId) {
        return SavedSearchEntity.count("profileId", profileId);
    }

    @Override
    public void save(SavedSearch search) {
        SavedSearchEntity e = SavedSearchEntity.findById(search.id());
        if (e == null) {
            e = new SavedSearchEntity();
            e.apply(search);
            e.persist();
        } else {
            e.apply(search);
        }
    }

    @Override
    public void delete(UUID id) {
        SavedSearchEntity.deleteById(id);
    }

    @Override
    public List<SavedSearch> activeForOrigin(City origin) {
        return SavedSearchEntity.<SavedSearchEntity>list("active = true and (originCity is null or originCity = ?1)", origin)
                .stream().map(SavedSearchEntity::toDomain).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<UUID, Long> alertsSince(Collection<UUID> searchIds, OffsetDateTime since) {
        if (searchIds.isEmpty()) return Map.of();
        List<Object[]> rows = em.createNativeQuery("SELECT search_id, count(*) FROM saved_search_alerts "
                        + "WHERE search_id IN (:ids) AND created_at >= :since GROUP BY search_id")
                .setParameter("ids", searchIds).setParameter("since", since).getResultList();
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put((UUID) r[0], ((Number) r[1]).longValue()));
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Set<UUID> blockedCarriersOf(UUID requesterOrgId) {
        List<Object> rows = em.createNativeQuery("SELECT carrier_org_id FROM carrier_preferences "
                + "WHERE requester_org_id = :r AND kind = 'BLOCKED'").setParameter("r", requesterOrgId).getResultList();
        return rows.stream().map(UUID.class::cast).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean recordAlert(UUID searchId, UUID requestId) {
        return em.createNativeQuery("INSERT INTO saved_search_alerts (search_id, request_id) VALUES (:s, :r) "
                        + "ON CONFLICT DO NOTHING").setParameter("s", searchId).setParameter("r", requestId)
                .executeUpdate() > 0;
    }
}
