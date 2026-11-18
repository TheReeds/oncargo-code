package pe.logyx.identity.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.logyx.identity.domain.model.Organization;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class OrganizationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    public OrgType type;

    @Column(name = "legal_name")
    public String legalName;

    /** CHAR(11) en V1: se declara para que la validación de esquema de Hibernate no lo reporte. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 11)
    public String ruc;

    @Enumerated(EnumType.STRING)
    @Column(name = "ruc_verification")
    public Organization.RucVerification rucVerification;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    public Organization.VerificationStatus verificationStatus;

    @Column(name = "verification_note")
    public String verificationNote;

    @Column(name = "verification_updated_at")
    public OffsetDateTime verificationUpdatedAt;

    @Column(name = "verification_updated_by")
    public UUID verificationUpdatedBy;

    public String city;
    public String phone;
    public String address;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Organization toDomain() {
        return new Organization(id, type, legalName, ruc, rucVerification,
                new Organization.Verification(verificationStatus, verificationNote, verificationUpdatedAt,
                        verificationUpdatedBy), city, phone, address, createdAt);
    }

    static OrganizationEntity from(Organization o) {
        OrganizationEntity e = new OrganizationEntity();
        e.apply(o);
        return e;
    }

    void apply(Organization o) {
        OrganizationEntity e = this;
        e.id = o.id();
        e.type = o.type();
        e.legalName = o.legalName();
        e.ruc = o.ruc();
        e.rucVerification = o.rucVerification();
        e.verificationStatus = o.verification().status();
        e.verificationNote = o.verification().note();
        e.verificationUpdatedAt = o.verification().updatedAt();
        e.verificationUpdatedBy = o.verification().updatedBy();
        e.city = o.city();
        e.phone = o.phone();
        e.address = o.address();
        e.createdAt = o.createdAt();
    }
}
