package pe.logyx.documents.domain;

import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.domain.BusinessException;

/**
 * Reglas de subida de documentos (RF46/RF47, RF37):
 * <ul>
 *   <li>Tamaño 1 B – {@code maxBytes} y formato jpg/png/pdf por firma binaria.</li>
 *   <li>Evidencia de entrega ({@code DELIVERY_PHOTO}, {@code SIGNATURE}): solo imágenes, solo el transportista o su
 *       conductor, ligada a la parada de ENTREGA y después de registrar la llegada a ella.</li>
 * </ul>
 */
public final class DocumentUploadPolicy {

    /** Datos de la parada indicada en la subida (null si no se indicó). */
    public record StopInfo(boolean isDelivery, boolean arrivedOrCompleted) {}

    private DocumentUploadPolicy() {}

    public static FileFormat ensureValidFile(DocumentType type, long sizeBytes, long maxBytes, byte[] header) {
        if (sizeBytes <= 0) {
            throw BusinessException.badRequest("LOGYX-DOC-003", "El archivo está vacío", "file");
        }
        if (sizeBytes > maxBytes) {
            throw BusinessException.badRequest("LOGYX-DOC-002",
                    "El archivo supera el tamaño máximo de " + (maxBytes / 1024) + " KB", "file");
        }
        FileFormat f = FileFormat.detect(header).orElseThrow(() -> BusinessException.badRequest("LOGYX-DOC-001",
                "Tipo de archivo no permitido: solo JPG, PNG o PDF", "file"));
        if (type.isDeliveryEvidence() && !f.image) {
            throw BusinessException.badRequest("LOGYX-DOC-001",
                    "La foto y la firma de entrega deben ser una imagen JPG o PNG", "file");
        }
        return f;
    }

    public static void ensureCanAttach(DocumentType type, boolean uploaderIsCarrierSide, StopInfo stop) {
        if (!type.isDeliveryEvidence()) return;
        if (!uploaderIsCarrierSide) {
            throw BusinessException.forbidden("Solo el transportista o su conductor sube la evidencia de entrega");
        }
        if (stop == null || !stop.isDelivery()) {
            throw BusinessException.badRequest("LOGYX-DOC-004",
                    "La evidencia de entrega debe indicar la parada de entrega (stopId)", "stopId");
        }
        if (!stop.arrivedOrCompleted()) {
            throw BusinessException.conflict("LOGYX-DOC-005",
                    "Registra la llegada a la parada antes de subir la evidencia de entrega", "stopId");
        }
    }
}
