package pe.logyx.shipment.infrastructure.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.logyx.shipment.domain.model.ShareLink;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code shipment_share_links} (V19). Solo se inserta con la entidad; las actualizaciones (revocar, contar visitas) van
 * por sentencias propias en {@link PanacheShareLinkRepository}: la RLS solo deja a la PYME tocar {@code revoked_*}.
 */
@Entity
@Table(name = "shipment_share_links")
public class ShareLinkEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "shipment_id")
    public UUID shipmentId;

    @Column(name = "requester_org_id")
    public UUID requesterOrgId;

    @Column(name = "token_hash")
    public String tokenHash;

    @Column(name = "token_hint")
    public String tokenHint;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "expires_at")
    public OffsetDateTime expiresAt;

    @Column(name = "revoked_at", insertable = true, updatable = false)
    public OffsetDateTime revokedAt;

    @Column(name = "last_viewed_at", updatable = false)
    public OffsetDateTime lastViewedAt;

    @Column(updatable = false)
    public int views;

    ShareLink toDomain() {
        return new ShareLink(id, shipmentId, requesterOrgId, tokenHash, tokenHint, createdBy, createdAt, expiresAt,
                revokedAt, lastViewedAt, views);
    }

    static ShareLinkEntity of(ShareLink l) {
        ShareLinkEntity e = new ShareLinkEntity();
        e.id = l.id();
        e.shipmentId = l.shipmentId();
        e.requesterOrgId = l.requesterOrgId();
        e.tokenHash = l.tokenHash();
        e.tokenHint = l.tokenHint();
        e.createdBy = l.createdBy();
        e.createdAt = l.createdAt();
        e.expiresAt = l.expiresAt();
        e.revokedAt = l.revokedAt();
        e.lastViewedAt = l.lastViewedAt();
        e.views = l.views();
        return e;
    }
}
