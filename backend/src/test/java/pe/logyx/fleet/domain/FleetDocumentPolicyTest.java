package pe.logyx.fleet.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.fleet.domain.FleetDocumentPolicy.Vigency;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.FleetDocumentType;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FleetDocumentPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private static FleetDocument doc(FleetDocumentType type, LocalDate expires, boolean current) {
        return new FleetDocument(UUID.randomUUID(), UUID.randomUUID(), type, UUID.randomUUID(), null, null, expires,
                "a.pdf", "application/pdf", 10, "x", "k", null, OffsetDateTime.now(), current ? null : OffsetDateTime.now());
    }

    @Test
    void estadoPorFechaDeVencimiento() {
        assertEquals(Vigency.NO_EXPIRY, FleetDocumentPolicy.status(null, TODAY));
        assertEquals(Vigency.EXPIRED, FleetDocumentPolicy.status(TODAY.minusDays(1), TODAY));
        assertEquals(Vigency.EXPIRING, FleetDocumentPolicy.status(TODAY, TODAY));
        assertEquals(Vigency.EXPIRING, FleetDocumentPolicy.status(TODAY.plusDays(30), TODAY));
        assertEquals(Vigency.VALID, FleetDocumentPolicy.status(TODAY.plusDays(31), TODAY));
    }

    @Test
    void umbralesDeAvisoSon30_7Y0() {
        assertTrue(FleetDocumentPolicy.noticeThreshold(31).isEmpty());
        assertEquals(30, FleetDocumentPolicy.noticeThreshold(30).orElseThrow());
        assertEquals(30, FleetDocumentPolicy.noticeThreshold(8).orElseThrow());
        assertEquals(7, FleetDocumentPolicy.noticeThreshold(7).orElseThrow());
        assertEquals(7, FleetDocumentPolicy.noticeThreshold(1).orElseThrow());
        assertEquals(0, FleetDocumentPolicy.noticeThreshold(0).orElseThrow());
        assertEquals(0, FleetDocumentPolicy.noticeThreshold(-5).orElseThrow());
    }

    @Test
    void elVencimientoEsObligatorioDondeCorrespondeYAcotado() {
        assertEquals("LOGYX-FLT-010", assertThrows(BusinessException.class,
                () -> FleetDocumentPolicy.ensureUploadable(FleetDocumentType.SOAT, null, TODAY)).code());
        assertDoesNotThrow(() -> FleetDocumentPolicy.ensureUploadable(FleetDocumentType.PROPERTY_CARD, null, TODAY));
        assertDoesNotThrow(() -> FleetDocumentPolicy.ensureUploadable(FleetDocumentType.SOAT, TODAY.minusDays(3), TODAY));
        assertEquals("LOGYX-FLT-010", assertThrows(BusinessException.class,
                () -> FleetDocumentPolicy.ensureUploadable(FleetDocumentType.SOAT, TODAY.plusYears(20), TODAY)).code());
    }

    @Test
    void alOfertarAvisaVencidosYLosQueVencenAntesDelRecojo() {
        var soatVencido = doc(FleetDocumentType.SOAT, TODAY.minusDays(2), true);
        var revisionAntesDelRecojo = doc(FleetDocumentType.TECHNICAL_INSPECTION, TODAY.plusDays(5), true);
        var polizaVigente = doc(FleetDocumentType.INSURANCE_POLICY, TODAY.plusDays(200), true);
        var tarjeta = doc(FleetDocumentType.PROPERTY_CARD, TODAY.minusDays(900), true);        // no se revisa al ofertar
        var viejo = doc(FleetDocumentType.MTC_PERMIT, TODAY.minusDays(40), false);              // reemplazado: se ignora
        var issues = FleetDocumentPolicy.bidIssues(List.of(soatVencido, revisionAntesDelRecojo, polizaVigente, tarjeta,
                viejo), TODAY, TODAY.plusDays(10));
        assertEquals(2, issues.size());
        assertEquals("LOGYX-FLT-007", issues.get(0).code());
        assertTrue(issues.get(0).expired());
        assertEquals("LOGYX-FLT-008", issues.get(1).code());
        assertFalse(issues.get(1).expired());
        assertTrue(FleetDocumentPolicy.bidIssues(List.of(), TODAY, TODAY.plusDays(10)).isEmpty(), "sin documentos no hay aviso");
    }

    @Test
    void selloSoloSiSigueVigente() {
        var soat = doc(FleetDocumentType.SOAT, TODAY.plusDays(90), true);
        var revision = doc(FleetDocumentType.TECHNICAL_INSPECTION, TODAY.minusDays(1), true);
        assertEquals(TODAY.plusDays(90), FleetDocumentPolicy.sealValidUntil(List.of(soat, revision), FleetDocumentType.SOAT, TODAY));
        assertNull(FleetDocumentPolicy.sealValidUntil(List.of(soat, revision), FleetDocumentType.TECHNICAL_INSPECTION, TODAY));
        assertNull(FleetDocumentPolicy.sealValidUntil(List.of(), FleetDocumentType.SOAT, TODAY));
    }
}
