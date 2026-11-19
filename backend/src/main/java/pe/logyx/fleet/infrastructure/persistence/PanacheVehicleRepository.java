package pe.logyx.fleet.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.fleet.application.port.out.VehicleRepository;
import pe.logyx.fleet.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheVehicleRepository implements VehicleRepository {

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return VehicleEntity.<VehicleEntity>findByIdOptional(id).map(VehicleEntity::toDomain);
    }

    @Override
    public Optional<Vehicle> findOwned(UUID id, UUID organizationId) {
        return VehicleEntity.<VehicleEntity>find("id = ?1 and organizationId = ?2", id, organizationId)
                .firstResultOptional().map(VehicleEntity::toDomain);
    }

    @Override
    public List<Vehicle> listByOrganization(UUID organizationId) {
        return VehicleEntity.<VehicleEntity>list("organizationId = ?1 and archivedAt is null order by plate", organizationId)
                .stream().map(VehicleEntity::toDomain).toList();
    }

    @Override
    public boolean plateExists(String plate) {
        return VehicleEntity.count("plate", plate) > 0;
    }

    @Override
    public List<Vehicle> listAvailableWithCapacity(java.math.BigDecimal minKg) {
        return VehicleEntity.<VehicleEntity>list("status = ?1 and archivedAt is null and maxWeightKg >= ?2 order by maxWeightKg, plate",
                Vehicle.Status.AVAILABLE, minKg).stream().map(VehicleEntity::toDomain).toList();
    }

    @Override
    public void save(Vehicle vehicle) {
        VehicleEntity e = VehicleEntity.findById(vehicle.id());
        if (e == null) {
            e = new VehicleEntity();
            e.apply(vehicle);
            e.persist();
        } else {
            e.apply(vehicle);
        }
    }
}
