package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;
import pe.logyx.shared.tenancy.TenantContext;
import pe.logyx.shipment.application.port.out.ShareLinkRepository;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.domain.ShareLinkPolicy;
import pe.logyx.shipment.domain.model.ShareLink;
import pe.logyx.shipment.domain.model.ShareToken;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Enlaces públicos de seguimiento que la PYME comparte con su cliente final (roadmap 1.6, ADR-0011). Todo corre bajo
 * la RLS de la PYME ({@code share_links_owner_*}): otra organización no ve ni revoca sus enlaces. El token en claro solo
 * existe en la respuesta de {@link #create}; después solo se conoce su hash.
 */
@ApplicationScoped
public class ShareLinkService {

    /** Enlace con su estado y caducidad efectivos (calculados con el envío). */
    public record ShareLinkSummary(ShareLink link, ShareLink.Status status, OffsetDateTime effectiveExpiresAt) {}

    /** Lo que devuelve crear: el resumen y el token, que no se vuelve a mostrar. */
    public record Created(ShareLinkSummary summary, String token) {}

    @Inject ShareLinkRepository links;
    @Inject ShipmentRepository shipments;
    @Inject TenantContext tenant;
    @Inject AuditService audit;

    @Transactional
    public Created create(UUID shipmentId, Integer expiresInDays) {
        Shipment s = ownShipment(shipmentId);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = ShareLinkPolicy.ensureCanCreate(s, links.listByShipment(s.id()), expiresInDays, now);
        ShareToken.Issued token = ShareToken.issue();
        ShareLink link = ShareLink.issue(s, token, tenant.profileId(), now, expiresAt);
        links.insert(link);
        audit.record(tenant.profileId(), tenant.orgId(), "SHARE_LINK_CREATED", "shipment", s.id(),
                "enlace=…" + link.tokenHint() + (expiresInDays == null ? "" : " dias=" + expiresInDays));
        return new Created(summary(link, s, now), token.token());
    }

    @Transactional
    public List<ShareLinkSummary> list(UUID shipmentId) {
        Shipment s = ownShipment(shipmentId);
        OffsetDateTime now = OffsetDateTime.now();
        return links.listByShipment(s.id()).stream().map(l -> summary(l, s, now)).toList();
    }

    @Transactional
    public ShareLinkSummary revoke(UUID shipmentId, UUID linkId) {
        Shipment s = ownShipment(shipmentId);
        ShareLink link = links.findById(linkId).filter(l -> l.shipmentId().equals(s.id()))
                .orElseThrow(() -> BusinessException.notFound("Enlace"));
        OffsetDateTime now = OffsetDateTime.now();
        if (links.revoke(link.id(), tenant.profileId(), now)) {
            audit.record(tenant.profileId(), tenant.orgId(), "SHARE_LINK_REVOKED", "shipment", s.id(),
                    "enlace=…" + link.tokenHint());
        }
        ShareLink after = links.findById(linkId).orElse(link);
        return summary(after, s, now);
    }

    /** Solo la PYME dueña del envío (ni el transportista ni el operador generan enlaces públicos). */
    private Shipment ownShipment(UUID shipmentId) {
        if (tenant.orgType() != OrgType.PYME) throw BusinessException.notFound("Envío");
        return shipments.findById(shipmentId).filter(s -> s.requesterOrgId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Envío"));
    }

    private static ShareLinkSummary summary(ShareLink l, Shipment s, OffsetDateTime now) {
        return new ShareLinkSummary(l, ShareLinkPolicy.status(l, s, now), ShareLinkPolicy.effectiveExpiry(l, s));
    }
}
