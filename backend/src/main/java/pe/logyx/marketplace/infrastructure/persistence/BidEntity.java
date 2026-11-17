package pe.logyx.marketplace.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.domain.model.Bid;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class BidEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "auction_id")
    public UUID auctionId;

    @Column(name = "request_id")
    public UUID requestId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "carrier_org_id")
    public UUID carrierOrgId;

    @Column(name = "vehicle_id")
    public UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    public Vehicle.Type vehicleType;

    @Column(name = "vehicle_plate")
    public String vehiclePlate;

    public BigDecimal amount;
    public String notes;

    @Enumerated(EnumType.STRING)
    public Bid.Status status;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "assigned_by_operator")
    public boolean assignedByOperator;

    @Column(name = "load_group_id")
    public UUID loadGroupId;

    Bid toDomain() {
        return new Bid(id, auctionId, requestId, requesterOrgId, carrierOrgId, vehicleId, vehicleType,
                vehiclePlate, amount, notes, status, createdAt, assignedByOperator, loadGroupId);
    }

    void apply(Bid b) {
        id = b.id();
        auctionId = b.auctionId();
        requestId = b.requestId();
        requesterOrgId = b.requesterOrgId();
        carrierOrgId = b.carrierOrgId();
        vehicleId = b.vehicleId();
        vehicleType = b.vehicleType();
        vehiclePlate = b.vehiclePlate();
        amount = b.amount();
        notes = b.notes();
        status = b.status();
        createdAt = b.createdAt();
        assignedByOperator = b.assignedByOperator();
        loadGroupId = b.loadGroupId();
    }
}
