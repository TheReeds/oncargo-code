package pe.logyx.operator;

import org.junit.jupiter.api.Test;
import pe.logyx.operator.domain.FraudSignalKind;
import pe.logyx.operator.domain.FraudSignalPolicy;
import pe.logyx.operator.domain.FraudSignalSeverity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudSignalPolicyTest {

    @Test
    void clasificaDatosCompartidosTasasYOfertasBajas() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var shared = List.of(
                new FraudSignalPolicy.Shared(FraudSignalKind.SHARED_PHONE, "987654321", List.of(a, b)),
                new FraudSignalPolicy.Shared(FraudSignalKind.SHARED_LICENSE, "Q123", List.of(a, b, c)),
                new FraudSignalPolicy.Shared(FraudSignalKind.SHARED_SIGNUP_IP, "hash", List.of(a, b)),
                new FraudSignalPolicy.Shared(FraudSignalKind.SHARED_PLATE, "ABC123", List.of(a)));
        var rates = List.of(
                new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_WITHDRAWAL_RATE, a, 3, 10),
                new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_WITHDRAWAL_RATE, b, 5, 10),
                new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_WITHDRAWAL_RATE, c, 4, 4),   // muestra chica
                new FraudSignalPolicy.Rate(FraudSignalKind.HIGH_WITHDRAWAL_RATE, c, 1, 10));
        var low = List.of(new FraudSignalPolicy.LowBids(c, 6, 8), new FraudSignalPolicy.LowBids(b, 2, 9));
        var out = FraudSignalPolicy.evaluate(shared, rates, low, new BigDecimal("0.30"), 5, 5);

        assertEquals(6, out.size());
        assertEquals(FraudSignalSeverity.HIGH, out.getFirst().severity(), "las altas primero");
        var phone = out.stream().filter(s -> s.kind() == FraudSignalKind.SHARED_PHONE).findFirst().orElseThrow();
        assertEquals(FraudSignalSeverity.MEDIUM, phone.severity());
        assertEquals("987654321", phone.value());
        var ip = out.stream().filter(s -> s.kind() == FraudSignalKind.SHARED_SIGNUP_IP).findFirst().orElseThrow();
        assertNull(ip.value(), "la huella de la IP nunca se muestra");
        assertEquals(FraudSignalSeverity.HIGH, ip.severity());
        assertEquals(2, out.stream().filter(s -> s.kind() == FraudSignalKind.HIGH_WITHDRAWAL_RATE).count());
        assertTrue(out.stream().anyMatch(s -> s.kind() == FraudSignalKind.HIGH_WITHDRAWAL_RATE
                && s.organizationIds().contains(b) && s.severity() == FraudSignalSeverity.HIGH));
        assertTrue(out.stream().anyMatch(s -> s.kind() == FraudSignalKind.LOW_BIDS_NEW_ACCOUNT
                && s.organizationIds().contains(c)));
    }
}
