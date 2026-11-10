package pe.logyx.shared.audit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

/** Registro de actividades de tratamiento y acciones críticas (Ley N° 29733). */
@ApplicationScoped
public class AuditService {

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(UUID actor, UUID org, String action, String entity, UUID entityId, String detail) {
        AuditEntry e = new AuditEntry();
        e.actorProfileId = actor;
        e.organizationId = org;
        e.action = action;
        e.entity = entity;
        e.entityId = entityId;
        e.detail = detail;
        e.persist();
    }
}
