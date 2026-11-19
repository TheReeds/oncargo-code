package pe.logyx.incident.domain;

import pe.logyx.incident.domain.model.IncidentCategory;
import org.junit.jupiter.api.Test;
import pe.logyx.incident.domain.model.Incident;
import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.incident.domain.model.IncidentComment;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.incident.domain.model.IncidentShipmentOutcome;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentStatusPolicyTest {

    private static Incident incident() {
        UUID p = UUID.randomUUID();
        return Incident.report(UUID.randomUUID(), p, UUID.randomUUID(), p, null, IncidentCategory.CARGO_DAMAGE, " retraso ");
    }

    @Test
    void cicloDeVidaValido() {
        Incident i = incident();
        assertTrue(i.isActive());
        assertEquals("retraso", i.description());
        i.changeStatus(IncidentStatus.IN_REVIEW, null, OffsetDateTime.now());
        assertTrue(i.isActive());
        i.changeStatus(IncidentStatus.RESOLVED, "Se reprogramó", IncidentShipmentOutcome.RESUME,
                Shipment.Status.IN_TRANSIT, OffsetDateTime.now());
        assertEquals(IncidentShipmentOutcome.RESUME, i.shipmentOutcome());
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
        // F27: cerrar (descartar) una activa también exige motivo
        assertEquals("LOGYX-INC-003", assertThrows(BusinessException.class,
                () -> IncidentStatusPolicy.ensureTransition(IncidentStatus.OPEN, IncidentStatus.CLOSED, null)).code());
        assertDoesNotThrow(() -> IncidentStatusPolicy.ensureTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED, null));
    }

    @Test
    void estadoFinalDelEnvioObligatorioYCoherente() {
        // RF55: sin estado final no se libera el envío
        assertEquals("LOGYX-INC-006", assertThrows(BusinessException.class,
                () -> incident().changeStatus(IncidentStatus.RESOLVED, "ok", OffsetDateTime.now())).code());
        assertEquals("LOGYX-INC-006", assertThrows(BusinessException.class,
                () -> incident().changeStatus(IncidentStatus.RESOLVED, "ok", null, Shipment.Status.IN_TRANSIT,
                        OffsetDateTime.now())).code());
        // un envío pendiente no se "entrega"; uno entregado solo queda entregado
        assertEquals("LOGYX-INC-006", assertThrows(BusinessException.class,
                () -> IncidentOutcomePolicy.ensureValid(IncidentShipmentOutcome.DELIVERED, Shipment.Status.PENDING)).code());
        assertEquals("LOGYX-INC-006", assertThrows(BusinessException.class,
                () -> IncidentOutcomePolicy.ensureValid(IncidentShipmentOutcome.RESUME, Shipment.Status.DELIVERED)).code());
        assertDoesNotThrow(() -> IncidentOutcomePolicy.ensureValid(IncidentShipmentOutcome.CANCELLED, Shipment.Status.IN_TRANSIT));
        assertDoesNotThrow(() -> IncidentOutcomePolicy.ensureValid(IncidentShipmentOutcome.DELIVERED, Shipment.Status.DELIVERED));
        // IN_REVIEW no libera el envío: no pide estado final
        Incident i = incident();
        assertDoesNotThrow(() -> i.changeStatus(IncidentStatus.IN_REVIEW, null, OffsetDateTime.now()));
        assertNull(i.shipmentOutcome());
    }

    @Test
    void textosConDatosDeContactoSeRechazan() {
        UUID p = UUID.randomUUID();
        assertEquals("LOGYX-NEG-001", assertThrows(BusinessException.class, () -> Incident.report(UUID.randomUUID(), p,
                UUID.randomUUID(), p, null, IncidentCategory.DELAY, "llámame al 987 654 321")).code());
        Incident i = incident();
        assertEquals("LOGYX-NEG-001", assertThrows(BusinessException.class,
                () -> IncidentComment.write(i, i.requesterOrgId(), null, "escríbeme a ana@gmail.com")).code());
    }

    @Test
    void cerradaNoAdmiteComentarios() {
        Incident i = incident();
        assertDoesNotThrow(() -> IncidentComment.write(i, i.requesterOrgId(), null, "hola"));
        i.changeStatus(IncidentStatus.CLOSED, "Duplicada", IncidentShipmentOutcome.RESUME, Shipment.Status.IN_TRANSIT,
                OffsetDateTime.now());
        assertEquals("LOGYX-INC-004", assertThrows(BusinessException.class,
                () -> IncidentComment.write(i, i.requesterOrgId(), null, "hola")).code());
    }
}
