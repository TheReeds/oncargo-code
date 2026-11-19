package pe.logyx.places.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.catalog.domain.City;
import pe.logyx.places.application.port.out.SavedPlaceRepository;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;
import pe.logyx.shared.domain.LikePatterns;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheSavedPlaceRepository implements SavedPlaceRepository {

    @Override
    public Optional<SavedPlace> findById(UUID id) {
        return SavedPlaceEntity.<SavedPlaceEntity>findByIdOptional(id).map(SavedPlaceEntity::toDomain);
    }

    @Override
    public List<SavedPlace> search(UUID organizationId, City city, PlaceKind kind, String q, boolean includeArchived,
                                   int max) {
        StringBuilder where = new StringBuilder("organizationId = ?1");
        List<Object> params = new ArrayList<>(List.of(organizationId));
        if (!includeArchived) where.append(" and archivedAt is null");
        if (city != null) where.append(" and city = ?").append(add(params, city));
        if (kind != null && kind != PlaceKind.BOTH) {
            where.append(" and kind in (?").append(add(params, kind)).append(", ?").append(add(params, PlaceKind.BOTH)).append(")");
        }
        if (q != null) {
            int p = add(params, LikePatterns.contains(q));
            where.append(" and (lower(label) like ?").append(p).append(" escape '\\' or lower(address) like ?")
                    .append(p).append(" escape '\\' or lower(contactName) like ?").append(p).append(" escape '\\')");
        }
        return SavedPlaceEntity.<SavedPlaceEntity>find(
                        where + " order by archivedAt nulls first, lastUsedAt desc nulls last, label, id", params.toArray())
                .page(0, max).list().stream().map(SavedPlaceEntity::toDomain).toList();
    }

    @Override
    public void save(SavedPlace place) {
        SavedPlaceEntity e = SavedPlaceEntity.findById(place.id());
        if (e == null) {
            e = new SavedPlaceEntity();
            e.apply(place);
            e.persist();
        } else {
            e.apply(place);
        }
    }

    @Override
    public void touch(UUID organizationId, Collection<UUID> ids, OffsetDateTime at) {
        SavedPlaceEntity.update("lastUsedAt = ?1 where organizationId = ?2 and id in ?3", at, organizationId, ids);
    }

    private static int add(List<Object> params, Object value) {
        params.add(value);
        return params.size();
    }
}
