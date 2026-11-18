package pe.logyx.operator.application;

import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fila del rastro de auditoría tal como la ve el operador (F05), con el actor y la organización resueltos.
 * Los datos del actor/organización pueden ser {@code null} (acciones del sistema o perfil ya inexistente).
 */
public record AuditRecord(long id, OffsetDateTime occurredAt, UUID actorProfileId, String actorEmail, String actorName,
                       UUID organizationId, String organizationName, OrgType organizationType,
                       String action, String entity, UUID entityId, String detail) {}
