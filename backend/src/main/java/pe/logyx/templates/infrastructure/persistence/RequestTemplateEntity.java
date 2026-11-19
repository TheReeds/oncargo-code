package pe.logyx.templates.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.templates.domain.model.RequestTemplate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_templates")
public class RequestTemplateEntity extends PanacheEntityBase {

    @Id
    public UUID id;
    @Column(name = "organization_id") public UUID organizationId;
    @Column(name = "created_by") public UUID createdBy;
    public String name;
    @Column(name = "origin_city") @Enumerated(EnumType.STRING) public City originCity;
    @Column(name = "origin_address") public String originAddress;
    @Column(name = "destination_city") @Enumerated(EnumType.STRING) public City destinationCity;
    @Column(name = "destination_address") public String destinationAddress;
    @Column(name = "cargo_type") @Enumerated(EnumType.STRING) public CargoType cargoType;
    @Column(name = "cargo_description") public String cargoDescription;
    @Column(name = "weight_kg") public BigDecimal weightKg;
    @Column(name = "volume_m3") public BigDecimal volumeM3;
    @Column(name = "customer_reference") public String customerReference;
    @Column(name = "declared_value") public BigDecimal declaredValue;
    @Column(name = "pickup_contact_name") public String pickupContactName;
    @Column(name = "pickup_contact_phone") public String pickupContactPhone;
    @Column(name = "pickup_window_from") public LocalTime pickupWindowFrom;
    @Column(name = "pickup_window_to") public LocalTime pickupWindowTo;
    @Column(name = "pickup_instructions") public String pickupInstructions;
    @Column(name = "delivery_contact_name") public String deliveryContactName;
    @Column(name = "delivery_contact_phone") public String deliveryContactPhone;
    @Column(name = "delivery_window_from") public LocalTime deliveryWindowFrom;
    @Column(name = "delivery_window_to") public LocalTime deliveryWindowTo;
    @Column(name = "delivery_instructions") public String deliveryInstructions;
    @Column(name = "origin_place_id") public UUID originPlaceId;
    @Column(name = "destination_place_id") public UUID destinationPlaceId;
    @Column(name = "invite_preferred") public boolean invitePreferred;
    @Column(name = "archived_at") public OffsetDateTime archivedAt;
    @Column(name = "last_published_at") public OffsetDateTime lastPublishedAt;
    @Column(name = "created_at") public OffsetDateTime createdAt;
    @Column(name = "updated_at") public OffsetDateTime updatedAt;

    RequestTemplate toDomain() {
        var pickup = new ShipmentRequest.StopDetails(pickupContactName, pickupContactPhone, pickupWindowFrom,
                pickupWindowTo, pickupInstructions);
        var delivery = new ShipmentRequest.StopDetails(deliveryContactName, deliveryContactPhone, deliveryWindowFrom,
                deliveryWindowTo, deliveryInstructions);
        var details = new ShipmentRequest.Details(customerReference, declaredValue, pickup, delivery);
        return new RequestTemplate(id, organizationId, createdBy, createdAt,
                new RequestTemplate.Data(name, originCity, originAddress, destinationCity, destinationAddress,
                        cargoType, cargoDescription, weightKg, volumeM3, details, originPlaceId, destinationPlaceId,
                        invitePreferred), archivedAt, lastPublishedAt, updatedAt);
    }

    void apply(RequestTemplate t) {
        var d = t.data();
        id = t.id();
        organizationId = t.organizationId();
        createdBy = t.createdBy();
        name = d.name();
        originCity = d.originCity();
        originAddress = d.originAddress();
        destinationCity = d.destinationCity();
        destinationAddress = d.destinationAddress();
        cargoType = d.cargoType();
        cargoDescription = d.cargoDescription();
        weightKg = d.weightKg();
        volumeM3 = d.volumeM3();
        var det = d.details();
        customerReference = det.customerReference();
        declaredValue = det.declaredValue();
        pickupContactName = det.pickup().contactName();
        pickupContactPhone = det.pickup().contactPhone();
        pickupWindowFrom = det.pickup().windowFrom();
        pickupWindowTo = det.pickup().windowTo();
        pickupInstructions = det.pickup().instructions();
        deliveryContactName = det.delivery().contactName();
        deliveryContactPhone = det.delivery().contactPhone();
        deliveryWindowFrom = det.delivery().windowFrom();
        deliveryWindowTo = det.delivery().windowTo();
        deliveryInstructions = det.delivery().instructions();
        originPlaceId = d.originPlaceId();
        destinationPlaceId = d.destinationPlaceId();
        invitePreferred = d.invitePreferred();
        archivedAt = t.archivedAt();
        lastPublishedAt = t.lastPublishedAt();
        createdAt = t.createdAt();
        updatedAt = t.updatedAt();
    }
}
