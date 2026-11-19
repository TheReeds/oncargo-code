package pe.logyx.fleet.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.fleet.application.port.out.FleetDocumentRepository;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheFleetDocumentRepository implements FleetDocumentRepository {

    @Inject EntityManager em;

    @Override
    public Optional<FleetDocument> findById(UUID id) {
        return FleetDocumentEntity.<FleetDocumentEntity>findByIdOptional(id).map(FleetDocumentEntity::toDomain);
    }

    @Override
    public void insert(FleetDocument d) {
        FleetDocumentEntity.of(d).persist();
    }

    @Override
    public void supersedeCurrent(FleetDocumentType type, UUID ownerId, OffsetDateTime now) {
        String owner = type.owner() == FleetDocumentType.Owner.VEHICLE ? "vehicleId" : "driverId";
        FleetDocumentEntity.update("supersededAt = ?1 where docType = ?2 and " + owner + " = ?3 and supersededAt is null",
                now, type, ownerId);
    }

    @Override
    public List<FleetDocument> listByVehicle(UUID vehicleId) {
        return FleetDocumentEntity.<FleetDocumentEntity>list("vehicleId = ?1 order by createdAt desc, id", vehicleId)
                .stream().map(FleetDocumentEntity::toDomain).toList();
    }

    @Override
    public List<FleetDocument> listByDriver(UUID driverId) {
        return FleetDocumentEntity.<FleetDocumentEntity>list("driverId = ?1 order by createdAt desc, id", driverId)
                .stream().map(FleetDocumentEntity::toDomain).toList();
    }

    @Override
    public List<FleetDocument> currentOfVehicles(Collection<UUID> vehicleIds) {
        if (vehicleIds.isEmpty()) return List.of();
        return FleetDocumentEntity.<FleetDocumentEntity>list("vehicleId in ?1 and supersededAt is null", vehicleIds)
                .stream().map(FleetDocumentEntity::toDomain).toList();
    }

    @Override
    public List<FleetDocument> currentOfDrivers(Collection<UUID> driverIds) {
        if (driverIds.isEmpty()) return List.of();
        return FleetDocumentEntity.<FleetDocumentEntity>list("driverId in ?1 and supersededAt is null", driverIds)
                .stream().map(FleetDocumentEntity::toDomain).toList();
    }

    @Override
    public List<FleetDocument> currentExpiringUntil(LocalDate until) {
        return FleetDocumentEntity.<FleetDocumentEntity>list(
                        "supersededAt is null and expiresAt is not null and expiresAt <= ?1 order by expiresAt", until)
                .stream().map(FleetDocumentEntity::toDomain).toList();
    }

    @Override
    public boolean noticeSent(UUID documentId, int threshold) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM fleet_document_notices WHERE document_id = :d "
                        + "AND threshold = :t").setParameter("d", documentId).setParameter("t", threshold)
                .getSingleResult()).longValue() > 0;
    }

    @Override
    public void recordNotice(UUID documentId, int threshold) {
        em.createNativeQuery("INSERT INTO fleet_document_notices (document_id, threshold) VALUES (:d, :t) "
                        + "ON CONFLICT DO NOTHING").setParameter("d", documentId).setParameter("t", threshold)
                .executeUpdate();
    }
}
