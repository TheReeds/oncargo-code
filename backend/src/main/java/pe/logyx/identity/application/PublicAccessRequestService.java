package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.port.out.AccountMailer;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.Duration;
import java.util.List;

/**
 * «Pedir acceso a la organización» desde el registro (roadmap 2.15): quien recibe {@code LOGYX-ORG-002} (RUC ya
 * registrado) pide unirse; el correo va a cada ADMIN activo de esa organización, que decide si lo invita. La respuesta
 * es siempre la misma (no confirma ni niega nada más allá de lo que ya dijo ORG-002). Límites: por IP
 * ({@code AuthRateLimits.beforePublicAccessRequest}) y una solicitud por organización cada
 * {@code logyx.access-request.public-cooldown} (auditoría {@code ORG_ACCESS_REQUESTED}). SystemTx: no hay usuario.
 */
@ApplicationScoped
public class PublicAccessRequestService {

    @Inject OrganizationRepository organizations;
    @Inject ProfileRepository profiles;
    @Inject AccountMailer mailer;
    @Inject AuditService audit;
    @Inject SystemTx system;
    @Inject EntityManager em;

    @ConfigProperty(name = "logyx.access-request.public-cooldown", defaultValue = "PT10M") Duration cooldown;
    @ConfigProperty(name = "logyx.web.url", defaultValue = "https://oncargo.tech") String webUrl;

    public void request(String ruc, String fullName, String email) {
        String name = fullName.strip().replaceAll("\\s+", " ");
        String mail = Profile.normalizeEmail(email);
        var targets = system.call(() -> {
            Organization o = organizations.findByRuc(ruc.strip()).orElse(null);
            if (o == null || o.type() == OrgType.OPERATOR || recentlyRequested(o)) return null;
            List<Profile> admins = profiles.listOrganizationUsers(o.id()).stream()
                    .filter(p -> p.isOrgAdmin() && p.canLogIn() && p.operatorSuspendedAt() == null).toList();
            audit.record(null, o.id(), "ORG_ACCESS_REQUESTED", "organization", o.id(),
                    "desde el registro · " + admins.size() + " ADMIN avisados");
            return new Targets(o, admins);
        });
        if (targets == null) return;
        String link = webUrl.replaceAll("/+$", "") + "/organizacion/usuarios";
        for (Profile admin : targets.admins()) {
            mailer.sendOrganizationAccessRequest(admin.email(), admin.fullName(), targets.org().legalName(), name, mail,
                    link);
        }
    }

    private record Targets(Organization org, List<Profile> admins) {}

    private boolean recentlyRequested(Organization o) {
        Number n = (Number) em.createNativeQuery("SELECT count(*) FROM audit_log WHERE action = 'ORG_ACCESS_REQUESTED' "
                        + "AND entity_id = ?1 AND occurred_at > now() - make_interval(secs => ?2)")
                .setParameter(1, o.id()).setParameter(2, (double) cooldown.toSeconds()).getSingleResult();
        return n.longValue() > 0;
    }
}
