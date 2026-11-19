package pe.logyx.identity.application.port.out;

import pe.logyx.catalog.domain.City;
import pe.logyx.identity.domain.model.OrganizationPreferences;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code organization_preferences} (V24). Bajo RLS: la propia organización (y el operador lee). */
public interface OrganizationPreferencesRepository {
    Optional<OrganizationPreferences> find(UUID organizationId);
    void upsert(OrganizationPreferences preferences);
    /**
     * Solo sistema (despacho del outbox): transportistas con alertas por rutas activadas y alguna ruta {@code origin →
     * destination} declarada.
     */
    List<OrganizationPreferences> carriersWithRouteAlert(City origin, City destination);
}
