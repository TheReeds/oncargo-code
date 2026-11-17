package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.domain.BusinessException;

import java.util.UUID;

/**
 * RN01 para todos los módulos: una organización solo asume compromisos nuevos (publicar una solicitud, ofertar,
 * reservar o publicar un viaje, ser asignada por el operador) si está verificada por LOGYX y no suspendida.
 * {@code logyx.verification.required} (true en prod y test; false por omisión en dev para la demo) decide si la
 * verificación es obligatoria; la suspensión bloquea siempre. Lee la organización en cada llamada: el JWT no
 * lleva el estado, así que una suspensión aplica de inmediato.
 */
@ApplicationScoped
public class OrganizationGuard {

    @Inject OrganizationRepository organizations;

    @ConfigProperty(name = "logyx.verification.required", defaultValue = "true")
    boolean verificationRequired;

    @Transactional(Transactional.TxType.MANDATORY)
    public Organization ensureCanTransact(UUID orgId) {
        Organization org = organizations.findById(orgId).orElseThrow(() -> BusinessException.notFound("Organización"));
        org.ensureCanTransact(verificationRequired);
        return org;
    }

    /** Igual que {@link #ensureCanTransact} pero sin lanzar (candidatos del operador, asignación manual). */
    @Transactional(Transactional.TxType.MANDATORY)
    public boolean canTransact(UUID orgId) {
        return organizations.findById(orgId).map(this::canTransact).orElse(false);
    }

    public boolean canTransact(Organization o) {
        return !o.isSuspended() && (!verificationRequired || o.rucVerified());
    }

    public boolean verificationRequired() { return verificationRequired; }
}
