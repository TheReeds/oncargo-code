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

    public String city;
    public String phone;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    Organization toDomain() {
        return new Organization(id, type, legalName, ruc, rucVerification, city, phone, createdAt);
    }

    static OrganizationEntity from(Organization o) {
        OrganizationEntity e = new OrganizationEntity();
        e.id = o.id();
        e.type = o.type();
        e.legalName = o.legalName();
        e.ruc = o.ruc();
        e.rucVerification = o.rucVerification();
        e.city = o.city();
        e.phone = o.phone();
        e.createdAt = o.createdAt();
        return e;
    }
}
