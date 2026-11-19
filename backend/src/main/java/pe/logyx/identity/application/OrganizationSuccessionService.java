package pe.logyx.identity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.identity.application.event.OrganizationVerificationChanged;
import pe.logyx.identity.application.port.out.OrganizationRepository;
import pe.logyx.identity.application.port.out.OrganizationSuccessionStore;
import pe.logyx.identity.application.port.out.OrganizationSuccessionStore.LegalChange;
import pe.logyx.identity.domain.RucValidator;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cambio asistido de razón social o RUC por el operador (roadmap 2.15), siempre con documento de sustento y motivo.
 * <ul>
 *   <li><b>Misma RUC, otra razón social:</b> se corrige en la misma organización.</li>
 *   <li><b>RUC nuevo:</b> nace una organización <b>sucesora</b> ({@code predecessor_id}) VERIFIED por la aprobación del
 *       operador (RN01); hereda la reputación (RN09: se copia el Trust Score y los recálculos suman las calificaciones de
 *       la antecesora), recibe a las personas y la flota, y la antecesora queda SUSPENDED con el motivo. El historial
 *       (envíos, solicitudes, calificaciones) queda en la antecesora. Exige que no haya compromisos vivos
 *       (LOGYX-ORG-008).</li>
 * </ul>
 * SystemTx justificado en el RUC nuevo: inserta una organización y mueve filas de personas y vehículos entre
 * organizaciones, algo que ninguna transacción de usuario puede bajo RLS.
 */
@ApplicationScoped
public class OrganizationSuccessionService {

    @Inject OrganizationRepository organizations;
    @Inject OrganizationSuccessionStore store;
    @Inject AccountSessions sessions;
    @Inject AuditService audit;
    @Inject TenantContext tenant;
    @Inject SystemTx system;
    @Inject Event<OrganizationVerificationChanged> verificationChanged;

    /** Documento ya guardado por quien llama (lo descarta si esto falla). */
    public record Evidence(String key, String fileName, String contentType, long sizeBytes, String sha256) {}

    /** Resultado: la organización vigente (la misma o la sucesora) y el registro del cambio. */
    public record Outcome(Organization organization, LegalChange change, boolean successorCreated) {}

    @Transactional
    public Outcome change(UUID organizationId, String newLegalName, String newRuc, String reason, Evidence doc) {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
        Organization o = organizations.findById(organizationId)
                .orElseThrow(() -> BusinessException.notFound("Organización"));
        if (o.type() == OrgType.OPERATOR) {
            throw BusinessException.conflict("LOGYX-ORG-005", "La organización operadora no se cambia desde aquí");
        }
        String why = OperatorAccountService.reason(reason);
        String name = newLegalName == null || newLegalName.isBlank() ? o.legalName()
                : ContactInfoPolicy.clean(newLegalName.strip().replaceAll("\\s+", " "), "legalName");
        if (name.length() < 3 || name.length() > 200) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La razón social debe tener entre 3 y 200 caracteres",
                    "legalName");
        }
        String ruc = newRuc == null || newRuc.isBlank() ? o.ruc() : newRuc.strip();
        if (name.equals(o.legalName()) && ruc.equals(o.ruc())) {
            throw BusinessException.badRequest("LOGYX-ORG-009", "Indica una razón social o un RUC distintos",
                    "legalName");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (ruc.equals(o.ruc())) {
            store.renameLegal(o.id(), name);
            LegalChange c = record(o, null, name, ruc, why, doc, now);
            audit.record(tenant.profileId(), tenant.orgId(), "ORGANIZATION_LEGAL_NAME_CHANGED", "organization", o.id(),
                    o.legalName() + " → " + name + " · motivo: " + why);
            return new Outcome(organizations.findById(o.id()).orElse(o), c, false);
        }
        if (!RucValidator.isValid(ruc)) {
            throw BusinessException.badRequest("LOGYX-ORG-001", "RUC inválido", "ruc");
        }
        UUID operatorProfile = tenant.profileId();
        UUID operatorOrg = tenant.orgId();
        Organization successor = system.call(() -> {
            if (organizations.existsByRuc(ruc)) {
                throw BusinessException.conflict("LOGYX-ORG-002", "Ya existe una organización con ese RUC", "ruc");
            }
            List<String> blocking = store.blockingCommitments(o.id());
            if (!blocking.isEmpty()) {
                throw BusinessException.conflict("LOGYX-ORG-008", "Primero cierra o cancela: " + String.join(", ", blocking));
            }
            Organization next = Organization.register(o.type(), name, ruc, o.city(), o.phone());
            next.verify(operatorProfile, "Sucesora de " + o.legalName() + " (RUC " + o.ruc() + ")", now);
            organizations.insert(next);
            store.setPredecessor(next.id(), o.id());
            store.copyReputation(o.id(), next.id());
            List<UUID> moved = store.transferMembersAndFleet(o.id(), next.id());
            Organization old = organizations.findById(o.id()).orElseThrow();
            old.suspend(operatorProfile, "Reemplazada por " + name + " (RUC " + ruc + ")", now);
            organizations.update(old);
            moved.forEach(sessions::revoked);   // su JWT lleva la organización anterior: vuelven a iniciar sesión
            return next;
        });
        LegalChange c = record(o, successor.id(), name, ruc, why, doc, now);
        audit.record(operatorProfile, operatorOrg, "ORGANIZATION_SUCCEEDED", "organization", o.id(),
                "sucesora=" + successor.id() + " RUC " + o.ruc() + " → " + ruc + " · motivo: " + why);
        verificationChanged.fire(new OrganizationVerificationChanged(successor.id(), successor.verificationStatus(),
                successor.verification().note()));
        return new Outcome(successor, c, true);
    }

    @Transactional
    public List<LegalChange> history(UUID organizationId) {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
        return store.legalChanges(organizationId);
    }

    @Transactional
    public LegalChange get(UUID changeId) {
        if (tenant.orgType() != OrgType.OPERATOR) throw BusinessException.forbidden("Solo el operador");
        return store.legalChange(changeId).orElseThrow(() -> BusinessException.notFound("Documento"));
    }

    private LegalChange record(Organization o, UUID successorId, String name, String ruc, String why, Evidence doc,
                               OffsetDateTime now) {
        LegalChange c = new LegalChange(UUID.randomUUID(), o.id(), successorId, o.legalName(), name, o.ruc(), ruc, why,
                doc.key(), doc.fileName(), doc.contentType(), doc.sizeBytes(), doc.sha256(), tenant.profileId(), now);
        store.insertLegalChange(c);
        return c;
    }
}
