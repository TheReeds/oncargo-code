package pe.logyx.fleet.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RN02 por tipo de carga: qué tipos de vehículo pueden llevar cada {@link CargoType}. Es la matriz única del
 * backend; la usan ofertar y aceptar (marketplace), la asignación manual del operador, el cambio de vehículo de un
 * envío (shipment) y los viajes, retornos y Smart Load (trip, vía {@code VehicleCargoRules}).
 *
 * <pre>
 * Carga \ Vehículo   VAN  T_SMALL T_MEDIUM T_LARGE PLATFORM REFRIG TANKER
 * GENERAL             ✔     ✔       ✔        ✔       ✔        ·      ·
 * FRAGILE             ✔     ✔       ✔        ✔       ·        ·      ·     (caja cerrada: no plataforma)
 * PERISHABLE          ·     ·       ·        ·       ·        ✔      ·     (cadena de frío)
 * HAZARDOUS           ·     ·       ✔        ✔       ·        ·      ✔     (MATPEL: camión mediano/grande o cisterna)
 * BULK                ·     ✔       ✔        ✔       ✔        ·      ✔     (granel ensacado o líquido en cisterna)
 * OVERSIZED           ·     ·       ·        ·       ✔        ·      ·     (sobredimensionado: plataforma)
 * </pre>
 * El refrigerado se reserva a perecederos y la cisterna no lleva carga general (coherente con los viajes publicados).
 * Además, si la carga declara volumen y el vehículo su capacidad en m³, el volumen debe caber.
 */
public final class VehicleCargoCompatibility {

    private static final Map<CargoType, Set<Vehicle.Type>> ALLOWED = new EnumMap<>(CargoType.class);

    static {
        ALLOWED.put(CargoType.GENERAL, EnumSet.of(Vehicle.Type.VAN, Vehicle.Type.TRUCK_SMALL, Vehicle.Type.TRUCK_MEDIUM,
                Vehicle.Type.TRUCK_LARGE, Vehicle.Type.PLATFORM));
        ALLOWED.put(CargoType.FRAGILE, EnumSet.of(Vehicle.Type.VAN, Vehicle.Type.TRUCK_SMALL, Vehicle.Type.TRUCK_MEDIUM,
                Vehicle.Type.TRUCK_LARGE));
        ALLOWED.put(CargoType.PERISHABLE, EnumSet.of(Vehicle.Type.REFRIGERATED));
        ALLOWED.put(CargoType.HAZARDOUS, EnumSet.of(Vehicle.Type.TRUCK_MEDIUM, Vehicle.Type.TRUCK_LARGE,
                Vehicle.Type.TANKER));
        ALLOWED.put(CargoType.BULK, EnumSet.of(Vehicle.Type.TRUCK_SMALL, Vehicle.Type.TRUCK_MEDIUM,
                Vehicle.Type.TRUCK_LARGE, Vehicle.Type.PLATFORM, Vehicle.Type.TANKER));
        ALLOWED.put(CargoType.OVERSIZED, EnumSet.of(Vehicle.Type.PLATFORM));
    }

    private VehicleCargoCompatibility() {}

    public static boolean canCarry(Vehicle.Type vehicle, CargoType cargo) {
        return ALLOWED.getOrDefault(cargo, Set.of()).contains(vehicle);
    }

    /** Tipos de vehículo aptos para la carga, en el orden del enum (para mensajes y clientes). */
    public static List<Vehicle.Type> allowedFor(CargoType cargo) {
        return List.copyOf(ALLOWED.getOrDefault(cargo, Set.of()));
    }

    /** El volumen declarado cabe (si alguno de los dos no lo declara, no se puede comprobar y se acepta). */
    public static boolean volumeFits(BigDecimal vehicleMaxVolumeM3, BigDecimal cargoVolumeM3) {
        return vehicleMaxVolumeM3 == null || cargoVolumeM3 == null || vehicleMaxVolumeM3.compareTo(cargoVolumeM3) >= 0;
    }

    /**
     * RN02 completa para un vehículo concreto (errores en {@code vehicleId}): disponible (LOGYX-CAP-002), capacidad
     * en kg (LOGYX-CAP-001), tipo de vehículo apto para el tipo de carga (LOGYX-CAP-003) y volumen (LOGYX-CAP-004).
     */
    public static void ensureSuitable(Vehicle v, CargoType cargo, BigDecimal weightKg, BigDecimal volumeM3) {
        if (!v.isAvailable()) {
            throw BusinessException.conflict("LOGYX-CAP-002", "El vehículo no está disponible", "vehicleId");
        }
        if (weightKg != null && v.maxWeightKg().compareTo(weightKg) < 0) {
            throw BusinessException.badRequest("LOGYX-CAP-001", "El vehículo no tiene capacidad suficiente (RN02)",
                    "vehicleId");
        }
        ensureTypeAndVolume(v.type(), v.maxVolumeM3(), cargo, volumeM3);
    }

    /**
     * RN02 por fecha (decisión del dueño, 2026-11-17): para <b>ofertar</b> el vehículo no tiene que estar libre "ahora"
     * sino en la fecha de recojo. Aquí solo bloquea el mantenimiento o el archivo (LOGYX-CAP-002); "en ruta" se
     * resuelve con la agenda (envíos activos, {@code ResourceSchedulePolicy}). Después, kg, tipo y volumen como en
     * {@link #ensureSuitable}.
     */
    public static void ensureSuitableOnDate(Vehicle v, CargoType cargo, BigDecimal weightKg, BigDecimal volumeM3) {
        if (v.isBlockedForBids()) {
            throw BusinessException.conflict("LOGYX-CAP-002", v.isArchived()
                    ? "El vehículo está archivado y no puede ofertar"
                    : "El vehículo está en mantenimiento: márcalo disponible para ofertar", "vehicleId");
        }
        if (weightKg != null && v.maxWeightKg().compareTo(weightKg) < 0) {
            throw BusinessException.badRequest("LOGYX-CAP-001", "El vehículo no tiene capacidad suficiente (RN02)",
                    "vehicleId");
        }
        ensureTypeAndVolume(v.type(), v.maxVolumeM3(), cargo, volumeM3);
    }

    /** LOGYX-CAP-003 (tipo de vehículo no apto) y LOGYX-CAP-004 (volumen), con {@code field = vehicleId}. */
    public static void ensureTypeAndVolume(Vehicle.Type type, BigDecimal maxVolumeM3, CargoType cargo,
                                           BigDecimal volumeM3) {
        if (cargo != null && !canCarry(type, cargo)) {
            throw BusinessException.badRequest("LOGYX-CAP-003", "Un vehículo " + label(type) + " no puede llevar carga "
                    + "de tipo «" + cargo.label.toLowerCase() + "». Vehículos aptos: " + String.join(", ",
                    allowedFor(cargo).stream().map(VehicleCargoCompatibility::label).toList()) + " (RN02)", "vehicleId");
        }
        if (!volumeFits(maxVolumeM3, volumeM3)) {
            throw BusinessException.badRequest("LOGYX-CAP-004", "La carga ocupa " + volumeM3.stripTrailingZeros()
                    .toPlainString() + " m³ y el vehículo admite " + maxVolumeM3.stripTrailingZeros().toPlainString()
                    + " m³ (RN02)", "vehicleId");
        }
    }

    /** "refrigerado", "cisterna"…: etiqueta en español para mensajes. */
    public static String label(Vehicle.Type t) {
        return switch (t) {
            case VAN -> "furgoneta";
            case TRUCK_SMALL -> "camión pequeño";
            case TRUCK_MEDIUM -> "camión mediano";
            case TRUCK_LARGE -> "camión grande";
            case PLATFORM -> "plataforma";
            case REFRIGERATED -> "refrigerado";
            case TANKER -> "cisterna";
        };
    }
}
