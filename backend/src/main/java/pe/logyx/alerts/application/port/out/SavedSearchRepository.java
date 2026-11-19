package pe.logyx.alerts.application.port.out;

import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.catalog.domain.City;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SavedSearchRepository {

    // ---- Bajo la RLS de la persona (saved_searches_own)
    List<SavedSearch> listByProfile(UUID profileId);
    Optional<SavedSearch> findOwned(UUID id, UUID profileId);
    long countByProfile(UUID profileId);
    /** Inserta o actualiza. */
    void save(SavedSearch search);
    void delete(UUID id);

    // ---- Solo sistema (matching al publicar, outbox): cruzan organizaciones
    /** Búsquedas activas cuyo origen es {@code origin} o no lo filtran (las demás las filtra {@code SavedSearchMatcher}). */
    List<SavedSearch> activeForOrigin(City origin);
    /** Cuántos avisos envió cada búsqueda desde {@code since}. */
    Map<UUID, Long> alertsSince(Collection<UUID> searchIds, OffsetDateTime since);
    /** Transportistas que la PYME bloqueó (2.9, V21): no se les avisa de sus cargas. Solo sistema (la tabla es de la PYME). */
    java.util.Set<UUID> blockedCarriersOf(UUID requesterOrgId);
    /** Registra el aviso de (búsqueda, solicitud); false si ya existía (idempotencia). */
    boolean recordAlert(UUID searchId, UUID requestId);
}
