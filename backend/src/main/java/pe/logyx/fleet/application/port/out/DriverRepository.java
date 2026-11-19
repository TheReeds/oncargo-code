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
    /** La licencia (sin distinguir mayúsculas ni espacios en los bordes) ya existe en la organización. */
    boolean licenseExists(UUID organizationId, String licenseNumber);
    /** Inserta o actualiza. */
    void save(Driver driver);
}
