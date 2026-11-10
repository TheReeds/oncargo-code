package pe.logyx.incident.domain;

import pe.logyx.incident.domain.model.IncidentCategory;
import org.junit.jupiter.api.Test;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.domain.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentStatusPolicyTest {

    private static Incident incident() {
        UUID p = UUID.randomUUID();
        return Incident.report(UUID.randomUUID(), p, UUID.randomUUID(), p, null, IncidentCategory.DELAY, " retraso ");
    }

    @Test
    void cicloDeVidaValido() {
        Incident i = incident();
        assertTrue(i.isActive());
        assertEquals("retraso", i.description());
        i.changeStatus(IncidentStatus.IN_REVIEW, null, OffsetDateTime.now());
        assertTrue(i.isActive());
        i.changeStatus(IncidentStatus.RESOLVED, "Se reprogramó", OffsetDateTime.now());
        assertFalse(i.isActive());
        assertNotNull(i.resolvedAt());
        i.changeStatus(IncidentStatus.CLOSED, null, OffsetDateTime.now());
        assertEquals("Se reprogramó", i.resolutionNotes());
    }

    @Test
    void transicionesInvalidasYNotasObligatorias() {
        assertEquals("LOGYX-INC-003", assertThrows(BusinessException.class,
                () -> IncidentStatusPolicy.ensureTransition(IncidentStatus.OPEN, IncidentStatus.RESOLVED, " ")).code());
        assertEquals("LOGYX-INC-002", assertThrows(BusinessException.class,
                () -> IncidentStatusPolicy.ensureTransition(IncidentStatus.RESOLVED, IncidentStatus.OPEN, null)).code());
        assertEquals("LOGYX-INC-002", assertThrows(BusinessException.class,
                () -> IncidentStatusPolicy.ensureTransition(IncidentStatus.CLOSED, IncidentStatus.IN_REVIEW, null)).code());
        assertDoesNotThrow(() -> IncidentStatusPolicy.ensureTransition(IncidentStatus.OPEN, IncidentStatus.CLOSED, null));
    }

    @Test
    void cerradaNoAdmiteComentarios() {
        Incident i = incident();
        assertDoesNotThrow(() -> IncidentComment.write(i, i.requesterOrgId(), null, "hola"));
        i.changeStatus(IncidentStatus.CLOSED, null, OffsetDateTime.now());
        assertEquals("LOGYX-INC-004", assertThrows(BusinessException.class,
                () -> IncidentComment.write(i, i.requesterOrgId(), null, "hola")).code());
    }
}
