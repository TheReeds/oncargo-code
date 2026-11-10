package pe.logyx.shipment.domain.model;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Parada de un envío (recojo o entrega). */
public final class ShipmentStop {

    public enum Type { PICKUP, DELIVERY }
    public enum Status { PENDING, ARRIVED, COMPLETED }

    private final UUID id;
    private final UUID shipmentId;
    private final UUID requesterOrgId;
    private final UUID carrierOrgId;
    private final int sequence;
    private final Type type;
    private final City city;
    private final String address;
    private Status status;
    private OffsetDateTime arrivedAt;
    private OffsetDateTime completedAt;

    public ShipmentStop(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, int sequence, Type type,
                        City city, String address, Status status, OffsetDateTime arrivedAt,
                        OffsetDateTime completedAt) {
        this.id = id;
        this.shipmentId = shipmentId;
        this.requesterOrgId = requesterOrgId;
        this.carrierOrgId = carrierOrgId;
        this.sequence = sequence;
        this.type = type;
        this.city = city;
        this.address = address;
        this.status = status;
        this.arrivedAt = arrivedAt;
        this.completedAt = completedAt;
    }

    public static ShipmentStop plan(Shipment s, int sequence, Type type, City city, String address) {
        return new ShipmentStop(UUID.randomUUID(), s.id(), s.requesterOrgId(), s.carrierOrgId(), sequence, type,
                city, address, Status.PENDING, null, null);
    }

    public void arrive(OffsetDateTime now) {
        if (status != Status.PENDING) {
            throw BusinessException.conflict("LOGYX-TRK-002", "Ya se registró la llegada a esta parada");
        }
        status = Status.ARRIVED;
        arrivedAt = now;
    }

    public void complete(OffsetDateTime now) {
        if (status != Status.ARRIVED) {
            throw BusinessException.conflict("LOGYX-TRK-003", "Primero registre la llegada a la parada");
        }
        status = Status.COMPLETED;
        completedAt = now;
    }

    public boolean isCompleted() { return status == Status.COMPLETED; }

    public UUID id() { return id; }
    public UUID shipmentId() { return shipmentId; }
    public UUID requesterOrgId() { return requesterOrgId; }
    public UUID carrierOrgId() { return carrierOrgId; }
    public int sequence() { return sequence; }
    public Type type() { return type; }
    public City city() { return city; }
    public String address() { return address; }
    public Status status() { return status; }
    public OffsetDateTime arrivedAt() { return arrivedAt; }
    public OffsetDateTime completedAt() { return completedAt; }
}
