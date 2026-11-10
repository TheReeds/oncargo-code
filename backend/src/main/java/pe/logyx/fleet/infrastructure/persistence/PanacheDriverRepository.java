package pe.logyx.fleet.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.fleet.application.port.out.DriverRepository;
import pe.logyx.fleet.domain.model.Driver;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheDriverRepository implements DriverRepository {

    @Override
    public Optional<Driver> findById(UUID id) {
        return DriverEntity.<DriverEntity>findByIdOptional(id).map(DriverEntity::toDomain);
    }

    @Override
    public Optional<Driver> findOwned(UUID id, UUID organizationId) {
        return DriverEntity.<DriverEntity>find("id = ?1 and organizationId = ?2", id, organizationId)
                .firstResultOptional().map(DriverEntity::toDomain);
    }

    @Override
    public Optional<Driver> findByProfile(UUID profileId) {
        return DriverEntity.<DriverEntity>find("profileId", profileId).firstResultOptional().map(DriverEntity::toDomain);
    }

    @Override
    public List<Driver> listByOrganization(UUID organizationId) {
        return DriverEntity.<DriverEntity>list("organizationId = ?1 order by createdAt", organizationId)
                .stream().map(DriverEntity::toDomain).toList();
    }

    @Override
    public void save(Driver driver) {
        DriverEntity e = DriverEntity.findById(driver.id());
        if (e == null) {
            e = new DriverEntity();
            e.apply(driver);
            e.persist();
        } else {
            e.apply(driver);
        }
    }
}
