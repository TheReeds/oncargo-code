package pe.logyx.trip.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.trip.application.port.out.SmartLoadRepository;
import pe.logyx.trip.domain.model.SmartLoadStatus;
import pe.logyx.trip.domain.model.SmartLoadSuggestion;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheSmartLoadRepository implements SmartLoadRepository {

    @Override
    public Optional<SmartLoadSuggestion> findOwned(UUID id, UUID carrierOrgId) {
        return SmartLoadSuggestionEntity.<SmartLoadSuggestionEntity>find("id = ?1 and carrierOrgId = ?2", id, carrierOrgId)
                .firstResultOptional().map(e -> withItems(List.of(e)).getFirst());
    }

    @Override
    public List<SmartLoadSuggestion> listByCarrier(UUID carrierOrgId, SmartLoadStatus status) {
        List<SmartLoadSuggestionEntity> list = status == null
                ? SmartLoadSuggestionEntity.list("carrierOrgId = ?1 order by createdAt desc, id", carrierOrgId)
                : SmartLoadSuggestionEntity.list("carrierOrgId = ?1 and status = ?2 order by createdAt desc, id",
                        carrierOrgId, status);
        return withItems(list);
    }

    @Override
    public List<SmartLoadSuggestion> listPendingExpiredBefore(OffsetDateTime now) {
        return withItems(SmartLoadSuggestionEntity.list("status = ?1 and expiresAt <= ?2", SmartLoadStatus.PENDING, now));
    }

    @Override
    public long countPending(UUID carrierOrgId, OffsetDateTime now) {
        return SmartLoadSuggestionEntity.count("carrierOrgId = ?1 and status = ?2 and expiresAt > ?3", carrierOrgId,
                SmartLoadStatus.PENDING, now);
    }

    @Override
    public void save(SmartLoadSuggestion s) {
        SmartLoadSuggestionEntity e = SmartLoadSuggestionEntity.findById(s.id());
        if (e == null) {
            e = new SmartLoadSuggestionEntity();
            e.apply(s);
            e.persist();
            s.items().forEach(i -> SmartLoadItemEntity.of(s, i).persist());   // los ítems no cambian después
        } else {
            e.apply(s);
        }
    }

    private static List<SmartLoadSuggestion> withItems(List<SmartLoadSuggestionEntity> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<SmartLoadSuggestion.Item>> items = SmartLoadItemEntity
                .<SmartLoadItemEntity>list("suggestionId in ?1 order by pickupOrder",
                        list.stream().map(e -> e.id).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.suggestionId,
                        Collectors.mapping(SmartLoadItemEntity::toDomain, Collectors.toList())));
        return list.stream().map(e -> e.toDomain(items.getOrDefault(e.id, List.of()))).toList();
    }
}
