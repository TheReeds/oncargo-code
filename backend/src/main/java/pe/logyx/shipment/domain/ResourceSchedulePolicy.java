package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * F11: un conductor o un vehículo no se comprometen en dos envíos activos (PENDING / IN_TRANSIT) que se solapan.
 *
 * <p><b>Ventana de ocupación</b> de un envío: desde la fecha de recojo hasta recojo + días de viaje, con
 * días = ⌈distancia / {@value #KM_PER_DAY} km⌉ (mínimo 1). Un envío activo cuya ventana ya pasó (recojo atrasado o
 * viaje más largo de lo estimado) sigue ocupando hasta hoy: mientras no se entregue o cancele, el recurso no se libera.
 *
 * <p><b>Excepción, carga consolidada</b>: varios envíos del mismo viaje publicado o del mismo plan Smart Load
 * ({@code loadGroupId} igual, no nulo) comparten el vehículo; y su conductor puede llevarlos todos si van en ese
 * mismo vehículo.
 *
 * <p>La aplicación serializa la comprobación por recurso (bloqueo consultivo por vehículo/conductor) para que dos
 * asignaciones concurrentes no pasen las dos.
 */
public final class ResourceSchedulePolicy {

    /** Kilómetros que un camión recorre por día en el corredor (estimación conservadora). */
    public static final int KM_PER_DAY = 500;

    /** Días calendario [from, to], ambos inclusive. */
    public record Window(LocalDate from, LocalDate to) {
        public boolean overlaps(Window o) {
            return !from.isAfter(o.to) && !o.from.isAfter(to);
        }
    }

    /** Un envío activo que ya ocupa un recurso. */
    public record Commitment(UUID shipmentId, UUID vehicleId, UUID driverId, UUID loadGroupId, Window window) {}

    private ResourceSchedulePolicy() {}

    public static Window window(LocalDate pickupDate, BigDecimal distanceKm, LocalDate today) {
        long days = distanceKm == null ? 1 : Math.max(1, distanceKm
                .divide(BigDecimal.valueOf(KM_PER_DAY), 0, RoundingMode.CEILING).longValue());
        LocalDate to = pickupDate.plusDays(days);
        if (to.isBefore(today)) to = today;
        return new Window(pickupDate, to);
    }

    public static Optional<Commitment> vehicleConflict(List<Commitment> active, UUID vehicleId, Window window,
                                                       UUID loadGroupId, UUID excludeShipmentId) {
        return active.stream()
                .filter(c -> vehicleId.equals(c.vehicleId()) && !c.shipmentId().equals(excludeShipmentId))
                .filter(c -> !sameGroup(loadGroupId, c.loadGroupId()))
                .filter(c -> c.window().overlaps(window))
                .findFirst();
    }

    public static Optional<Commitment> driverConflict(List<Commitment> active, UUID driverId, UUID vehicleId,
                                                      Window window, UUID loadGroupId, UUID excludeShipmentId) {
        return active.stream()
                .filter(c -> driverId.equals(c.driverId()) && !c.shipmentId().equals(excludeShipmentId))
                .filter(c -> !(sameGroup(loadGroupId, c.loadGroupId()) && Objects.equals(vehicleId, c.vehicleId())))
                .filter(c -> c.window().overlaps(window))
                .findFirst();
    }

    /** LOGYX-CAP-005 (409, {@code vehicleId}). */
    public static void ensureVehicleFree(List<Commitment> active, UUID vehicleId, Window window, UUID loadGroupId,
                                         UUID excludeShipmentId) {
        vehicleConflict(active, vehicleId, window, loadGroupId, excludeShipmentId).ifPresent(c -> {
            throw BusinessException.conflict("LOGYX-CAP-005", "El vehículo ya está comprometido en otro envío entre el "
                    + c.window().from() + " y el " + c.window().to() + ": elige otro vehículo", "vehicleId");
        });
    }

    /** LOGYX-SHP-004 (409, {@code driverId}). */
    public static void ensureDriverFree(List<Commitment> active, UUID driverId, UUID vehicleId, Window window,
                                        UUID loadGroupId, UUID excludeShipmentId) {
        driverConflict(active, driverId, vehicleId, window, loadGroupId, excludeShipmentId).ifPresent(c -> {
            throw BusinessException.conflict("LOGYX-SHP-004", "El conductor ya tiene otro envío activo entre el "
                    + c.window().from() + " y el " + c.window().to() + ": elige otro conductor", "driverId");
        });
    }

    private static boolean sameGroup(UUID a, UUID b) {
        return a != null && a.equals(b);
    }
}
