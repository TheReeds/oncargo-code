package pe.logyx.shipment.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.AdjustmentPolicy;
import pe.logyx.shipment.domain.model.AdjustmentType;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentAdjustment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdjustmentPolicyTest {

    private static Shipment shipment(Shipment.Status status, OffsetDateTime deliveredAt) {
        return new Shipment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, new BigDecimal("1180.00"), "ABC-123", null, status,
                OffsetDateTime.now().minusDays(10), deliveredAt, null, null, new BigDecimal("0.08"));
    }

    private static String code(Runnable r) {
        return assertThrows(BusinessException.class, r::run).code();
    }

    @Test
    void validaMontoTipoNotaYPlazo() {
        var now = OffsetDateTime.now();
        var s = shipment(Shipment.Status.IN_TRANSIT, null);
        assertEquals("LOGYX-ADJ-003", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                BigDecimal.ZERO, null, 0, now)));
        assertEquals("LOGYX-ADJ-003", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                new BigDecimal("-5"), null, 0, now)));
        assertEquals("LOGYX-ADJ-003", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                new BigDecimal("1180.01"), null, 0, now)));
        assertEquals("LOGYX-ADJ-003", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                new BigDecimal("1.001"), null, 0, now)));
        assertEquals("LOGYX-ADJ-004", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.OTHER,
                BigDecimal.TEN, " ", 0, now)));
        assertEquals("LOGYX-ADJ-005", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                BigDecimal.TEN, null, 10, now)));
        assertEquals("LOGYX-NEG-001", code(() -> AdjustmentPolicy.validateProposal(s, AdjustmentType.TOLL,
                BigDecimal.TEN, "escríbeme a peaje@correo.pe", 0, now)));
        assertNull(AdjustmentPolicy.validateProposal(s, AdjustmentType.WEIGHT_DIFF, new BigDecimal("-50.50"), null, 0, now));
        assertEquals("LOGYX-ADJ-001", code(() -> AdjustmentPolicy.validateProposal(
                shipment(Shipment.Status.CANCELLED, null), AdjustmentType.TOLL, BigDecimal.TEN, null, 0, now)));
        assertEquals("LOGYX-ADJ-002", code(() -> AdjustmentPolicy.validateProposal(
                shipment(Shipment.Status.DELIVERED, now.minusDays(8)), AdjustmentType.TOLL, BigDecimal.TEN, null, 0, now)));
        assertDoesNotThrow(() -> AdjustmentPolicy.validateProposal(shipment(Shipment.Status.DELIVERED, now.minusDays(6)),
                AdjustmentType.TOLL, BigDecimal.TEN, null, 0, now));
    }

    @Test
    void decideLaPymeAntesDeEscalarYElOperadorDespues() {
        var now = OffsetDateTime.now();
        var a = ShipmentAdjustment.propose(shipment(Shipment.Status.IN_TRANSIT, null), null, AdjustmentType.TOLL,
                BigDecimal.TEN, null, null, UUID.randomUUID(), now.minusHours(50), Duration.ofHours(48));
        assertDoesNotThrow(() -> AdjustmentPolicy.ensureCanDecide(a, AdjustmentPolicy.Decider.REQUESTER));
        assertEquals("LOGYX-ADJ-006", code(() -> AdjustmentPolicy.ensureCanDecide(a, AdjustmentPolicy.Decider.OPERATOR)));
        assertTrue(AdjustmentPolicy.dueForEscalation(a, now));
        assertTrue(a.escalate(now));
        assertFalse(a.escalate(now), "idempotente");
        assertFalse(AdjustmentPolicy.dueForEscalation(a, now));
        assertEquals("LOGYX-ADJ-006", code(() -> AdjustmentPolicy.ensureCanDecide(a, AdjustmentPolicy.Decider.REQUESTER)));
        assertEquals("LOGYX-ADJ-004", code(() -> a.decide(false, UUID.randomUUID(), UUID.randomUUID(), null, now)));
        a.decide(true, UUID.randomUUID(), UUID.randomUUID(), null, now);
        assertEquals("LOGYX-ADJ-007", code(() -> a.withdraw(UUID.randomUUID(), UUID.randomUUID(), now)));
    }

    @Test
    void precioFinalYSugerencias() {
        var s = shipment(Shipment.Status.IN_TRANSIT, null);
        var now = OffsetDateTime.now();
        var ok = ShipmentAdjustment.propose(s, null, AdjustmentType.TOLL, new BigDecimal("118.00"), null, null, null,
                now, Duration.ofHours(48));
        ok.decide(true, UUID.randomUUID(), null, null, now);
        var pending = ShipmentAdjustment.propose(s, null, AdjustmentType.HANDLING, new BigDecimal("50.00"), null, null,
                null, now, Duration.ofHours(48));
        BigDecimal total = AdjustmentPolicy.acceptedTotal(List.of(ok, pending));
        assertEquals(new BigDecimal("118.00"), total);
        assertEquals(new BigDecimal("1298.00"), AdjustmentPolicy.finalPrice(s.agreedPrice(), total));

        // 3 h sin cargo, 5 h 10 min de espera → 2 h 10 min → 2.5 h × 40 = 100
        var d = AdjustmentPolicy.detention(now, now.plusMinutes(310), Duration.ofHours(3), new BigDecimal("40"));
        assertNotNull(d);
        assertEquals(new BigDecimal("2.5"), d.billableHours());
        assertEquals(new BigDecimal("100.00"), d.amount());
        assertNull(AdjustmentPolicy.detention(now, now.plusHours(3), Duration.ofHours(3), new BigDecimal("40")));

        assertNull(AdjustmentPolicy.weightDifference(new BigDecimal("2000"), new BigDecimal("2090"), s.agreedPrice()));
        assertEquals(new BigDecimal("295.00"), AdjustmentPolicy.weightDifference(new BigDecimal("2000"),
                new BigDecimal("2500"), s.agreedPrice()));
    }
}
