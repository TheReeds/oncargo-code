package pe.logyx.identity.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Persistencia del cambio asistido de razón social o RUC (roadmap 2.15). Las escrituras que cruzan organizaciones
 * (mover personas y flota a la sucesora, copiar la reputación) corren por SystemTx en el servicio.
 */
public interface OrganizationSuccessionStore {

    /** Compromisos vivos que impiden cambiar de RUC (envíos en curso, solicitudes publicadas, ofertas, viajes). */
    List<String> blockingCommitments(UUID organizationId);

    /** Mueve personas (y sus conductores) y vehículos a la sucesora; devuelve las personas movidas. */
    List<UUID> transferMembersAndFleet(UUID fromOrgId, UUID toOrgId);

    void setPredecessor(UUID successorId, UUID predecessorId);

    /** Copia la fila de reputación (RN09: la sucesora hereda el Trust Score con la aprobación del operador). */
    void copyReputation(UUID fromOrgId, UUID toOrgId);

    void renameLegal(UUID organizationId, String legalName);

    record LegalChange(UUID id, UUID organizationId, UUID successorId, String oldLegalName, String newLegalName,
                       String oldRuc, String newRuc, String reason, String documentKey, String documentName,
                       String documentContentType, long documentSizeBytes, String documentSha256, UUID createdBy,
                       OffsetDateTime createdAt) {}

    void insertLegalChange(LegalChange change);

    /** Cambios de la organización (como antecesora o sucesora), recientes primero. Lectura del operador. */
    List<LegalChange> legalChanges(UUID organizationId);

    java.util.Optional<LegalChange> legalChange(UUID id);

    /** Antecesora directa (null si no tiene) y sucesoras directas. */
    UUID predecessorOf(UUID organizationId);

    List<UUID> successorsOf(UUID organizationId);
}
