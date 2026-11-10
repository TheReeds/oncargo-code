package pe.logyx.organization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.shared.tenancy.OrgType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization extends PanacheEntityBase {

    public enum RucVerification { NONE, CHECKSUM, SUNAT }

    @Id
    @GeneratedValue
    public UUID id;

    @Enumerated(EnumType.STRING)
    public OrgType type;

    @Column(name = "legal_name")
    public String legalName;

    public String ruc;

    @Enumerated(EnumType.STRING)
    @Column(name = "ruc_verification")
    public RucVerification rucVerification = RucVerification.NONE;

    public String city;
    public String phone;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public boolean rucVerified() {
        return rucVerification != RucVerification.NONE;
    }

    public static boolean existsByRuc(String ruc) {
        return count("ruc", ruc) > 0;
    }
}
