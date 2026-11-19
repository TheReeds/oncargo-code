package pe.logyx.fleet.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.fleet.domain.model.Driver;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class DriverEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    /** FK a profiles (identity); sin relación JPA para no acoplar módulos. */
    @Column(name = "profile_id")
    public UUID profileId;

    @Column(name = "license_number")
    public String licenseNumber;

    @Enumerated(EnumType.STRING)
    public Driver.Status status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "license_category")
    public String licenseCategory;

    @Column(name = "license_expires_at")
    public java.time.LocalDate licenseExpiresAt;

    public String dni;

    Driver toDomain() {
        return new Driver(id, organizationId, profileId, licenseNumber, status, createdAt, licenseCategory,
                licenseExpiresAt, dni);
    }

    void apply(Driver d) {
        id = d.id();
        organizationId = d.organizationId();
        profileId = d.profileId();
        licenseNumber = d.licenseNumber();
        status = d.status();
        createdAt = d.createdAt();
        licenseCategory = d.licenseCategory();
        licenseExpiresAt = d.licenseExpiresAt();
        dni = d.dni();
    }
}
