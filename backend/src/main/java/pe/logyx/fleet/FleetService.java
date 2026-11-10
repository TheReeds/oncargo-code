package pe.logyx.fleet;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*;
import pe.logyx.organization.Organization;
import pe.logyx.organization.Profile;
import pe.logyx.shared.api.BusinessException;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shared.tenancy.TenantContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Transactional
public class FleetService {

    public record VehicleRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9]{3}-?[A-Z0-9]{3}", message = "Placa inválida (ej. ABC-123)") String plate,
            @NotNull Vehicle.Type type,
            @NotNull @DecimalMin(value = "100", message = "Capacidad mínima 100 kg") BigDecimal maxWeightKg,
            @DecimalMin("0.1") BigDecimal maxVolumeM3) {}

    public record VehicleView(UUID id, String plate, Vehicle.Type type, BigDecimal maxWeightKg,
                              BigDecimal maxVolumeM3, Vehicle.Status status) {
        static VehicleView of(Vehicle v) {
            return new VehicleView(v.id, v.plate, v.type, v.maxWeightKg, v.maxVolumeM3, v.status);
        }
    }

    public record DriverRequest(@NotBlank @Size(max = 150) String fullName,
                                @NotBlank @Email String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Size(max = 20) String licenseNumber) {}

    public record DriverView(UUID id, String fullName, String email, String licenseNumber, Driver.Status status) {
        static DriverView of(Driver d) {
            return new DriverView(d.id, d.profile.fullName, d.profile.email, d.licenseNumber, d.status);
        }
    }

    @Inject TenantContext tenant;
    @Inject AuditService audit;
    @Inject SystemTx system;

    public List<VehicleView> vehicles() {
        // El WHERE es la primera barrera; RLS (vehicles_owner) la segunda.
        return Vehicle.<Vehicle>list("organizationId = ?1 order by plate", tenant.orgId())
                .stream().map(VehicleView::of).toList();
    }

    public VehicleView addVehicle(VehicleRequest req) {
        String plate = normalizePlate(req.plate());
        // Unicidad global: con RLS solo veríamos las placas propias.
        if (system.call(() -> Vehicle.count("plate", plate) > 0)) {
            throw BusinessException.conflict("LOGYX-FLT-001", "La placa ya está registrada");
        }
        Vehicle v = new Vehicle();
        v.organizationId = tenant.orgId();
        v.plate = plate;
        v.type = req.type();
        v.maxWeightKg = req.maxWeightKg();
        v.maxVolumeM3 = req.maxVolumeM3();
        v.persist();
        return VehicleView.of(v);
    }

    public VehicleView setVehicleStatus(UUID id, Vehicle.Status status) {
        Vehicle v = ownVehicle(id);
        if (v.status == Vehicle.Status.IN_ROUTE || status == Vehicle.Status.IN_ROUTE) {
            throw BusinessException.conflict("LOGYX-FLT-002", "El estado 'en ruta' lo controla el envío");
        }
        v.status = status;
        return VehicleView.of(v);
    }

    public List<DriverView> drivers() {
        return Driver.<Driver>list("organizationId = ?1 order by createdAt", tenant.orgId())
                .stream().map(DriverView::of).toList();
    }

    /** Crea la cuenta del conductor (rol DRIVER) dentro de la organización transportista. */
    public DriverView addDriver(DriverRequest req) {
        if (system.call(() -> Profile.emailTaken(req.email()))) {
            throw BusinessException.conflict("LOGYX-AUTH-002", "El email ya está registrado");
        }
        Profile p = new Profile();
        p.organization = Organization.findById(tenant.orgId());
        p.email = req.email().trim().toLowerCase();
        p.passwordHash = BcryptUtil.bcryptHash(req.password());
        p.fullName = req.fullName().trim();
        p.role = Profile.Role.DRIVER;
        // El conductor confirma el consentimiento en su primer ingreso a la app;
        // aquí lo registra el administrador del transportista que lo da de alta.
        p.consentAcceptedAt = OffsetDateTime.now();
        p.persist();

        Driver d = new Driver();
        d.organizationId = tenant.orgId();
        d.profile = p;
        d.licenseNumber = req.licenseNumber().trim().toUpperCase();
        d.persist();
        audit.record(tenant.profileId(), tenant.orgId(), "DRIVER_CREATED", "driver", d.id, null);
        return DriverView.of(d);
    }

    public Vehicle ownVehicle(UUID id) {
        return Vehicle.<Vehicle>find("id = ?1 and organizationId = ?2", id, tenant.orgId())
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Vehículo"));
    }

    public Driver ownDriver(UUID id) {
        return Driver.<Driver>find("id = ?1 and organizationId = ?2", id, tenant.orgId())
                .firstResultOptional().orElseThrow(() -> BusinessException.notFound("Conductor"));
    }

    static String normalizePlate(String plate) {
        String p = plate.toUpperCase().replace("-", "");
        return p.substring(0, 3) + "-" + p.substring(3);
    }
}
