package pe.logyx.shipment.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Enlace público de seguimiento de un envío (roadmap 1.6, ADR-0011). Lo crea la PYME para su cliente final. El token
 * nunca se guarda: solo su SHA-256 ({@code tokenHash}) y 4 caracteres para reconocerlo en la lista ({@code tokenHint}).
 * {@code expiresAt}: caducidad elegida al crear (null = sin fecha propia); además vence al cerrarse el envío más un
 * plazo de gracia ({@link pe.logyx.shipment.domain.ShareLinkPolicy}).
 */
public record ShareLink(UUID id, UUID shipmentId, UUID requesterOrgId, String tokenHash, String tokenHint,
                        UUID createdBy, OffsetDateTime createdAt, OffsetDateTime expiresAt,
                        OffsetDateTime revokedAt, OffsetDateTime lastViewedAt, int views) {

    public enum Status { ACTIVE, EXPIRED, REVOKED }

    public static ShareLink issue(Shipment s, ShareToken.Issued token, UUID createdBy, OffsetDateTime now,
                                  OffsetDateTime expiresAt) {
        return new ShareLink(UUID.randomUUID(), s.id(), s.requesterOrgId(), token.hash(), token.hint(), createdBy,
                now, expiresAt, null, null, 0);
    }
}
