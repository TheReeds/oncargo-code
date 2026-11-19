package pe.logyx.shipment.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * ETA aproximada para el seguimiento público (roadmap 1.6). Sin GPS: parte de la hora del último hito (el recojo
 * completado o la última entrega) y del tiempo de manejo por carretera que queda (dataset OSRM de {@code routing},
 * calculado para auto). Se ajusta para un camión ({@link #TRUCK_FACTOR}), se suman descansos ({@link #REST} cada
 * {@link #DRIVE_BLOCK} de manejo) y se entrega como <b>ventana</b> (+{@link #SPREAD}), redondeada a media hora.
 * Si la ventana ya pasó no se inventa otra: null (la página dice «Llegando»).
 */
public final class EtaPolicy {

    public record Window(OffsetDateTime from, OffsetDateTime to) {}

    public static final double TRUCK_FACTOR = 1.25;
    public static final double SPREAD = 0.35;
    public static final Duration DRIVE_BLOCK = Duration.ofHours(10);
    public static final Duration REST = Duration.ofHours(3);

    private EtaPolicy() {}

    /** {@code base}: hora del último hito; {@code remainingDriveMin}: minutos de manejo (auto) hasta el destino. */
    public static Window estimate(OffsetDateTime base, double remainingDriveMin, OffsetDateTime now) {
        if (base == null || remainingDriveMin <= 0) return null;
        long truck = Math.round(remainingDriveMin * TRUCK_FACTOR);
        long rests = truck / DRIVE_BLOCK.toMinutes();
        long total = truck + rests * REST.toMinutes();
        OffsetDateTime from = floorHalfHour(base.plusMinutes(total));
        OffsetDateTime to = ceilHalfHour(base.plusMinutes(Math.round(total * (1 + SPREAD))));
        if (!to.isAfter(now)) return null;
        if (from.isBefore(now)) from = ceilHalfHour(now);
        if (!to.isAfter(from)) to = from.plusMinutes(30);
        return new Window(from, to);
    }

    private static OffsetDateTime floorHalfHour(OffsetDateTime t) {
        OffsetDateTime h = t.truncatedTo(ChronoUnit.HOURS);
        return t.getMinute() >= 30 ? h.plusMinutes(30) : h;
    }

    private static OffsetDateTime ceilHalfHour(OffsetDateTime t) {
        OffsetDateTime f = floorHalfHour(t);
        return f.equals(t) ? f : f.plusMinutes(30);
    }
}
