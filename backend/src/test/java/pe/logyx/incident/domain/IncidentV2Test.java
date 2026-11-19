package pe.logyx.incident.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.incident.domain.model.*;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentV2Test {

    private static final UUID PYME = UUID.randomUUID(), CARRIER = UUID.randomUUID();

    private static Incident report(IncidentCategory c, UUID by) {
        return Incident.report(UUID.randomUUID(), PYME, CARRIER, by, null, c, "Descripción de prueba",
                Incident.Details.NONE, sev -> sev == IncidentSeverity.CRITICAL ? Duration.ofHours(4) : Duration.ofHours(24));
    }

    @Test
    void cadaCategoriaDeclaraSiCongelaYSuSeveridad() {
        assertFalse(IncidentCategory.DELAY.blocking());
        assertFalse(IncidentCategory.ROAD_BLOCKED.blocking());
        assertTrue(IncidentCategory.THEFT.blocking());
        Incident theft = report(IncidentCategory.THEFT, PYME);
        assertEquals(IncidentSeverity.CRITICAL, theft.severity());
        assertEquals(theft.createdAt().plusHours(4), theft.dueAt());
        assertTrue(theft.holdsShipment());
        assertFalse(report(IncidentCategory.ROAD_BLOCKED, CARRIER).holdsShipment());
    }

    @Test
    void soloQuienReportoRetiraUnaInformativa() {
        Incident delay = report(IncidentCategory.DELAY, CARRIER);
        assertEquals("LOGYX-INC-007", assertThrows(BusinessException.class,
                () -> delay.withdraw(PYME, OffsetDateTime.now())).code());
        delay.withdraw(CARRIER, OffsetDateTime.now());
        assertEquals(IncidentStatus.CLOSED, delay.status());
        assertEquals("LOGYX-INC-008", assertThrows(BusinessException.class,
                () -> delay.reopen("motivo", OffsetDateTime.now(), Duration.ofHours(1))).code());
        Incident theft = report(IncidentCategory.THEFT, PYME);
        assertEquals("LOGYX-INC-007", assertThrows(BusinessException.class,
                () -> theft.withdraw(PYME, OffsetDateTime.now())).code());
    }

    @Test
    void unaInformativaSeResuelveSinEstadoFinalYSeReabreConSlaNuevo() {
        Incident delay = report(IncidentCategory.DELAY, CARRIER);
        delay.changeStatus(IncidentStatus.RESOLVED, "Llegó", null, Shipment.Status.IN_TRANSIT, OffsetDateTime.now());
        assertEquals(IncidentStatus.RESOLVED, delay.status());
        Incident theft = report(IncidentCategory.THEFT, PYME);
        assertEquals("LOGYX-INC-006", assertThrows(BusinessException.class, () -> theft.changeStatus(
                IncidentStatus.RESOLVED, "x", null, Shipment.Status.IN_TRANSIT, OffsetDateTime.now())).code());
        theft.changeStatus(IncidentStatus.RESOLVED, "Recuperada", IncidentShipmentOutcome.RESUME,
                Shipment.Status.IN_TRANSIT, OffsetDateTime.now());
        OffsetDateTime now = OffsetDateTime.now();
        theft.reopen("Faltan bultos", now, Duration.ofHours(4));
        assertEquals(IncidentStatus.IN_REVIEW, theft.status());
        assertNull(theft.shipmentOutcome());
        assertEquals(1, theft.reopenCount());
        assertEquals(now.plusHours(4), theft.dueAt());
    }

    @Test
    void elMontoReclamadoDebeSerPositivoYLaNotaInternaEsDelOperador() {
        assertEquals("LOGYX-INC-010", assertThrows(BusinessException.class, () -> Incident.report(UUID.randomUUID(),
                PYME, CARRIER, PYME, null, IncidentCategory.CARGO_DAMAGE, "x", new Incident.Details(null,
                        new BigDecimal("-1"), null, null, null), sev -> Duration.ofHours(1))).code());
        Incident i = report(IncidentCategory.CARGO_DAMAGE, PYME);
        assertEquals("LOGYX-INC-009", assertThrows(BusinessException.class,
                () -> IncidentComment.write(i, PYME, null, "nota", true)).code());
        assertTrue(IncidentComment.write(i, UUID.randomUUID(), null, "nota", true).internal());
    }
}
