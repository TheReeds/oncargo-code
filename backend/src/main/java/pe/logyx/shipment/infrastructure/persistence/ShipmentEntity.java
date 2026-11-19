package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class ShipmentEntity extends PanacheEntityBase {

    @Id
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
    public Shipment.Status status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "delivered_at")
    public OffsetDateTime deliveredAt;

    @Column(name = "load_group_id")
    public UUID loadGroupId;

    @Column(name = "cancel_reason")
    public String cancelReason;

    @Column(name = "cancelled_at")
    public OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by_org_id")
    public UUID cancelledByOrgId;

    @Column(name = "commission_rate")
    public BigDecimal commissionRate;

    @Column(name = "pickup_photo_required")
    public boolean pickupPhotoRequired;

    @Column(name = "pickup_weight_required")
    public boolean pickupWeightRequired;

    Shipment toDomain() {
        return new Shipment(id, requestId, bidId, requesterOrgId, carrierOrgId, vehicleId, driverId, agreedPrice,
                vehiclePlate, driverName, status, createdAt, deliveredAt, loadGroupId,
                cancelledAt == null && cancelReason == null ? null
                        : new Shipment.Cancellation(cancelledByOrgId, cancelReason, cancelledAt),
                commissionRate)
                .restorePickupRequirements(new Shipment.PickupRequirements(pickupPhotoRequired, pickupWeightRequired));
    }

    void apply(Shipment s) {
        id = s.id();
        requestId = s.requestId();
        bidId = s.bidId();
        requesterOrgId = s.requesterOrgId();
        carrierOrgId = s.carrierOrgId();
        vehicleId = s.vehicleId();
        driverId = s.driverId();
        agreedPrice = s.agreedPrice();
        vehiclePlate = s.vehiclePlate();
        driverName = s.driverName();
        status = s.status();
        createdAt = s.createdAt();
        deliveredAt = s.deliveredAt();
        loadGroupId = s.loadGroupId();
        commissionRate = s.commissionRate();
        pickupPhotoRequired = s.pickupRequirements().photo();
        pickupWeightRequired = s.pickupRequirements().weight();
        var c = s.cancellation();
        cancelReason = c == null ? null : c.reason();
        cancelledAt = c == null ? null : c.at();
        cancelledByOrgId = c == null ? null : c.byOrgId();
    }
}
