package pe.logyx.fleet;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.organization.Profile;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver extends PanacheEntityBase {

    public enum Status { AVAILABLE, ON_DUTY, OFF_DUTY }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    public Profile profile;

    @Column(name = "license_number")
    public String licenseNumber;

    @Enumerated(EnumType.STRING)
    public Status status = Status.AVAILABLE;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static Optional<Driver> findByProfile(UUID profileId) {
        return find("profile.id", profileId).firstResultOptional();
    }
}
