package pe.logyx.settlement.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.settlement.domain.SettlementPolicy.Side;
import pe.logyx.settlement.domain.model.*;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Transiciones del cobro (2.12): quién puede qué, en qué estado, y bloqueo de ajustes. */
class SettlementPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T10:00:00-05:00");
    private static final UUID PYME = UUID.randomUUID();
    private static final UUID CARRIER = UUID.randomUUID();
    private static final BigDecimal PRICE = new BigDecimal("1180.00");

    private static Settlement fresh() {
        return Settlement.open(UUID.randomUUID(), PYME, CARRIER, TODAY.plusDays(30), true);
    }

    private static void invoice(Settlement s) {
        SettlementPolicy.invoice(s, Side.CARRIER, false, "F001-000123", TODAY, TODAY, null, PRICE, 0, TODAY, NOW, null);
    }

    private static void pay(Settlement s) {
        SettlementPolicy.pay(s, Side.REQUESTER, PaymentMethod.TRANSFER, "123456", TODAY, PRICE, TODAY, null, 0, TODAY,
                NOW, null);
    }

    private static String code(Runnable r) {
        return assertThrows(BusinessException.class, r::run).code();
    }

    @Test
    void elFlujoNormalFacturaPagaYConfirma() {
        Settlement s = fresh();
        assertFalse(SettlementPolicy.locksAdjustments(s.status()));
        invoice(s);
        assertEquals(SettlementStatus.INVOICED, s.status());
        assertEquals(0, PRICE.compareTo(s.invoice().amount()));
        assertTrue(SettlementPolicy.locksAdjustments(s.status()), "facturado: el precio no cambia");
        invoice(s);                                                                    // corrige antes del pago
        pay(s);
        assertEquals(SettlementStatus.PAID, s.status());
        SettlementPolicy.confirm(s, Side.CARRIER, false, NOW, null);
        assertEquals(SettlementStatus.CONFIRMED, s.status());
        assertEquals("LOGYX-STL-002", code(() -> SettlementPolicy.confirm(s, Side.CARRIER, false, NOW, null)));
    }

    @Test
    void cadaPasoLoDaSuParte() {
        Settlement s = fresh();
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.invoice(s, Side.REQUESTER, false, "F1", TODAY, TODAY,
                null, PRICE, 0, TODAY, NOW, null)));
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.invoice(s, Side.CARRIER, true, "F1", TODAY, TODAY,
                null, PRICE, 0, TODAY, NOW, null)), "el conductor no factura");
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.pay(s, Side.CARRIER, PaymentMethod.CASH, null, TODAY,
                PRICE, TODAY, null, 0, TODAY, NOW, null)));
        pay(s);                                                                         // la PYME puede pagar sin factura
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.confirm(s, Side.REQUESTER, false, NOW, null)));
    }

    @Test
    void conAjustesPendientesNoSeFacturaNiSePaga() {
        Settlement s = fresh();
        assertEquals("LOGYX-STL-003", code(() -> SettlementPolicy.invoice(s, Side.CARRIER, false, "F1", TODAY, TODAY,
                null, PRICE, 1, TODAY, NOW, null)));
        assertEquals("LOGYX-STL-003", code(() -> SettlementPolicy.pay(s, Side.REQUESTER, PaymentMethod.TRANSFER, "1",
                TODAY, PRICE, TODAY, null, 2, TODAY, NOW, null)));
    }

    @Test
    void validaNumerosFechasYMontos() {
        Settlement s = fresh();
        assertEquals("LOGYX-VAL-001", code(() -> SettlementPolicy.invoice(s, Side.CARRIER, false, "F1", TODAY.plusDays(1),
                TODAY, null, PRICE, 0, TODAY, NOW, null)));
        assertEquals("LOGYX-VAL-001", code(() -> SettlementPolicy.invoice(s, Side.CARRIER, false, "F1; DROP", TODAY,
                TODAY, null, PRICE, 0, TODAY, NOW, null)));
        assertEquals("LOGYX-VAL-001", code(() -> SettlementPolicy.pay(s, Side.REQUESTER, PaymentMethod.YAPE, " ", TODAY,
                PRICE, TODAY, null, 0, TODAY, NOW, null)), "Yape exige número de operación");
        assertEquals("LOGYX-VAL-001", code(() -> SettlementPolicy.pay(s, Side.REQUESTER, PaymentMethod.TRANSFER, "1",
                TODAY, new BigDecimal("10.123"), TODAY, null, 0, TODAY, NOW, null)));
        SettlementPolicy.pay(s, Side.REQUESTER, PaymentMethod.CASH, null, TODAY, PRICE, TODAY, null, 0, TODAY, NOW, null);
        assertNull(s.payment().operationNumber(), "efectivo sin número");
    }

    @Test
    void laDisputaVuelveAlEstadoAnteriorOAlQueElijaElOperador() {
        Settlement s = fresh();
        assertEquals("LOGYX-STL-004", code(() -> SettlementPolicy.dispute(s, Side.REQUESTER, false, "No llegó", PYME, NOW)));
        invoice(s);
        pay(s);
        SettlementPolicy.dispute(s, Side.CARRIER, false, "El monto pagado no coincide", CARRIER, NOW);
        assertEquals(SettlementStatus.DISPUTED, s.status());
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.resolve(s, Side.REQUESTER, PYME, null)),
                "la otra parte no la cierra");
        assertEquals("LOGYX-STL-005", code(() -> SettlementPolicy.resolve(s, Side.CARRIER, CARRIER,
                SettlementStatus.PENDING_INVOICE)), "quien la abrió solo la retira");
        assertEquals(SettlementStatus.PAID, SettlementPolicy.resolve(s, Side.CARRIER, CARRIER, null));
        SettlementPolicy.dispute(s, Side.REQUESTER, false, "Pagué de más por error", PYME, NOW);
        assertEquals(SettlementStatus.INVOICED, SettlementPolicy.resolve(s, Side.OPERATOR, UUID.randomUUID(),
                SettlementStatus.INVOICED));
        assertNull(s.payment(), "volver a facturado borra el pago");
        assertNotNull(s.invoice());
    }

    @Test
    void laComisionSeCobraUnaVezYNoEnDisputa() {
        Settlement s = fresh();
        SettlementPolicy.ensureCommissionCollectable(s, new BigDecimal("94.40"));
        s.collectCommission(UUID.randomUUID());
        assertEquals("LOGYX-STL-006", code(() -> SettlementPolicy.ensureCommissionCollectable(s, new BigDecimal("1"))));
        Settlement zero = Settlement.open(UUID.randomUUID(), PYME, CARRIER, TODAY, false);
        assertEquals(CommissionStatus.NOT_APPLICABLE, zero.commissionStatus());
        assertEquals("LOGYX-STL-006", code(() -> SettlementPolicy.ensureCommissionCollectable(zero, BigDecimal.ZERO)));
    }

    @Test
    void vencimientoYRecordatorios() {
        assertEquals(LocalDate.of(2026, 12, 19), SettlementPolicy.dueOn(TODAY, 30));
        LocalDate due = TODAY;
        assertEquals(SettlementPolicy.Notice.DUE_SOON, SettlementPolicy.noticeFor(SettlementStatus.INVOICED, due, TODAY.minusDays(3)));
        assertNull(SettlementPolicy.noticeFor(SettlementStatus.INVOICED, due, TODAY.minusDays(4)));
        assertEquals(SettlementPolicy.Notice.DUE_TODAY, SettlementPolicy.noticeFor(SettlementStatus.PENDING_INVOICE, due, TODAY));
        assertEquals(SettlementPolicy.Notice.OVERDUE, SettlementPolicy.noticeFor(SettlementStatus.INVOICED, due, TODAY.plusDays(9)));
        assertNull(SettlementPolicy.noticeFor(SettlementStatus.PAID, due, TODAY.plusDays(9)), "pagado: sin recordatorio");
        Settlement s = Settlement.open(UUID.randomUUID(), PYME, CARRIER, TODAY.minusDays(5), true);
        assertEquals(5, SettlementPolicy.daysOverdue(s, TODAY));
    }
}
