package pe.logyx.fleet.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Conductor de un transportista. Su cuenta de usuario (perfil) vive en identity. */
public final class Driver {

    public enum Status { AVAILABLE, ON_DUTY, OFF_DUTY }

    private final UUID id;
    private final UUID organizationId;
    private final UUID profileId;
    private final String licenseNumber;
    private Status status;
    private final OffsetDateTime createdAt;
    /** Categoría y vencimiento de la licencia, y DNI (V22, 2.4). Opcionales: los completa el transportista. */
    private String licenseCategory;
    private LocalDate licenseExpiresAt;
    private String dni;

    public Driver(UUID id, UUID organizationId, UUID profileId, String licenseNumber, Status status,
                  OffsetDateTime createdAt, String licenseCategory, LocalDate licenseExpiresAt, String dni) {
        this(id, organizationId, profileId, licenseNumber, status, createdAt);
        this.licenseCategory = licenseCategory;
        this.licenseExpiresAt = licenseExpiresAt;
        this.dni = dni;
    }

    public Driver(UUID id, UUID organizationId, UUID profileId, String licenseNumber, Status status,
                  OffsetDateTime createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.profileId = profileId;
        this.licenseNumber = licenseNumber;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Driver hire(UUID organizationId, UUID profileId, String licenseNumber) {
        return new Driver(UUID.randomUUID(), organizationId, profileId, licenseNumber.trim().toUpperCase(),
                Status.AVAILABLE, OffsetDateTime.now());
    }

    /** Datos del conductor que el transportista completa (todos opcionales). */
    public void updateCredentials(String category, LocalDate expiresAt, String dniNumber) {
        this.licenseCategory = category == null || category.isBlank() ? null : category.trim().toUpperCase();
        this.licenseExpiresAt = expiresAt;
        this.dni = dniNumber == null || dniNumber.isBlank() ? null : dniNumber.trim();
    }

    public void goOnDuty() { status = Status.ON_DUTY; }

    public void release() { status = Status.AVAILABLE; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID profileId() { return profileId; }
    public String licenseNumber() { return licenseNumber; }
    public Status status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
    public String licenseCategory() { return licenseCategory; }
    public LocalDate licenseExpiresAt() { return licenseExpiresAt; }
    public String dni() { return dni; }
}
