package pe.logyx.marketplace.domain;

import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cuándo la PYME puede adjudicar a la segunda oferta (roadmap 2.9): el envío adjudicado se canceló dentro de las
 * {@value #WINDOW_HOURS} h siguientes a la adjudicación (p. ej. el transportista no llegó), la solicitud quedó
 * cancelada por ese motivo y el recojo aún no pasó.
 */
public final class BackupAwardPolicy {

    public static final int WINDOW_HOURS = 24;
    public static final Duration WINDOW = Duration.ofHours(WINDOW_HOURS);

    private BackupAwardPolicy() {}

    /** Datos del último envío de la solicitud. {@code cancelledAt} nulo = no está cancelado. */
    public record AwardedShipment(OffsetDateTime createdAt, OffsetDateTime cancelledAt) {}

    /** Hasta cuándo se puede elegir la segunda oferta, o {@code null} si el envío no se canceló. */
    public static OffsetDateTime deadline(AwardedShipment s) {
        return s == null || s.cancelledAt() == null ? null : s.createdAt().plus(WINDOW);
    }

    /**
     * @throws BusinessException BCK-001 (no corresponde), BCK-002 (pasaron las 24 h), BCK-003 (el recojo ya pasó)
     */
    public static void ensureAwardable(ShipmentRequest.Status requestStatus, AwardedShipment shipment,
                                       LocalDate pickupDate, LocalDate today) {
        if (requestStatus != ShipmentRequest.Status.CANCELLED || shipment == null || shipment.cancelledAt() == null) {
            throw BusinessException.conflict("LOGYX-BCK-001",
                    "Solo se adjudica a la segunda oferta cuando el envío adjudicado se canceló");
        }
        if (shipment.cancelledAt().isAfter(shipment.createdAt().plus(WINDOW))) {
            throw BusinessException.conflict("LOGYX-BCK-002", "El envío se canceló pasadas las " + WINDOW_HOURS
                    + " h de la adjudicación: publica la carga de nuevo");
        }
        if (pickupDate.isBefore(today)) {
            throw BusinessException.conflict("LOGYX-BCK-003",
                    "La fecha de recojo ya pasó: publica la carga de nuevo con otra fecha");
        }
    }

    /** Variante que no lanza: el código del impedimento o {@code null} si se puede. */
    public static String blocker(ShipmentRequest.Status requestStatus, AwardedShipment shipment, LocalDate pickupDate,
                                 LocalDate today) {
        try {
            ensureAwardable(requestStatus, shipment, pickupDate, today);
            return null;
        } catch (BusinessException e) {
            return e.code();
        }
    }
}
