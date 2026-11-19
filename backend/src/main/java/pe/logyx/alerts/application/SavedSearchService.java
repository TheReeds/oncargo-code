package pe.logyx.alerts.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.alerts.application.port.out.SavedSearchRepository;
import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Búsquedas guardadas de la persona que inició sesión (2.3). Bajo RLS: solo ve y cambia las suyas. */
@ApplicationScoped
public class SavedSearchService {

    @Inject SavedSearchRepository searches;
    @Inject FleetService fleet;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "logyx.alerts.max-saved-searches-per-person", defaultValue = "20")
    int maxPerPerson;

    @Transactional
    public List<SavedSearch> mine() {
        return searches.listByProfile(tenant.profileId());
    }

    @Transactional
    public SavedSearch create(SavedSearch.Filters filters) {
        UUID me = tenant.profileId();
        if (searches.countByProfile(me) >= maxPerPerson) {
            throw BusinessException.conflict("LOGYX-ALT-002",
                    "Ya tienes " + maxPerPerson + " búsquedas guardadas: borra alguna para crear otra");
        }
        ensureVehicleIsMine(filters.vehicleId());
        SavedSearch s = SavedSearch.create(tenant.orgId(), me, filters, OffsetDateTime.now());
        searches.save(s);
        audit.record(me, tenant.orgId(), "SAVED_SEARCH_CREATED", "saved_search", s.id(), s.name());
        return s;
    }

    @Transactional
    public SavedSearch update(UUID id, SavedSearch.Filters filters, Boolean active) {
        SavedSearch s = owned(id);
        ensureVehicleIsMine(filters.vehicleId());
        OffsetDateTime now = OffsetDateTime.now();
        s.apply(filters, now);
        if (active != null) s.setActive(active, now);
        searches.save(s);
        return s;
    }

    @Transactional
    public void delete(UUID id) {
        SavedSearch s = owned(id);
        searches.delete(s.id());
        audit.record(tenant.profileId(), tenant.orgId(), "SAVED_SEARCH_DELETED", "saved_search", s.id(), s.name());
    }

    private SavedSearch owned(UUID id) {
        return searches.findOwned(id, tenant.profileId()).orElseThrow(() -> BusinessException.notFound("Búsqueda"));
    }

    private void ensureVehicleIsMine(UUID vehicleId) {
        if (vehicleId != null) fleet.ownVehicle(vehicleId);                       // 404 si no es de mi flota
    }
}
