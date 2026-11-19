package pe.logyx.places.application.port.out;

import pe.logyx.catalog.domain.City;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPlaceRepository {
    Optional<SavedPlace> findById(UUID id);

    /** De la organización, la usada más recientemente primero (las nunca usadas, por nombre). Tope de {@code max}. */
    List<SavedPlace> search(UUID organizationId, City city, PlaceKind kind, String q, boolean includeArchived, int max);

    void save(SavedPlace place);

    /** Marca como usadas ahora (solo de la organización dada). */
    void touch(UUID organizationId, Collection<UUID> ids, OffsetDateTime at);
}
