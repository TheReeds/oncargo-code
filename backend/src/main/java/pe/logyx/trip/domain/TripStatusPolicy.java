package pe.logyx.trip.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.trip.domain.model.TripStatus;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static pe.logyx.trip.domain.model.TripStatus.*;

/**
 * Máquina de estados del viaje:
 * <pre>
 * OPEN ──(1.ª reserva confirmada)──▶ FILLING ──(se llena / cierre manual)──▶ CLOSED ──(salida + 24 h)──▶ COMPLETED
 *   └──(cierre manual)──────────────────────────────▶ CLOSED
 * OPEN | FILLING | CLOSED ──(cancelar)──▶ CANCELLED
 * FILLING ──(se libera todo el espacio confirmado)──▶ OPEN
 * </pre>
 */
public final class TripStatusPolicy {

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED = Map.of(
            OPEN, EnumSet.of(FILLING, CLOSED, CANCELLED),
            FILLING, EnumSet.of(OPEN, CLOSED, CANCELLED),
            CLOSED, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(TripStatus.class),
            CANCELLED, EnumSet.noneOf(TripStatus.class));

    private TripStatusPolicy() {}

    public static boolean canTransition(TripStatus from, TripStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void ensureTransition(TripStatus from, TripStatus to) {
        if (!canTransition(from, to)) {
            throw BusinessException.conflict("LOGYX-TRP-005",
                    "El viaje no puede pasar de " + from + " a " + to, "status");
        }
    }

    /** Solo OPEN y FILLING reciben reservas nuevas. */
    public static boolean acceptsBookings(TripStatus status) {
        return status == OPEN || status == FILLING;
    }

    /** Estado tras confirmar una reserva: la primera pasa a FILLING; si no queda espacio, CLOSED. */
    public static TripStatus afterConfirmation(TripStatus current, BigDecimal remainingKg) {
        if (remainingKg.signum() <= 0) return CLOSED;
        return current == OPEN ? FILLING : current;
    }

    /** Estado tras liberar espacio (reserva confirmada cancelada): sin nada confirmado, un FILLING vuelve a OPEN. */
    public static TripStatus afterRelease(TripStatus current, BigDecimal confirmedKg) {
        return current == FILLING && confirmedKg.signum() == 0 ? OPEN : current;
    }
}
