package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.City;
import pe.logyx.shipment.domain.model.ShipmentStop;

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

    ShipmentStop toDomain() {
        return new ShipmentStop(id, shipmentId, requesterOrgId, carrierOrgId, sequence, type, city, address, status,
                arrivedAt, completedAt);
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
    }
}
