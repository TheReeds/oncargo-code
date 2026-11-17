package pe.logyx.fleet.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.port.out.DriverRepository;
import pe.logyx.fleet.application.port.out.VehicleRepository;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Casos de uso de flota (vehículos y conductores) del transportista actual, más
 * las operaciones que marketplace y shipment necesitan sobre la flota.
 */
@ApplicationScoped
@Transactional
public class FleetService {

    @Inject VehicleRepository vehicles;
    @Inject DriverRepository drivers;
    @Inject UserAccounts accounts;
    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject SystemTx system;

    // ------------------------------------------------------------ Vehículos

    public List<Vehicle> vehicles() {
        // El WHERE es la primera barrera; RLS (vehicles_owner) la segunda.
        return vehicles.listByOrganization(tenant.orgId());
    }

    public Vehicle addVehicle(AddVehicleCommand cmd) {
        Vehicle v = Vehicle.register(tenant.orgId(), cmd.plate(), cmd.type(), cmd.maxWeightKg(), cmd.maxVolumeM3());
        // Unicidad global: con RLS solo veríamos las placas propias.
        if (system.call(() -> vehicles.plateExists(v.plate()))) {
            throw BusinessException.conflict("LOGYX-FLT-001", "La placa ya está registrada", "plate");
        }
        vehicles.save(v);
        return v;
    }

    public Vehicle setVehicleStatus(UUID id, Vehicle.Status status) {
        Vehicle v = ownVehicle(id);
        v.changeStatus(status);
        vehicles.save(v);
        return v;
    }

    /** Vehículo de la organización actual o 404. */
    public Vehicle ownVehicle(UUID id) {
        return vehicles.findOwned(id, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Vehículo"));
    }

    /**
     * Vehículo de un transportista dado, para la asignación manual del operador (RN10). Bajo RLS solo el
     * operador (vehicles_operator) o el propio transportista pueden leerlo; si no pertenece a esa organización, 404.
     */
    public Vehicle vehicleOfCarrier(UUID carrierOrgId, UUID vehicleId) {
        return vehicles.findOwned(vehicleId, carrierOrgId).orElseThrow(() -> BusinessException.notFound("Vehículo"));
    }

    /** Vehículos disponibles con capacidad ≥ {@code minKg} de todos los transportistas (lectura del operador). */
    public List<Vehicle> availableVehiclesForOperator(java.math.BigDecimal minKg) {
        return vehicles.listAvailableWithCapacity(minKg);
    }

    /**
     * Vehículos disponibles de todas las organizaciones visibles. Sin usuario (planificador programado) corre como
     * dueño del esquema; con usuario, RLS deja solo los propios (transportista) o todos (operador).
     */
    public List<Vehicle> availableVehicles() {
        return vehicles.listAvailableWithCapacity(java.math.BigDecimal.ZERO);
    }

    /**
     * Placa a copiar en el contrato de envío. La flota es privada del
     * transportista (RLS) y quien acepta es la PYME: se lee en contexto de
     * sistema, una sola vez, y solo la placa.
     */
    public String plateForContract(UUID vehicleId) {
        return system.call(() -> vehicles.findById(vehicleId).orElseThrow().plate());
    }

    // ------------------------------------------------------------ Conductores

    public List<DriverAccount> drivers() {
        List<Driver> list = drivers.listByOrganization(tenant.orgId());
        Map<UUID, Profile> profiles = accounts.byIds(list.stream().map(Driver::profileId).toList());
        return list.stream().map(d -> account(d, profiles.get(d.profileId()))).toList();
    }

    /** Crea la cuenta del conductor (rol DRIVER) dentro de la organización transportista. */
    public DriverAccount addDriver(AddDriverCommand cmd) {
        Profile p = accounts.createDriverAccount(tenant.orgId(), cmd.fullName(), cmd.email(), cmd.password());
        Driver d = Driver.hire(tenant.orgId(), p.id(), cmd.licenseNumber());
        drivers.save(d);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_CREATED", "driver", d.id(), null);
        return account(d, p);
    }

    /** Conductor de la organización actual o 404. */
    public DriverAccount ownDriver(UUID id) {
        Driver d = drivers.findOwned(id, tenant.orgId()).orElseThrow(() -> BusinessException.notFound("Conductor"));
        return account(d, accounts.byId(d.profileId()).orElseThrow());
    }

    public Optional<Driver> driverForProfile(UUID profileId) {
        return drivers.findByProfile(profileId);
    }

    // ------------------------------------------------------------ Viajes (lo invoca shipment)

    /** El envío salió: vehículo en ruta y conductor en servicio. */
    public void startTrip(UUID vehicleId, UUID driverId) {
        Vehicle v = vehicles.findById(vehicleId).orElseThrow();
        Driver d = drivers.findById(driverId).orElseThrow();
        v.startRoute();
        d.goOnDuty();
        vehicles.save(v);
        drivers.save(d);
    }

    /**
     * El envío terminó (o se canceló en ruta): vehículo y conductor vuelven a estar disponibles. Lo invoca shipment
     * con el RLS de quien actúa (conductor, o el operador con las políticas de V13).
     */
    public void finishTrip(UUID vehicleId, UUID driverId) {
        vehicles.findById(vehicleId).ifPresent(v -> { v.release(); vehicles.save(v); });
        if (driverId != null) drivers.findById(driverId).ifPresent(d -> { d.release(); drivers.save(d); });
    }

    /** Cambio de vehículo con la carga ya recogida (F06): el anterior queda disponible y el nuevo en ruta. */
    public void swapVehicleInRoute(UUID previousVehicleId, UUID newVehicleId) {
        vehicles.findById(previousVehicleId).ifPresent(v -> { v.release(); vehicles.save(v); });
        Vehicle n = vehicles.findById(newVehicleId).orElseThrow();
        n.startRoute();
        vehicles.save(n);
    }

    /** Relevo de conductor con la carga ya recogida (F06). */
    public void swapDriverInRoute(UUID previousDriverId, UUID newDriverId) {
        if (previousDriverId != null) drivers.findById(previousDriverId).ifPresent(d -> { d.release(); drivers.save(d); });
        Driver n = drivers.findById(newDriverId).orElseThrow();
        n.goOnDuty();
        drivers.save(n);
    }

    private static DriverAccount account(Driver d, Profile p) {
        return new DriverAccount(d, p == null ? null : p.fullName(), p == null ? null : p.email());
    }
}
