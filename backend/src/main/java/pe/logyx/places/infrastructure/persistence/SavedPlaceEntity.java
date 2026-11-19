package pe.logyx.places.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import pe.logyx.catalog.domain.City;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "saved_places")
public class SavedPlaceEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organization_id")
    public UUID organizationId;

    public String label;

    @Enumerated(EnumType.STRING)
    public City city;

    public String address;

    @Column(name = "contact_name")
    public String contactName;

    @Column(name = "contact_phone")
    public String contactPhone;

    @Column(name = "opening_hours")
    public String openingHours;

    @Column(name = "window_from")
    public LocalTime windowFrom;

    @Column(name = "window_to")
    public LocalTime windowTo;

    public String instructions;

    @Enumerated(EnumType.STRING)
    public PlaceKind kind;

    @Column(name = "archived_at")
    public OffsetDateTime archivedAt;

    @Column(name = "last_used_at")
    public OffsetDateTime lastUsedAt;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    SavedPlace toDomain() {
        return new SavedPlace(id, organizationId, createdBy, createdAt,
                new SavedPlace.Data(label, city, address, contactName, contactPhone, openingHours, windowFrom,
                        windowTo, instructions, kind),
                archivedAt, lastUsedAt, updatedAt);
    }

    void apply(SavedPlace p) {
        var d = p.data();
        id = p.id();
        organizationId = p.organizationId();
        label = d.label();
        city = d.city();
        address = d.address();
        contactName = d.contactName();
        contactPhone = d.contactPhone();
        openingHours = d.openingHours();
        windowFrom = d.windowFrom();
        windowTo = d.windowTo();
        instructions = d.instructions();
        kind = d.kind();
        archivedAt = p.archivedAt();
        lastUsedAt = p.lastUsedAt();
        createdBy = p.createdBy();
        createdAt = p.createdAt();
        updatedAt = p.updatedAt();
    }
}
