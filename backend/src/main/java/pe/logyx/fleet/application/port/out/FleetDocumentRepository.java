package pe.logyx.fleet.application.port.out;

import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FleetDocumentRepository {
    Optional<FleetDocument> findById(UUID id);
    void insert(FleetDocument d);
    /** Marca como reemplazados los vigentes del mismo dueño y tipo. */
    void supersedeCurrent(FleetDocumentType type, UUID ownerId, java.time.OffsetDateTime now);
    /** Vigentes e historial del vehículo, recientes primero. */
    List<FleetDocument> listByVehicle(UUID vehicleId);
    List<FleetDocument> listByDriver(UUID driverId);
    /** Solo los vigentes de varios vehículos o conductores (agenda, sellos, cumplimiento). */
    List<FleetDocument> currentOfVehicles(Collection<UUID> vehicleIds);
    List<FleetDocument> currentOfDrivers(Collection<UUID> driverIds);
    /** Job: vigentes con vencimiento <= {@code until}. Corre como sistema (cruza organizaciones). */
    List<FleetDocument> currentExpiringUntil(LocalDate until);
    boolean noticeSent(UUID documentId, int threshold);
    void recordNotice(UUID documentId, int threshold);
}
