package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.ShareLink;
import pe.logyx.shipment.domain.model.ShareToken;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Roadmap 1.6 / ADR-0011: caducidad, revocación, límites y token. */
class ShareLinkPolicyTest {

    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-18T10:00:00-05:00");

    private Shipment shipment(Shipment.Status status, OffsetDateTime deliveredAt) {
        return new Shipment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, new BigDecimal("1500"), "ABC-123", null, status, now.minusDays(3), deliveredAt,
                null, status == Shipment.Status.CANCELLED ? new Shipment.Cancellation(UUID.randomUUID(), "x", now.minusDays(1)) : null);
    }

    private ShareLink link(Shipment s, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
        return new ShareLink(UUID.randomUUID(), s.id(), s.requesterOrgId(), "h", "abcd", null, now.minusDays(1),
                expiresAt, revokedAt, null, 0);
    }

    @Test
    void abiertoSinFechaNoVenceYConFechaVenceEnEsaFecha() {
        Shipment s = shipment(Shipment.Status.IN_TRANSIT, null);
        assertNull(ShareLinkPolicy.effectiveExpiry(link(s, null, null), s));
        assertEquals(ShareLink.Status.ACTIVE, ShareLinkPolicy.status(link(s, null, null), s, now));
        assertEquals(ShareLink.Status.EXPIRED, ShareLinkPolicy.status(link(s, now.minusMinutes(1), null), s, now));
        assertEquals(ShareLink.Status.REVOKED, ShareLinkPolicy.status(link(s, null, now.minusHours(1)), s, now));
    }

    @Test
    void entregadoVenceSieteDiasDespuesOAntesSiLaFechaPropiaEsMenor() {
        Shipment s = shipment(Shipment.Status.DELIVERED, now.minusDays(2));
        assertEquals(now.plusDays(5), ShareLinkPolicy.effectiveExpiry(link(s, null, null), s));
        assertEquals(now.plusDays(1), ShareLinkPolicy.effectiveExpiry(link(s, now.plusDays(1), null), s));
        Shipment old = shipment(Shipment.Status.DELIVERED, now.minusDays(8));
        assertEquals(ShareLink.Status.EXPIRED, ShareLinkPolicy.status(link(old, null, null), old, now));
    }

    @Test
    void creacionValidaEstadoDiasYMaximoVigentes() {
        Shipment open = shipment(Shipment.Status.PENDING, null);
        assertNull(ShareLinkPolicy.ensureCanCreate(open, List.of(), null, now));
        assertEquals(now.plusDays(30), ShareLinkPolicy.ensureCanCreate(open, List.of(), 30, now));
        assertEquals("LOGYX-VAL-001", assertThrows(BusinessException.class,
                () -> ShareLinkPolicy.ensureCanCreate(open, List.of(), 0, now)).code());
        assertEquals("LOGYX-SHL-002", assertThrows(BusinessException.class,
                () -> ShareLinkPolicy.ensureCanCreate(shipment(Shipment.Status.CANCELLED, null), List.of(), null, now)).code());
        assertEquals("LOGYX-SHL-003", assertThrows(BusinessException.class,
                () -> ShareLinkPolicy.ensureCanCreate(shipment(Shipment.Status.DELIVERED, now.minusDays(7)), List.of(),
                        null, now)).code());
        List<ShareLink> ten = new ArrayList<>();
        for (int i = 0; i < ShareLinkPolicy.MAX_ACTIVE; i++) ten.add(link(open, null, null));
        assertEquals("LOGYX-SHL-004", assertThrows(BusinessException.class,
                () -> ShareLinkPolicy.ensureCanCreate(open, ten, null, now)).code());
        ten.set(0, link(open, null, now));                                  // uno revocado libera un cupo
        assertDoesNotThrow(() -> ShareLinkPolicy.ensureCanCreate(open, ten, null, now));
    }

    @Test
    void tokenDe128BitsSoloSeGuardaSuHash() {
        var a = ShareToken.issue();
        var b = ShareToken.issue();
        assertEquals(22, a.token().length());
        assertTrue(ShareToken.isWellFormed(a.token()));
        assertNotEquals(a.token(), b.token());
        assertEquals(64, a.hash().length());
        assertEquals(ShareToken.hash(a.token()), a.hash());
        assertFalse(a.hash().contains(a.token()));
        assertEquals(a.token().substring(18), a.hint());
        assertFalse(ShareToken.isWellFormed("abc"));
        assertFalse(ShareToken.isWellFormed("AAAAAAAAAAAAAAAAAAAA/="));
        assertFalse(ShareToken.isWellFormed(null));
    }
}
