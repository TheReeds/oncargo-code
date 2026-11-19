package pe.logyx.fleet.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.port.out.DriverRepository;
import pe.logyx.fleet.application.port.out.FleetCommitments;
import pe.logyx.fleet.application.port.out.VehicleRepository;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.security.AccessDenied;
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
    @Inject FleetCommitments commitments;

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
        if (status == Vehicle.Status.MAINTENANCE && v.status() != Vehicle.Status.MAINTENANCE) {
            // Higiene de flota: no se da de baja un vehículo que tiene envíos activos o por salir.
            var active = commitments.activeShipmentsOfVehicle(v.id());
            if (!active.isEmpty()) {
                throw BusinessException.conflict("LOGYX-FLT-004", v.plate() + " tiene " + describe(active.size(),
                        "envío activo", "envíos activos") + " (" + span(active) + "): reasígnalo antes de ponerlo en "
                        + "mantenimiento", "vehicleId");
            }
        }
        v.changeStatus(status);
        vehicles.save(v);
        return v;
    }

    /**
     * Corrige tipo, capacidad y volumen (roadmap 1.12). Con envíos activos, ofertas pendientes o viajes publicados solo
     * se puede cambiar el volumen: tipo y kg sustentan RN02 y RN12 de esos compromisos (LOGYX-FLT-004).
     */
    public Vehicle updateVehicle(UUID id, UpdateVehicleCommand cmd) {
        Vehicle v = ownVehicle(id);
        if (v.isArchived()) {
            throw BusinessException.conflict("LOGYX-FLT-004", "El vehículo está archivado y no se edita", "vehicleId");
        }
        boolean changesCore = (cmd.type() != null && cmd.type() != v.type())
                || (cmd.maxWeightKg() != null && cmd.maxWeightKg().compareTo(v.maxWeightKg()) != 0);
        if (changesCore && hasCommitments(v)) {
            throw BusinessException.conflict("LOGYX-FLT-004", v.plate() + " tiene envíos, ofertas pendientes o viajes "
                    + "publicados: solo puedes corregir su volumen (m³)", "vehicleId");
        }
        v.revise(changesCore ? cmd.type() : null, changesCore ? cmd.maxWeightKg() : null, cmd.maxVolumeM3());
        vehicles.save(v);
        audit.record(tenant.profileId(), tenant.orgId(), "VEHICLE_UPDATED", "vehicle", v.id(), v.plate());
        return v;
    }

    /**
     * Equipamiento y tipos de carga aceptados por el vehículo (2.4): restringen la matriz RN02, nunca la amplían
     * (LOGYX-FLT-009). Con ofertas pendientes ya hechas no cambia nada para ellas: la restricción rige al ofertar y al aceptar.
     */
    public Vehicle configureVehicle(UUID id, java.util.Set<pe.logyx.fleet.domain.model.Equipment> equipment,
                                    java.util.Set<pe.logyx.catalog.domain.CargoType> accepted) {
        Vehicle v = ownVehicle(id);
        if (v.isArchived()) {
            throw BusinessException.conflict("LOGYX-FLT-004", "El vehículo está archivado y no se edita", "vehicleId");
        }
        v.configure(equipment, accepted);
        vehicles.save(v);
        audit.record(tenant.profileId(), tenant.orgId(), "VEHICLE_CONFIGURED", "vehicle", v.id(), v.plate());
        return v;
    }

    /** Categoría de licencia y DNI del conductor (2.4). El vencimiento y el archivo van como documento. */
    public DriverAccount updateDriverCredentials(UUID id, String licenseCategory, String dni) {
        AccessDenied.requireOrgAdmin(tenant);
        DriverAccount current = ownDriver(id);
        if (dni != null && !dni.isBlank() && !dni.trim().matches("\\d{8}")) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "El DNI tiene 8 dígitos", "dni");
        }
        Driver d = current.driver();
        d.updateCredentials(licenseCategory, d.licenseExpiresAt(), dni);
        drivers.save(d);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_UPDATED", "driver", d.id(), null);
        return account(d, accounts.byId(d.profileId()).orElse(null));
    }

    /** Vehículos de una organización (para el matching de alertas, sin usuario): sin archivados. */
    public List<Vehicle> vehiclesOf(UUID organizationId) {
        return vehicles.listByOrganization(organizationId);
    }

    /** Da de baja (archiva) el vehículo: sin envíos activos, ofertas pendientes ni viajes publicados (LOGYX-FLT-004). */
    public Vehicle archiveVehicle(UUID id) {
        Vehicle v = ownVehicle(id);
        if (v.isArchived()) return v;
        if (hasCommitments(v)) {
            throw BusinessException.conflict("LOGYX-FLT-004", v.plate() + " tiene envíos activos, ofertas pendientes o "
                    + "viajes publicados: resuélvelos antes de archivarlo", "vehicleId");
        }
        v.archive(java.time.OffsetDateTime.now());
        vehicles.save(v);
        audit.record(tenant.profileId(), tenant.orgId(), "VEHICLE_ARCHIVED", "vehicle", v.id(), v.plate());
        return v;
    }

    private boolean hasCommitments(Vehicle v) {
        return !commitments.activeShipmentsOfVehicle(v.id()).isEmpty()
                || commitments.pendingBidsOfVehicle(v.id()) > 0
                || commitments.openTripsOfVehicle(v.id()) > 0;
    }

    private static String describe(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String span(java.util.List<FleetCommitments.ActiveShipment> list) {
        var from = list.stream().map(FleetCommitments.ActiveShipment::from).min(java.util.Comparator.naturalOrder()).orElseThrow();
        var to = list.stream().map(FleetCommitments.ActiveShipment::to).max(java.util.Comparator.naturalOrder()).orElseThrow();
        return from.equals(to) ? from.toString() : from + " a " + to;
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

    /** Crea la cuenta del conductor (rol DRIVER) dentro de la organización transportista. Solo su ADMIN (F03). */
    public DriverAccount addDriver(AddDriverCommand cmd) {
        AccessDenied.requireOrgAdmin(tenant);
        if (drivers.licenseExists(tenant.orgId(), cmd.licenseNumber())) {
            throw BusinessException.conflict("LOGYX-FLT-005", "Ya hay un conductor con esa licencia en tu flota",
                    "licenseNumber");
        }
        Profile p = accounts.createDriverAccount(tenant.orgId(), cmd.fullName(), cmd.email(), cmd.password());
        Driver d = Driver.hire(tenant.orgId(), p.id(), cmd.licenseNumber());
        drivers.save(d);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_CREATED", "driver", d.id(), null);
        return account(d, p);
    }

    /** Desactiva (pierde el acceso de inmediato) o reactiva la cuenta del conductor (F31). Solo el ADMIN. */
    public DriverAccount setDriverActive(UUID id, boolean active) {
        AccessDenied.requireOrgAdmin(tenant);
        DriverAccount current = ownDriver(id);
        if (!active) {
            // Higiene de flota: un conductor con envíos activos o por salir no se desactiva (queda un envío huérfano).
            var running = commitments.activeShipmentsOfDriver(current.driver().id());
            if (!running.isEmpty()) {
                throw BusinessException.conflict("LOGYX-FLT-006", current.fullName() + " tiene "
                        + describe(running.size(), "envío activo", "envíos activos") + " (" + span(running)
                        + "): reasigna primero el conductor de esos envíos", "driverId");
            }
        }
        Profile p = accounts.setDriverActive(current.driver().profileId(), active);
        audit.record(tenant.profileId(), tenant.orgId(), active ? "DRIVER_REACTIVATED" : "DRIVER_DEACTIVATED",
                "driver", id, null);
        return account(current.driver(), p);
    }

    /** El ADMIN define una contraseña nueva para el conductor (F02); se cierran sus sesiones. */
    public DriverAccount resetDriverPassword(UUID id, String newPassword) {
        AccessDenied.requireOrgAdmin(tenant);
        DriverAccount current = ownDriver(id);
        Profile p = accounts.resetDriverPassword(current.driver().profileId(), newPassword);
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_PASSWORD_RESET", "driver", id, null);
        return account(current.driver(), p);
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
        return new DriverAccount(d, p == null ? null : p.fullName(), p == null ? null : p.email(),
                p != null && p.active());
    }
}
