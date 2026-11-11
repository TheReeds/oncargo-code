package pe.logyx.trip.domain;

import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;

/**
 * RN12: un transportista no puede publicar un viaje con capacidad mayor a la de su flota (aquí, la del vehículo
 * elegido) y las reservas confirmadas descuentan la capacidad restante.
 */
public final class TripCapacityPolicy {

    private TripCapacityPolicy() {}

    /** RN12 al publicar: la capacidad ofrecida no puede superar la capacidad registrada del vehículo. */
    public static void ensurePublishable(BigDecimal offeredKg, BigDecimal vehicleMaxKg) {
        if (offeredKg.compareTo(vehicleMaxKg) > 0) {
            throw BusinessException.badRequest("LOGYX-TRP-001",
                    "La capacidad publicada (" + plain(offeredKg) + " kg) supera la capacidad verificada del vehículo ("
                            + plain(vehicleMaxKg) + " kg) (RN12)", "capacityKg");
        }
    }

    /** Capacidad restante = ofrecida − confirmada (nunca negativa). */
    public static BigDecimal remaining(BigDecimal offeredKg, BigDecimal confirmedKg) {
        return offeredKg.subtract(confirmedKg).max(BigDecimal.ZERO);
    }

    /** RF23 / RN12 al reservar y al confirmar: el peso debe caber en lo que queda. */
    public static void ensureFits(BigDecimal weightKg, BigDecimal remainingKg) {
        if (weightKg.compareTo(remainingKg) > 0) {
            throw BusinessException.conflict("LOGYX-TRP-004",
                    "El peso (" + plain(weightKg) + " kg) excede la capacidad restante del viaje ("
                            + plain(remainingKg) + " kg)", "weightKg");
        }
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
