package pe.logyx.shipment.domain.model;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Parada de un envío (recojo o entrega). {@code arrivedAt}/{@code completedAt} son la hora en que <b>ocurrió</b> el
 * evento (la del dispositivo del conductor, acotada por {@code StopEventTimePolicy}); {@code *RecordedAt} es la hora
 * del servidor en que llegó (roadmap 1.11: con mala señal un evento puede sincronizarse tarde).
 */
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
    private OffsetDateTime arrivedRecordedAt;
    private OffsetDateTime completedRecordedAt;
    private final StopContact contact;

    public ShipmentStop(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, int sequence, Type type,
                        City city, String address, Status status, OffsetDateTime arrivedAt,
                        OffsetDateTime completedAt) {
        this(id, shipmentId, requesterOrgId, carrierOrgId, sequence, type, city, address, status, arrivedAt,
                completedAt, arrivedAt, completedAt, StopContact.NONE);
    }

    public ShipmentStop(UUID id, UUID shipmentId, UUID requesterOrgId, UUID carrierOrgId, int sequence, Type type,
                        City city, String address, Status status, OffsetDateTime arrivedAt,
                        OffsetDateTime completedAt, OffsetDateTime arrivedRecordedAt,
                        OffsetDateTime completedRecordedAt, StopContact contact) {
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
        this.arrivedRecordedAt = arrivedRecordedAt;
        this.completedRecordedAt = completedRecordedAt;
        this.contact = StopContact.ofNullable(contact);
    }

    public static ShipmentStop plan(Shipment s, int sequence, Type type, City city, String address) {
        return plan(s, sequence, type, city, address, StopContact.NONE);
    }

    /** Parada planificada con el contacto copiado de la solicitud al adjudicar (RN07 post-adjudicación). */
    public static ShipmentStop plan(Shipment s, int sequence, Type type, City city, String address,
                                    StopContact contact) {
        return new ShipmentStop(UUID.randomUUID(), s.id(), s.requesterOrgId(), s.carrierOrgId(), sequence, type,
                city, address, Status.PENDING, null, null, null, null, contact);
    }

    public void arrive(OffsetDateTime now) {
        arrive(now, now);
    }

    /** {@code occurredAt}: cuándo llegó el conductor; {@code recordedAt}: cuándo lo supo el servidor. */
    public void arrive(OffsetDateTime occurredAt, OffsetDateTime recordedAt) {
        if (status != Status.PENDING) {
            throw BusinessException.conflict("LOGYX-TRK-002", "Ya se registró la llegada a esta parada");
        }
        status = Status.ARRIVED;
        arrivedAt = occurredAt;
        arrivedRecordedAt = recordedAt;
    }

    /** F49: antes de pedir evidencia o cerrar, la llegada debe estar registrada (LOGYX-TRK-003). */
    public void ensureArrived() {
        if (status != Status.ARRIVED) {
            throw BusinessException.conflict("LOGYX-TRK-003", "Primero registre la llegada a la parada");
        }
    }

    public void complete(OffsetDateTime now) {
        complete(now, now);
    }

    public void complete(OffsetDateTime occurredAt, OffsetDateTime recordedAt) {
        if (status != Status.ARRIVED) {
            throw BusinessException.conflict("LOGYX-TRK-003", "Primero registre la llegada a la parada");
        }
        status = Status.COMPLETED;
        completedAt = occurredAt;
        completedRecordedAt = recordedAt;
    }

    public boolean isCompleted() { return status == Status.COMPLETED; }

    /** La llegada ya quedó registrada (la parada está ARRIVED o COMPLETED): repetir «Llegué» no cambia nada. */
    public boolean arrivalRecorded() { return status != Status.PENDING; }

    /** El operador cierra la parada al dar por entregado el envío (RF55): registra llegada y cierre ahora. */
    public void forceComplete(OffsetDateTime now) {
        if (status == Status.COMPLETED) return;
        if (arrivedAt == null) {
            arrivedAt = now;
            arrivedRecordedAt = now;
        }
        status = Status.COMPLETED;
        completedAt = now;
        completedRecordedAt = now;
    }

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
    public OffsetDateTime arrivedRecordedAt() { return arrivedRecordedAt; }
    public OffsetDateTime completedRecordedAt() { return completedRecordedAt; }
    public StopContact contact() { return contact; }
}
