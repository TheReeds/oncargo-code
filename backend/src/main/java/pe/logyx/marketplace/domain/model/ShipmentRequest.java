package pe.logyx.marketplace.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.ContactInfoPolicy;

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
    /** Cambia solo la fecha de recojo al reabrir la subasta (F07). */
    private Cargo cargo;
    private final Pricing pricing;
    private Status status;
    private UUID assignedCarrierOrgId;
    private final OffsetDateTime createdAt;
    /** Motivo de cancelación (PYME, o el del envío cancelado tras adjudicar). */
    private String cancelReason;
    /** Solicitud cancelada de la que se republicó esta (F07, F06). */
    private final UUID republishedFrom;

    public ShipmentRequest(UUID id, UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing,
                           Status status, UUID assignedCarrierOrgId, OffsetDateTime createdAt) {
        this(id, requesterOrgId, createdBy, cargo, pricing, status, assignedCarrierOrgId, createdAt, null, null);
    }

    public ShipmentRequest(UUID id, UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing,
                           Status status, UUID assignedCarrierOrgId, OffsetDateTime createdAt, String cancelReason,
                           UUID republishedFrom) {
        this.id = id;
        this.requesterOrgId = requesterOrgId;
        this.createdBy = createdBy;
        this.cargo = cargo;
        this.pricing = pricing;
        this.status = status;
        this.assignedCarrierOrgId = assignedCarrierOrgId;
        this.createdAt = createdAt;
        this.cancelReason = cancelReason;
        this.republishedFrom = republishedFrom;
    }

    public static ShipmentRequest publish(UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing) {
        return publish(requesterOrgId, createdBy, cargo, pricing, null);
    }

    /** RN07: direcciones y descripción las ven los transportistas; no admiten datos de contacto. */
    public static ShipmentRequest publish(UUID requesterOrgId, UUID createdBy, Cargo cargo, Pricing pricing,
                                          UUID republishedFrom) {
        Cargo clean = new Cargo(cargo.originCity(), ContactInfoPolicy.clean(cargo.originAddress(), "originAddress"),
                cargo.destinationCity(), ContactInfoPolicy.clean(cargo.destinationAddress(), "destinationAddress"),
                cargo.cargoType(), ContactInfoPolicy.clean(cargo.cargoDescription(), "cargoDescription"),
                cargo.weightKg(), cargo.volumeM3(), cargo.pickupDate());
        return new ShipmentRequest(UUID.randomUUID(), requesterOrgId, createdBy, clean, pricing,
                Status.PUBLISHED, null, OffsetDateTime.now(), null, republishedFrom);
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
        cancel(null);
    }

    /** La PYME cancela antes de adjudicar; el motivo (opcional) lo ven los transportistas que ofertaron (RN07). */
    public void cancel(String reason) {
        if (status != Status.PUBLISHED) {
            throw BusinessException.conflict("LOGYX-REQ-002", "Solo se puede cancelar una solicitud publicada");
        }
        cancelReason = ContactInfoPolicy.clean(reason, "reason");
        status = Status.CANCELLED;
    }

    /** El envío adjudicado se canceló (F06): la solicitud queda cancelada con ese motivo. Idempotente. */
    public boolean cancelAfterAward(String reason) {
        if (status != Status.MATCHED) return false;
        status = Status.CANCELLED;
        cancelReason = reason;
        return true;
    }

    /** Reabrir la subasta (F07) admite mover la fecha de recojo; no puede ser pasada. */
    public void reschedule(LocalDate pickupDate, LocalDate today) {
        ensureOpenForBids();
        LocalDate date = pickupDate == null ? cargo.pickupDate() : pickupDate;
        if (date.isBefore(today)) {
            throw BusinessException.badRequest("LOGYX-REQ-003",
                    "La fecha de recojo ya pasó: indica una nueva fecha", "pickupDate");
        }
        cargo = new Cargo(cargo.originCity(), cargo.originAddress(), cargo.destinationCity(),
                cargo.destinationAddress(), cargo.cargoType(), cargo.cargoDescription(), cargo.weightKg(),
                cargo.volumeM3(), date);
    }

    public boolean isCancelled() { return status == Status.CANCELLED; }

    public UUID id() { return id; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID createdBy() { return createdBy; }
    public Cargo cargo() { return cargo; }
    public Pricing pricing() { return pricing; }
    public Status status() { return status; }
    public UUID assignedCarrierOrgId() { return assignedCarrierOrgId; }
    public OffsetDateTime createdAt() { return createdAt; }
    public String cancelReason() { return cancelReason; }
    public UUID republishedFrom() { return republishedFrom; }
}
