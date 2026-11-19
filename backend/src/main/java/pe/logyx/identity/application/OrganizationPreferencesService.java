package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.port.out.OrganizationPreferencesRepository;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.OrganizationPreferencesPolicy;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.OrganizationPreferences;
import pe.logyx.identity.domain.model.OrganizationPreferences.InterestRoute;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AccessDenied;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Preferencias de la organización (V24, 2.2): las lee cualquiera de la organización (también Lector) y el operador; las
 * escribe solo su ADMIN. Bajo RLS ({@code org_preferences_own}, {@code org_preferences_operator_read}). Los demás
 * módulos las leen por aquí: {@code alerts} (rutas de interés del transportista, en el despacho del outbox) y
 * {@code settlement} lee {@code payment_terms_days} con su propio SQL.
 */
@ApplicationScoped
public class OrganizationPreferencesService {

    public record UpdateCommand(List<City> cities, List<InterestRoute> routes, List<CargoType> cargoTypes,
                                Boolean routeAlerts, BigDecimal minPricePerKm, LocalTime defaultPickupFrom,
                                LocalTime defaultPickupTo, Integer paymentTermsDays) {}

    @Inject OrganizationPreferencesRepository preferences;
    @Inject OrganizationRepository organizations;
    @Inject TenantContext tenant;
    @Inject AuditService audit;

    @Transactional
    public OrganizationPreferences mine() {
        UUID org = tenant.orgId();
        return preferences.find(org).orElseGet(() -> OrganizationPreferences.empty(org));
    }

    /** Operador: las de cualquier organización (PYME o transportista). 404 si no existe. */
    @Transactional
    public OrganizationPreferences of(UUID organizationId) {
        Organization o = organizations.findById(organizationId).orElseThrow(() -> BusinessException.notFound("Organización"));
        return preferences.find(o.id()).orElseGet(() -> OrganizationPreferences.empty(o.id()));
    }

    /** Solo el ADMIN ({@code LOGYX-AUTH-008}). Reemplaza todo (lo que no aplica al tipo de organización se ignora). */
    @Transactional
    public OrganizationPreferences update(UpdateCommand cmd) {
        AccessDenied.requireOrgAdmin(tenant);
        OrgType type = tenant.orgType();
        OrganizationPreferences next = OrganizationPreferencesPolicy.normalize(tenant.orgId(), type, cmd.cities(),
                cmd.routes(), cmd.cargoTypes(), cmd.routeAlerts(), cmd.minPricePerKm(), cmd.defaultPickupFrom(),
                cmd.defaultPickupTo(), cmd.paymentTermsDays(), tenant.profileId(), OffsetDateTime.now());
        preferences.upsert(next);
        audit.record(tenant.profileId(), tenant.orgId(), "ORGANIZATION_PREFERENCES_UPDATED", "organization", tenant.orgId(),
                next.routes().size() + " rutas, " + next.cities().size() + " ciudades");
        return next;
    }

    /**
     * Transportistas que quieren alertas de cargas {@code origin → destination} por sus rutas de interés (2.2 + 2.3).
     * Solo dentro del despacho del outbox (SystemTx): cruza organizaciones.
     */
    public List<OrganizationPreferences> carriersWatchingRoute(City origin, City destination) {
        return preferences.carriersWithRouteAlert(origin, destination);
    }
}
