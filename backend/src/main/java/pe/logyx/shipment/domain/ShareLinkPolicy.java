package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.ShareLink;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reglas del enlace público de seguimiento (roadmap 1.6, ADR-0011):
 * <ul>
 *   <li>Solo la PYME dueña del envío lo crea; un envío cancelado no se comparte (LOGYX-SHL-002) y uno cerrado hace
 *       más de {@link #CLOSED_GRACE} tampoco (LOGYX-SHL-003).</li>
 *   <li>Caducidad opcional de 1 a {@link #MAX_DAYS} días; en todo caso el enlace vence {@link #CLOSED_GRACE}
 *       después de entregarse (o cancelarse) el envío.</li>
 *   <li>Hasta {@link #MAX_ACTIVE} enlaces vigentes por envío (LOGYX-SHL-004): revoca alguno para crear otro.</li>
 *   <li>Revocado o vencido: la página pública responde igual que un token inexistente (LOGYX-SHL-001), así no
 *       se distingue un enlace que existió de uno inventado.</li>
 * </ul>
 */
public final class ShareLinkPolicy {

    public static final Duration CLOSED_GRACE = Duration.ofDays(7);
    public static final int MAX_DAYS = 30;
    public static final int MAX_ACTIVE = 10;

    private ShareLinkPolicy() {}

    /** Cuándo deja de valer: la fecha elegida o el cierre del envío + 7 días, lo que ocurra primero (null = abierta). */
    public static OffsetDateTime effectiveExpiry(ShareLink link, Shipment s) {
        OffsetDateTime closedAt = closedAt(s);
        OffsetDateTime byClose = closedAt == null ? null : closedAt.plus(CLOSED_GRACE);
        if (link.expiresAt() == null) return byClose;
        if (byClose == null) return link.expiresAt();
        return link.expiresAt().isBefore(byClose) ? link.expiresAt() : byClose;
    }

    public static ShareLink.Status status(ShareLink link, Shipment s, OffsetDateTime now) {
        if (link.revokedAt() != null) return ShareLink.Status.REVOKED;
        OffsetDateTime until = effectiveExpiry(link, s);
        return until != null && !now.isBefore(until) ? ShareLink.Status.EXPIRED : ShareLink.Status.ACTIVE;
    }

    /** Valida la creación y devuelve la caducidad propia (null si no se eligió). */
    public static OffsetDateTime ensureCanCreate(Shipment s, List<ShareLink> existing, Integer expiresInDays,
                                                 OffsetDateTime now) {
        if (s.status() == Shipment.Status.CANCELLED) {
            throw BusinessException.conflict("LOGYX-SHL-002", "Un envío cancelado no se puede compartir");
        }
        OffsetDateTime closedAt = closedAt(s);
        if (closedAt != null && !now.isBefore(closedAt.plus(CLOSED_GRACE))) {
            throw BusinessException.conflict("LOGYX-SHL-003",
                    "El envío se entregó hace más de 7 días: el seguimiento ya no se comparte");
        }
        if (expiresInDays != null && (expiresInDays < 1 || expiresInDays > MAX_DAYS)) {
            throw BusinessException.badRequest("LOGYX-VAL-001",
                    "La caducidad debe ser de 1 a " + MAX_DAYS + " días", "expiresInDays");
        }
        long active = existing.stream().filter(l -> status(l, s, now) == ShareLink.Status.ACTIVE).count();
        if (active >= MAX_ACTIVE) {
            throw BusinessException.conflict("LOGYX-SHL-004",
                    "Este envío ya tiene " + MAX_ACTIVE + " enlaces vigentes: revoca alguno para crear otro");
        }
        return expiresInDays == null ? null : now.plusDays(expiresInDays);
    }

    private static OffsetDateTime closedAt(Shipment s) {
        if (s.status() == Shipment.Status.DELIVERED) return s.deliveredAt();
        if (s.status() == Shipment.Status.CANCELLED) {
            return s.cancellation() != null && s.cancellation().at() != null ? s.cancellation().at() : s.createdAt();
        }
        return null;
    }
}
