package pe.logyx.marketplace.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ManualAssignmentPolicyTest {

    private static final Duration SLA = Duration.ofHours(24);

    private static ShipmentRequest request(ShipmentRequest.Status status, OffsetDateTime createdAt) {
        var cargo = new ShipmentRequest.Cargo(City.LIMA, "a", City.CUSCO, "b", CargoType.GENERAL, "c",
                BigDecimal.TEN, null, LocalDate.now());
        var pricing = new ShipmentRequest.Pricing(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE);
        return new ShipmentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cargo, pricing, status,
                null, createdAt);
    }

    @Test
    void rn10SoloPublicadaSinOfertasTras24h() {
        OffsetDateTime now = OffsetDateTime.now();
        assertTrue(ManualAssignmentPolicy.slaBreached(request(ShipmentRequest.Status.PUBLISHED, now.minusHours(24)), 0, now, SLA));
        assertFalse(ManualAssignmentPolicy.slaBreached(request(ShipmentRequest.Status.PUBLISHED, now.minusHours(23)), 0, now, SLA));
        assertFalse(ManualAssignmentPolicy.slaBreached(request(ShipmentRequest.Status.PUBLISHED, now.minusHours(30)), 1, now, SLA));
        assertFalse(ManualAssignmentPolicy.slaBreached(request(ShipmentRequest.Status.MATCHED, now.minusHours(30)), 0, now, SLA));
        assertEquals("LOGYX-OPS-001", assertThrows(BusinessException.class, () -> ManualAssignmentPolicy.ensureSlaBreached(
                request(ShipmentRequest.Status.CANCELLED, now.minusDays(3)), 0, now, SLA)).code());
    }
}
