package pe.logyx.fleet.application.port.out;

import pe.logyx.fleet.domain.model.Driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository {
    Optional<Driver> findById(UUID id);
    Optional<Driver> findOwned(UUID id, UUID organizationId);
    Optional<Driver> findByProfile(UUID profileId);
    List<Driver> listByOrganization(UUID organizationId);
    /** Inserta o actualiza. */
    void save(Driver driver);
}
