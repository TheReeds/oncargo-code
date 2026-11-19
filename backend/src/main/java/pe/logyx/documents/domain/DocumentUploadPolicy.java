package pe.logyx.documents.domain;

import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.documents.domain.model.Document;
import pe.logyx.shared.domain.BusinessException;

/**
 * Reglas de subida de documentos (RF46/RF47, RF37):
 * <ul>
 *   <li>Tamaño 1 B – {@code maxBytes} y formato jpg/png/pdf por firma binaria.</li>
 *   <li>Evidencia de entrega ({@code DELIVERY_PHOTO}, {@code SIGNATURE}, RN06/RF45, F14): solo imágenes, solo el
 *       <b>conductor asignado</b> (LOGYX-DOC-007; ni la PYME ni la oficina del transportista), ligada a la parada de
 *       ENTREGA (LOGYX-DOC-004), entre la llegada (LOGYX-DOC-005) y el cierre de esa parada: completada la parada o
 *       cerrado el envío ya no se agrega evidencia (LOGYX-DOC-008). Documentos y evidencia no se editan ni borran
 *       (RLS sin UPDATE/DELETE).</li>
 * </ul>
 */
public final class DocumentUploadPolicy {

    /** Datos de la parada indicada en la subida (null si no se indicó). */
    public record StopInfo(boolean isDelivery, boolean arrived, boolean completed) {
        public StopInfo(boolean isDelivery, boolean arrivedOrCompleted) {
            this(isDelivery, arrivedOrCompleted, false);
        }

        public boolean isPickup() { return !isDelivery; }
    }

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
        if (type.isPickupEvidence() && !f.image) {
            throw BusinessException.badRequest("LOGYX-DOC-001", "La foto de la carga debe ser una imagen JPG o PNG",
                    "file");
        }
        return f;
    }

    /**
     * @param uploaderIsAssignedDriver quien sube es el conductor asignado a este envío (rol DRIVER)
     * @param shipmentClosed           el envío ya está entregado o cancelado
     */
    public static void ensureCanAttach(DocumentType type, boolean uploaderIsAssignedDriver, StopInfo stop,
                                       boolean shipmentClosed) {
        if (type.isPickupEvidence()) {
            ensurePickupEvidence(uploaderIsAssignedDriver, stop, shipmentClosed);
            return;
        }
        if (!type.isDeliveryEvidence()) return;
        if (!uploaderIsAssignedDriver) {
            throw new BusinessException(403, "LOGYX-DOC-007",
                    "Solo el conductor asignado sube la foto y la firma de entrega, desde la parada");
        }
        if (stop == null || !stop.isDelivery()) {
            throw BusinessException.badRequest("LOGYX-DOC-004",
                    "La evidencia de entrega debe indicar la parada de entrega (stopId)", "stopId");
        }
        if (shipmentClosed || stop.completed()) {
            throw BusinessException.conflict("LOGYX-DOC-008",
                    "La entrega ya se cerró: la evidencia no se puede agregar ni cambiar", "stopId");
        }
        if (!stop.arrived()) {
            throw BusinessException.conflict("LOGYX-DOC-005",
                    "Registra la llegada a la parada antes de subir la evidencia de entrega", "stopId");
        }
    }

    /**
     * Foto de la carga en el recojo (roadmap 2.5): mismas garantías que la de entrega (RN06, F14). Solo el conductor
     * asignado (LOGYX-DOC-007; el despachador que registra la parada desde la oficina no la sube), con el
     * {@code stopId} del recojo (LOGYX-DOC-009), con la llegada registrada (LOGYX-DOC-005) y antes de cerrar el recojo
     * (LOGYX-DOC-008).
     */
    private static void ensurePickupEvidence(boolean uploaderIsAssignedDriver, StopInfo stop, boolean shipmentClosed) {
        if (!uploaderIsAssignedDriver) {
            throw new BusinessException(403, "LOGYX-DOC-007",
                    "Solo el conductor asignado sube la foto de la carga, desde la parada de recojo");
        }
        if (stop == null || !stop.isPickup()) {
            throw BusinessException.badRequest("LOGYX-DOC-009",
                    "La foto de la carga debe indicar la parada de recojo (stopId)", "stopId");
        }
        if (shipmentClosed || stop.completed()) {
            throw BusinessException.conflict("LOGYX-DOC-008",
                    "El recojo ya se cerró: la foto de la carga no se puede agregar", "stopId");
        }
        if (!stop.arrived()) {
            throw BusinessException.conflict("LOGYX-DOC-005",
                    "Registra la llegada al recojo antes de subir la foto de la carga", "stopId");
        }
    }
}
