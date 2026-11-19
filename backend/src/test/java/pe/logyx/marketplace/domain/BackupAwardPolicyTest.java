package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.marketplace.domain.model.*;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackupAwardPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private static final OffsetDateTime AWARDED = OffsetDateTime.parse("2026-11-18T09:00:00-05:00");

    private static BackupAwardPolicy.AwardedShipment cancelledAfter(long hours) {
        return new BackupAwardPolicy.AwardedShipment(AWARDED, AWARDED.plusHours(hours));
    }

    private static String code(Runnable r) {
        return assertThrows(BusinessException.class, r::run).code();
    }

    @Test
    void sePuedeSiElEnvioSeCancelaDentroDeLas24HYElRecojoNoPaso() {
        assertDoesNotThrow(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.CANCELLED, cancelledAfter(23),
                TODAY.plusDays(2), TODAY));
        assertDoesNotThrow(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.CANCELLED, cancelledAfter(24),
                TODAY, TODAY));
        assertEquals(AWARDED.plusHours(24), BackupAwardPolicy.deadline(cancelledAfter(3)));
    }

    @Test
    void noCorrespondeSiNoSeCancelo() {
        assertEquals("LOGYX-BCK-001", code(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.MATCHED,
                new BackupAwardPolicy.AwardedShipment(AWARDED, null), TODAY.plusDays(2), TODAY)));
        assertEquals("LOGYX-BCK-001", code(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.CANCELLED,
                null, TODAY.plusDays(2), TODAY)));
        assertEquals("LOGYX-BCK-001", code(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.PUBLISHED,
                cancelledAfter(1), TODAY.plusDays(2), TODAY)));
        assertNull(BackupAwardPolicy.deadline(null));
    }

    @Test
    void fueraDePlazoOConElRecojoPasado() {
        assertEquals("LOGYX-BCK-002", code(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.CANCELLED,
                cancelledAfter(25), TODAY.plusDays(2), TODAY)));
        assertEquals("LOGYX-BCK-003", code(() -> BackupAwardPolicy.ensureAwardable(ShipmentRequest.Status.CANCELLED,
                cancelledAfter(2), TODAY.minusDays(1), TODAY)));
        assertEquals("LOGYX-BCK-002", BackupAwardPolicy.blocker(ShipmentRequest.Status.CANCELLED, cancelledAfter(30),
                TODAY.plusDays(2), TODAY));
        assertNull(BackupAwardPolicy.blocker(ShipmentRequest.Status.CANCELLED, cancelledAfter(2), TODAY.plusDays(2), TODAY));
    }

    @Test
    void elRespaldoSigueElCircuitoPedirConfirmarAdjudicar() {
        UUID req = UUID.randomUUID(), org = UUID.randomUUID(), carrier = UUID.randomUUID();
        BidBackup b = new BidBackup(UUID.randomUUID(), req, org, carrier, 1, BackupState.AVAILABLE,
                OffsetDateTime.now(), null, null, null);
        assertEquals("LOGYX-BCK-005", code(() -> b.respond(true)), "no se responde sin que la PYME lo pida");
        assertEquals("LOGYX-BCK-004", code(b::use), "no se adjudica sin confirmación");
        b.request();
        assertEquals(BackupState.REQUESTED, b.state());
        assertEquals("LOGYX-BCK-005", code(b::request), "pedir dos veces");
        b.respond(true);
        assertEquals(BackupState.CONFIRMED, b.state());
        b.use();
        assertEquals(BackupState.USED, b.state());
        assertEquals("LOGYX-BCK-005", code(() -> b.respond(false)));
    }
}
