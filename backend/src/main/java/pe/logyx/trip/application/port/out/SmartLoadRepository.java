package pe.logyx.trip.application.port.out;

import pe.logyx.trip.domain.model.SmartLoadStatus;
import pe.logyx.trip.domain.model.SmartLoadSuggestion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmartLoadRepository {
    Optional<SmartLoadSuggestion> findOwned(UUID id, UUID carrierOrgId);
    /** Más recientes primero; {@code status} null = todas. */
    List<SmartLoadSuggestion> listByCarrier(UUID carrierOrgId, SmartLoadStatus status);
    /** PENDING de cualquier transportista visible, vencidas antes de {@code now} (mantenimiento programado). */
    List<SmartLoadSuggestion> listPendingExpiredBefore(OffsetDateTime now);
    long countPending(UUID carrierOrgId, OffsetDateTime now);
    /** Inserta o actualiza (con sus ítems). */
    void save(SmartLoadSuggestion suggestion);
}
