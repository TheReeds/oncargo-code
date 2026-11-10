package pe.logyx.organization;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Usuario de la plataforma; siempre pertenece a una organización. */
@Entity
@Table(name = "profiles")
public class Profile extends PanacheEntityBase {

    public enum Role { ADMIN, OPS, VIEWER, DRIVER }

    @Id
    @GeneratedValue
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id")
    public Organization organization;

    public String email;

    @Column(name = "password_hash")
    public String passwordHash;

    @Column(name = "full_name")
    public String fullName;

    @Enumerated(EnumType.STRING)
    public Role role;

    public boolean active = true;

    @Column(name = "consent_accepted_at")
    public OffsetDateTime consentAcceptedAt;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();

    public static Optional<Profile> findByEmail(String email) {
        return find("lower(email) = ?1", email.toLowerCase()).firstResultOptional();
    }

    public static boolean emailTaken(String email) {
        return count("lower(email) = ?1", email.toLowerCase()) > 0;
    }
}
