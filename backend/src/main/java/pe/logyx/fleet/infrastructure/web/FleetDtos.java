package pe.logyx.fleet.infrastructure.web;

import jakarta.validation.constraints.*;
import pe.logyx.fleet.application.AddDriverCommand;
import pe.logyx.fleet.application.AddVehicleCommand;
import pe.logyx.fleet.application.DriverAccount;
import pe.logyx.fleet.application.UpdateVehicleCommand;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.fleet.domain.model.Vehicle;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Equipment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class FleetDtos {
    private FleetDtos() {}

    public record VehicleRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9]{3}-?[A-Z0-9]{3}", message = "Placa inválida (ej. ABC-123)") String plate,
            @NotNull Vehicle.Type type,
            @NotNull @DecimalMin(value = "100", message = "Capacidad mínima 100 kg") BigDecimal maxWeightKg,
            @DecimalMin("0.1") BigDecimal maxVolumeM3) {

        AddVehicleCommand toCommand() {
            return new AddVehicleCommand(plate, type, maxWeightKg, maxVolumeM3);
        }
    }

    /**
     * {@code equipment} y {@code acceptedCargoTypes} (2.4): lo segundo restringe la matriz RN02 para este vehículo
     * (vacío = todo lo que la matriz permite para su tipo).
     */
    public record VehicleView(UUID id, String plate, Vehicle.Type type, BigDecimal maxWeightKg,
                              BigDecimal maxVolumeM3, Vehicle.Status status, Set<Equipment> equipment,
                              Set<CargoType> acceptedCargoTypes) {
        static VehicleView of(Vehicle v) {
            return new VehicleView(v.id(), v.plate(), v.type(), v.maxWeightKg(), v.maxVolumeM3(), v.status(),
                    v.equipment(), v.acceptedCargoTypes());
        }
    }

    /** Configuración del vehículo (2.4): reemplaza el equipamiento y los tipos de carga aceptados (vacío = sin restricción). */
    public record VehicleConfig(Set<Equipment> equipment, Set<CargoType> acceptedCargoTypes) {}

    /** Categoría de licencia (p. ej. A-IIb) y DNI del conductor; el vencimiento y el archivo van como documento. */
    public record DriverPatch(@Size(max = 10) String licenseCategory, @Size(max = 12) String dni) {}

    /** Corrección del vehículo (la placa no cambia). Con compromisos solo se admite el volumen (409 LOGYX-FLT-004). */
    public record VehiclePatch(Vehicle.Type type,
                               @DecimalMin(value = "100", message = "Capacidad mínima 100 kg") BigDecimal maxWeightKg,
                               @DecimalMin("0.1") BigDecimal maxVolumeM3) {

        UpdateVehicleCommand toCommand() {
            return new UpdateVehicleCommand(type, maxWeightKg, maxVolumeM3);
        }
    }

    public record StatusChange(@NotNull Vehicle.Status status) {}

    public record DriverRequest(@NotBlank @Size(max = 150) String fullName,
                                @NotBlank @Email String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Size(max = 20) String licenseNumber) {

        AddDriverCommand toCommand() {
            return new AddDriverCommand(fullName, email, password, licenseNumber);
        }
    }

    /** {@code active} (V14): la cuenta del conductor puede iniciar sesión y recibir envíos. */
    public record DriverView(UUID id, String fullName, String email, String licenseNumber, Driver.Status status,
                             boolean active, String licenseCategory, LocalDate licenseExpiresAt, String dni) {
        static DriverView of(DriverAccount a) {
            Driver d = a.driver();
            return new DriverView(d.id(), a.fullName(), a.email(), d.licenseNumber(), d.status(), a.active(),
                    d.licenseCategory(), d.licenseExpiresAt(), d.dni());
        }
    }

    public record DriverPasswordReset(
            @NotBlank @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            String newPassword) {}
}
