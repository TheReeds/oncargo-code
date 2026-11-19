package pe.logyx.identity.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "terms_acceptances")
public class TermsAcceptanceEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "profile_id", nullable = false)
    public UUID profileId;

    @Column(name = "organization_id", nullable = false)
    public UUID organizationId;

    @Column(name = "terms_version", nullable = false)
    public String termsVersion;

    @Column(name = "accepted_at", nullable = false)
    public OffsetDateTime acceptedAt;

    @Column(nullable = false)
    public String source = "REGISTER";
}
