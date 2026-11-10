package pe.logyx.shipment;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.City;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_stops")
public class ShipmentStop extends PanacheEntityBase {

    public enum Type { PICKUP, DELIVERY }
    public enum Status { PENDING, ARRIVED, COMPLETED }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    public int sequence;

    @Enumerated(EnumType.STRING)
    public Type type;

    @Enumerated(EnumType.STRING)
    public City city;

    public String address;

    @Enumerated(EnumType.STRING)
    public Status status = Status.PENDING;

    @Column(name = "arrived_at")
    public OffsetDateTime arrivedAt;

    @Column(name = "completed_at")
    public OffsetDateTime completedAt;
}
