package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.marketplace.domain.model.CarrierPreference;
import pe.logyx.marketplace.domain.model.PreferenceKind;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "carrier_preferences")
@IdClass(CarrierPreferenceEntity.Key.class)
public class CarrierPreferenceEntity extends PanacheEntityBase {

    public static class Key implements Serializable {
        public UUID requesterOrgId;
        public UUID carrierOrgId;

        public Key() {}
        public Key(UUID requesterOrgId, UUID carrierOrgId) {
            this.requesterOrgId = requesterOrgId;
            this.carrierOrgId = carrierOrgId;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Key k && java.util.Objects.equals(requesterOrgId, k.requesterOrgId)
                    && java.util.Objects.equals(carrierOrgId, k.carrierOrgId);
        }

        @Override public int hashCode() { return java.util.Objects.hash(requesterOrgId, carrierOrgId); }
    }

    @Id
    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Id
    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Enumerated(EnumType.STRING)
    public PreferenceKind kind;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    CarrierPreference toDomain() {
        return new CarrierPreference(requesterOrgId, carrierOrgId, kind, createdBy, createdAt, updatedAt);
    }

    void apply(CarrierPreference p) {
        requesterOrgId = p.requesterOrgId();
        carrierOrgId = p.carrierOrgId();
        kind = p.kind();
        createdBy = p.createdBy();
        createdAt = p.createdAt();
        updatedAt = p.updatedAt();
    }
}
