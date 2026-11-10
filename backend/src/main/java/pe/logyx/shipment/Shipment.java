package pe.logyx.shipment;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment extends PanacheEntityBase {

    public enum Status { PENDING, IN_TRANSIT, DELIVERED, CANCELLED }

    @Id
    @GeneratedValue
    public UUID id;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "bid_id")
    public UUID bidId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    @Column(name = "driver_id")
    public UUID driverId;

    @Column(name = "agreed_price")
    public BigDecimal agreedPrice;

    @Column(name = "vehicle_plate")
    public String vehiclePlate;

    @Column(name = "driver_name")
    public String driverName;

    @Enumerated(EnumType.STRING)
    public Status status = Status.PENDING;

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "delivered_at")
    public OffsetDateTime deliveredAt;
}
