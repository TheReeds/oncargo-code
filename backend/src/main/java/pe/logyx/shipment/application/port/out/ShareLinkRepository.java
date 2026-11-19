package pe.logyx.shipment.application.port.out;

import pe.logyx.shipment.domain.model.ShareLink;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Enlaces públicos de seguimiento (V19, {@code shipment_share_links}). */
public interface ShareLinkRepository {
    void insert(ShareLink link);
    /** Más recientes primero. */
    List<ShareLink> listByShipment(UUID shipmentId);
    Optional<ShareLink> findById(UUID id);
    /** Solo {@code revoked_at}/{@code revoked_by} (único UPDATE que la RLS permite a la PYME). false si ya estaba revocado. */
    boolean revoke(UUID id, UUID byProfileId, OffsetDateTime at);
    /** Resolución del enlace público: corre dentro de {@code SystemTx} (no hay usuario). */
    Optional<ShareLink> findByHash(String tokenHash);
    /** Cuenta una visita (dentro de {@code SystemTx}). */
    void recordView(UUID id, OffsetDateTime at);
}
