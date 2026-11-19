package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.port.out.ProfileRepository;
import pe.logyx.identity.application.port.out.TermsAcceptanceRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;

/**
 * Aceptación de la versión vigente de los Términos y condiciones (V24, roadmap 2.17). Cuando cambia
 * {@code logyx.terms.version}, {@code GET /auth/me} trae {@code termsAccepted = false} y la web pide aceptarla (sin
 * bloquear la API). Aceptar inserta {@code source = 'UPDATE'} bajo la RLS de la persona ({@code terms_own_insert}).
 */
@ApplicationScoped
public class TermsService {

    @Inject LegalTerms legalTerms;
    @Inject TermsAcceptanceRepository acceptances;
    @Inject ProfileRepository profiles;
    @Inject TenantContext tenant;
    @Inject AuditService audit;

    /** Arma la cuenta con el estado de los términos. Requiere una transacción activa (de usuario o de sistema). */
    public UserAccount account(Profile p, Organization org) {
        return new UserAccount(p, org, acceptances.hasAccepted(p.id(), legalTerms.version()));
    }

    /**
     * La persona actual acepta {@code version} (debe ser la vigente: 409 {@code LOGYX-AUTH-012}). Idempotente: si ya la
     * había aceptado no se repite la fila.
     */
    @Transactional
    public String accept(String version) {
        String current = legalTerms.ensureCurrent(version);
        Profile p = profiles.findById(tenant.profileId()).orElseThrow(() -> BusinessException.notFound("Usuario"));
        if (!acceptances.hasAccepted(p.id(), current)) {
            acceptances.insert(p.id(), p.organizationId(), current, OffsetDateTime.now(), "UPDATE");
            audit.record(p.id(), p.organizationId(), "TERMS_ACCEPTED", "profile", p.id(), "términos " + current);
        }
        return current;
    }

    public String currentVersion() { return legalTerms.version(); }
}
