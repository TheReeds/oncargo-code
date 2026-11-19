package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.City;
import pe.logyx.shipment.domain.model.ShipmentStop;
import pe.logyx.shipment.domain.model.StopContact;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_stops")
public class ShipmentStopEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    public int sequence;

    @Enumerated(EnumType.STRING)
    public ShipmentStop.Type type;

    @Enumerated(EnumType.STRING)
    public City city;

    public String address;

    @Enumerated(EnumType.STRING)
    public ShipmentStop.Status status;

    @Column(name = "arrived_at")
    public OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    public OffsetDateTime completedAt;

    @Column(name = "arrived_recorded_at")
    public OffsetDateTime arrivedRecordedAt;

    @Column(name = "completed_recorded_at")
    public OffsetDateTime completedRecordedAt;

    @Column(name = "contact_name")
    public String contactName;

    @Column(name = "contact_phone")
    public String contactPhone;

    @Column(name = "window_from")
    public LocalTime windowFrom;

    @Column(name = "window_to")
    public LocalTime windowTo;

    public String instructions;

    ShipmentStop toDomain() {
        return new ShipmentStop(id, shipmentId, requesterOrgId, carrierOrgId, sequence, type, city, address, status,
                arrivedAt, completedAt, arrivedRecordedAt, completedRecordedAt,
                new StopContact(contactName, contactPhone, windowFrom, windowTo, instructions));
    }

    void apply(ShipmentStop s) {
        id = s.id();
        shipmentId = s.shipmentId();
        requesterOrgId = s.requesterOrgId();
        carrierOrgId = s.carrierOrgId();
        sequence = s.sequence();
        type = s.type();
        city = s.city();
        address = s.address();
        status = s.status();
        arrivedAt = s.arrivedAt();
        completedAt = s.completedAt();
        arrivedRecordedAt = s.arrivedRecordedAt();
        completedRecordedAt = s.completedRecordedAt();
        StopContact c = s.contact();
        contactName = c.name();
        contactPhone = c.phone();
        windowFrom = c.windowFrom();
        windowTo = c.windowTo();
        instructions = c.instructions();
    }
}
