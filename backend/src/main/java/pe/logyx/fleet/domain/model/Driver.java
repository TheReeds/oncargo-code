package pe.logyx.fleet.domain.model;

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

    public void goOnDuty() { status = Status.ON_DUTY; }

    public void release() { status = Status.AVAILABLE; }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public UUID profileId() { return profileId; }
    public String licenseNumber() { return licenseNumber; }
    public Status status() { return status; }
    public OffsetDateTime createdAt() { return createdAt; }
}
