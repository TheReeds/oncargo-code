package pe.logyx.fleet.application.port.out;

import pe.logyx.fleet.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository {
    Optional<Vehicle> findById(UUID id);
    Optional<Vehicle> findOwned(UUID id, UUID organizationId);
    List<Vehicle> listByOrganization(UUID organizationId);
    boolean plateExists(String plate);
    /** Inserta o actualiza. */
    void save(Vehicle vehicle);
}
