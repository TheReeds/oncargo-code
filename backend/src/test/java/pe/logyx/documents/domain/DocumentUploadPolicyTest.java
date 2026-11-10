package pe.logyx.documents.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.documents.domain.DocumentUploadPolicy.StopInfo;
import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.shared.domain.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadPolicyTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 0, 0, 0};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    private static final byte[] TXT = {'h', 'o', 'l', 'a', ' ', 'm', 'u', 'n'};

    private static String code(Runnable r) {
        return assertThrows(BusinessException.class, r::run).code();
    }

    @Test
    void reconoceFormatosPorFirmaBinaria() {
        assertEquals(FileFormat.PNG, FileFormat.detect(PNG).orElseThrow());
        assertEquals(FileFormat.JPEG, FileFormat.detect(JPG).orElseThrow());
        assertEquals(FileFormat.PDF, FileFormat.detect(PDF).orElseThrow());
        assertTrue(FileFormat.detect(TXT).isEmpty());
        assertTrue(FileFormat.detect(new byte[]{(byte) 0xFF}).isEmpty());
    }

    @Test
    void validaTamanoYTipo() {
        assertEquals("LOGYX-DOC-003", code(() -> DocumentUploadPolicy.ensureValidFile(DocumentType.OTHER, 0, 100, PNG)));
        assertEquals("LOGYX-DOC-002", code(() -> DocumentUploadPolicy.ensureValidFile(DocumentType.OTHER, 101, 100, PNG)));
        assertEquals("LOGYX-DOC-001", code(() -> DocumentUploadPolicy.ensureValidFile(DocumentType.OTHER, 10, 100, TXT)));
        assertEquals("LOGYX-DOC-001", code(() -> DocumentUploadPolicy.ensureValidFile(DocumentType.SIGNATURE, 10, 100, PDF)));
        assertEquals(FileFormat.PDF, DocumentUploadPolicy.ensureValidFile(DocumentType.REMISSION_GUIDE, 10, 100, PDF));
        assertEquals(FileFormat.JPEG, DocumentUploadPolicy.ensureValidFile(DocumentType.DELIVERY_PHOTO, 100, 100, JPG));
    }

    @Test
    void evidenciaSoloDelTransportistaEnLaEntregaYTrasLaLlegada() {
        StopInfo arrivedDelivery = new StopInfo(true, true);
        assertEquals("LOGYX-GEN-403", code(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.DELIVERY_PHOTO, false, arrivedDelivery)));
        assertEquals("LOGYX-DOC-004", code(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.SIGNATURE, true, null)));
        assertEquals("LOGYX-DOC-004", code(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.SIGNATURE, true, new StopInfo(false, true))));
        assertEquals("LOGYX-DOC-005", code(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.DELIVERY_PHOTO, true, new StopInfo(true, false))));
        assertDoesNotThrow(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.DELIVERY_PHOTO, true, arrivedDelivery));
        assertDoesNotThrow(() -> DocumentUploadPolicy.ensureCanAttach(DocumentType.REMISSION_GUIDE, false, null));
    }
}
