package pe.logyx.marketplace.domain;

import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * RN10: el operador solo asigna manualmente un transportista a una solicitud publicada que lleva más de
 * {@code sla} (24 h por defecto, {@code logyx.operator.sla}) sin recibir ofertas.
 */
public final class ManualAssignmentPolicy {

    private ManualAssignmentPolicy() {}

    public static boolean slaBreached(ShipmentRequest r, long bidCount, OffsetDateTime now, Duration sla) {
        return r.isPublished() && bidCount == 0 && !r.createdAt().plus(sla).isAfter(now);
    }

    public static void ensureSlaBreached(ShipmentRequest r, long bidCount, OffsetDateTime now, Duration sla) {
        if (!slaBreached(r, bidCount, now, sla)) {
            throw BusinessException.conflict("LOGYX-OPS-001", "Solo se asigna manualmente una solicitud publicada con "
                    + sla.toHours() + " h o más sin ofertas (RN10)");
        }
    }
}
