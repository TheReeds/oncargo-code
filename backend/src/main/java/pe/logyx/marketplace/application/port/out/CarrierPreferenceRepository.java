package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.model.CarrierPreference;
import pe.logyx.marketplace.domain.model.PreferenceKind;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Preferidos y bloqueados de una PYME. Corre bajo la RLS de la PYME (el transportista no lee esta tabla). */
public interface CarrierPreferenceRepository {
    Optional<CarrierPreference> find(UUID requesterOrgId, UUID carrierOrgId);
    /** {@code kind} nulo = todos. Más recientes primero. */
    List<CarrierPreference> list(UUID requesterOrgId, PreferenceKind kind);
    void save(CarrierPreference preference);
    void delete(UUID requesterOrgId, UUID carrierOrgId);
}
