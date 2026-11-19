package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.OrganizationDirectory;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.marketplace.application.port.out.CarrierPreferenceRepository;
import pe.logyx.marketplace.domain.model.CarrierPreference;
import pe.logyx.marketplace.domain.model.PreferenceKind;
import pe.logyx.reputation.application.ReputationQueries;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transportistas preferidos y bloqueados de la PYME (roadmap 2.9). La organización sale del JWT; la tabla es privada
 * de la PYME (RLS). Bloquear oculta a la PYME del marketplace de ese transportista y le impide ofertar (V21).
 */
@ApplicationScoped
@Transactional
public class CarrierPreferenceService {

    /** Una marca con el perfil público del transportista. */
    public record Entry(CarrierPreference preference, String carrierName, boolean carrierRucVerified,
                        BigDecimal carrierTrustScore) {}

    @Inject CarrierPreferenceRepository prefs;
    @Inject OrganizationDirectory organizations;
    @Inject ReputationQueries reputation;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    public List<Entry> list(PreferenceKind kind) {
        List<CarrierPreference> all = prefs.list(tenant.orgId(), kind);
        Set<UUID> ids = all.stream().map(CarrierPreference::carrierOrgId).collect(Collectors.toSet());
        Map<UUID, Organization> orgs = organizations.byIds(ids);
        Map<UUID, BigDecimal> trust = reputation.trustScores(ids);
        return all.stream().filter(p -> orgs.containsKey(p.carrierOrgId())).map(p -> {
            Organization o = orgs.get(p.carrierOrgId());
            return new Entry(p, o.legalName(), o.rucVerified(), trust.get(p.carrierOrgId()));
        }).toList();
    }

    /** Marca o cambia la marca de un transportista. Idempotente. */
    public Entry mark(UUID carrierOrgId, PreferenceKind kind) {
        Organization carrier = organizations.get(carrierOrgId);
        if (carrier.type() != OrgType.CARRIER) {
            throw BusinessException.badRequest("LOGYX-PRF-002", "Solo puedes marcar transportistas", "carrierOrgId");
        }
        CarrierPreference p = prefs.find(tenant.orgId(), carrierOrgId)
                .map(existing -> { existing.change(kind); return existing; })
                .orElseGet(() -> CarrierPreference.mark(tenant.orgId(), carrierOrgId, kind, tenant.profileId()));
        prefs.save(p);
        audit.record(tenant.profileId(), tenant.orgId(), kind == PreferenceKind.BLOCKED ? "CARRIER_BLOCKED"
                : "CARRIER_PREFERRED", "organization", carrierOrgId, null);
        return new Entry(p, carrier.legalName(), carrier.rucVerified(),
                reputation.trustScores(Set.of(carrierOrgId)).get(carrierOrgId));
    }

    /** Quita la marca. Idempotente: sin marca no hace nada. */
    public void unmark(UUID carrierOrgId) {
        if (prefs.find(tenant.orgId(), carrierOrgId).isPresent()) {
            prefs.delete(tenant.orgId(), carrierOrgId);
            audit.record(tenant.profileId(), tenant.orgId(), "CARRIER_UNMARKED", "organization", carrierOrgId, null);
        }
    }

    /** Ids de las organizaciones con esa marca (de la PYME actual). */
    public Set<UUID> ids(PreferenceKind kind) {
        return prefs.list(tenant.orgId(), kind).stream().map(CarrierPreference::carrierOrgId)
                .collect(Collectors.toSet());
    }

    public boolean isBlocked(UUID carrierOrgId) {
        return prefs.find(tenant.orgId(), carrierOrgId).map(CarrierPreference::isBlocked).orElse(false);
    }
}
