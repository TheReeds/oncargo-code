package pe.logyx.marketplace.domain;

import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * RN10: el operador solo asigna manualmente un transportista a una solicitud publicada sin ofertas vigentes que
 * lleva más de {@code sla} (24 h por defecto, {@code logyx.operator.sla}) publicada <b>o</b> cuya subasta ya cerró
 * sin ofertas (F07: la subasta de 20 min, RN04, dejaba ~23 h 40 min sin que nadie pudiera actuar).
 */
public final class ManualAssignmentPolicy {

    private ManualAssignmentPolicy() {}

    public static boolean slaBreached(ShipmentRequest r, long bidCount, OffsetDateTime now, Duration sla) {
        return slaBreached(r, bidCount, false, now, sla);
    }

    public static boolean slaBreached(ShipmentRequest r, long bidCount, boolean auctionClosed, OffsetDateTime now,
                                      Duration sla) {
        return r.isPublished() && bidCount == 0 && (auctionClosed || !r.createdAt().plus(sla).isAfter(now));
    }

    public static void ensureSlaBreached(ShipmentRequest r, long bidCount, OffsetDateTime now, Duration sla) {
        ensureSlaBreached(r, bidCount, false, now, sla);
    }

    public static void ensureSlaBreached(ShipmentRequest r, long bidCount, boolean auctionClosed, OffsetDateTime now,
                                         Duration sla) {
        if (!slaBreached(r, bidCount, auctionClosed, now, sla)) {
            throw BusinessException.conflict("LOGYX-OPS-001", "Solo se asigna manualmente una solicitud publicada sin "
                    + "ofertas cuya subasta ya cerró o que lleva " + sla.toHours() + " h o más publicada (RN10)");
        }
    }
}
