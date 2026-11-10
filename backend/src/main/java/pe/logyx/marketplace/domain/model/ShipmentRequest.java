package pe.logyx.marketplace.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Solicitud de flete publicada por una PYME en el marketplace. */
public final class ShipmentRequest {

    public enum Status { PUBLISHED, MATCHED, CANCELLED }

    /** Ruta, carga y fecha tal como las pide la PYME. */
    public record Cargo(City originCity, String originAddress, City destinationCity, String destinationAddress,
                        CargoType cargoType, String cargoDescription, BigDecimal weightKg, BigDecimal volumeM3,
                        LocalDate pickupDate) {}

    /** Precio calculado por el motor de costos al publicar (piso = RN03). */
    public record Pricing(BigDecimal distanceKm, BigDecimal suggestedPrice, BigDecimal priceFloor) {}

    private final UUID id;
    private final UUID requesterOrgId;
    private final UUID createdBy;
    private final Cargo cargo;
    private final Pricing pricing;
    private Status status;
    private UUID assignedCarrierOrgId;
    private final OffsetDateTime createdAt;

    public ShipmentRequest(UUID id, UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing,
                           Status status, UUID assignedCarrierOrgId, OffsetDateTime createdAt) {
        this.id = id;
        this.requesterOrgId = requesterOrgId;
        this.createdBy = createdBy;
        this.cargo = cargo;
        this.pricing = pricing;
        this.status = status;
        this.assignedCarrierOrgId = assignedCarrierOrgId;
        this.createdAt = createdAt;
    }

    /** RN01: solo PYMEs con RUC verificado publican. */
    public static void ensureRequesterCanPublish(boolean requesterRucVerified) {
        if (!requesterRucVerified) {
            throw BusinessException.forbidden("Su organización debe tener el RUC verificado para publicar (RN01)");
        }
    }

    public static ShipmentRequest publish(UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing) {
        Cargo clean = new Cargo(cargo.originCity(), cargo.originAddress().trim(), cargo.destinationCity(),
                cargo.destinationAddress().trim(), cargo.cargoType(), cargo.cargoDescription().trim(),
                cargo.weightKg(), cargo.volumeM3(), cargo.pickupDate());
        return new ShipmentRequest(UUID.randomUUID(), requesterOrgId, createdBy, clean, pricing,
                Status.PUBLISHED, null, OffsetDateTime.now());
    }

    public boolean isPublished() { return status == Status.PUBLISHED; }

    /** "Lima → Juliaca", para textos de notificaciones. */
    public String routeLabel() { return cargo.originCity().label + " → " + cargo.destinationCity().label; }

    public boolean isOwnedBy(UUID orgId) { return requesterOrgId.equals(orgId); }

    public void ensureOpenForBids() {
        if (status != Status.PUBLISHED) {
            throw BusinessException.conflict("LOGYX-AUC-001", "La subasta no está abierta");
        }
    }

    /** RN05: la solicitud queda adjudicada al transportista de la oferta aceptada. */
    public void matchTo(UUID carrierOrgId) {
        status = Status.MATCHED;
        assignedCarrierOrgId = carrierOrgId;
    }

    public void cancel() {
        if (status != Status.PUBLISHED) {
            throw BusinessException.conflict("LOGYX-REQ-002", "Solo se puede cancelar una solicitud publicada");
        }
        status = Status.CANCELLED;
    }

    public UUID id() { return id; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID createdBy() { return createdBy; }
    public Cargo cargo() { return cargo; }
    public Pricing pricing() { return pricing; }
    public Status status() { return status; }
    public UUID assignedCarrierOrgId() { return assignedCarrierOrgId; }
    public OffsetDateTime createdAt() { return createdAt; }
}
