package pe.logyx.marketplace.domain.model;

import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una PYME marca a un transportista como preferido o bloqueado (roadmap 2.9). Es privada de la PYME: el transportista
 * no la lee (la base solo le oculta las solicitudes y le impide ofertar, ver {@code app_is_blocked_by}).
 */
public final class CarrierPreference {

    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private PreferenceKind kind;
    private final UUID createdBy;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public CarrierPreference(UUID requesterOrgId, UUID carrierOrgId, PreferenceKind kind, UUID createdBy,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.kind = kind;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CarrierPreference mark(UUID requesterOrgId, UUID carrierOrgId, PreferenceKind kind, UUID createdBy) {
        if (requesterOrgId.equals(carrierOrgId)) {
            throw BusinessException.badRequest("LOGYX-PRF-001", "No puedes marcar a tu propia organización", "carrierOrgId");
        }
        OffsetDateTime now = OffsetDateTime.now();
        return new CarrierPreference(requesterOrgId, carrierOrgId, kind, createdBy, now, now);
    }

    public void change(PreferenceKind kind) {
        if (this.kind != kind) {
            this.kind = kind;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public boolean isBlocked() { return kind == PreferenceKind.BLOCKED; }
    public boolean isPreferred() { return kind == PreferenceKind.PREFERRED; }

    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public PreferenceKind kind() { return kind; }
    public UUID createdBy() { return createdBy; }
    public OffsetDateTime createdAt() { return createdAt; }
    public OffsetDateTime updatedAt() { return updatedAt; }
}
