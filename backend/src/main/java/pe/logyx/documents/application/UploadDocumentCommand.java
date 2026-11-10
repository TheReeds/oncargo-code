package pe.logyx.documents.application;

import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.documents.domain.model.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Subida de un archivo a un envío. {@code content} abre el archivo (el adaptador web lo tiene en disco temporal);
 * {@code sizeBytes} es el tamaño real recibido.
 */
public record UploadDocumentCommand(UUID shipmentId, DocumentType type, UUID stopId, String fileName,
                                    long sizeBytes, Content content) {

    @FunctionalInterface
    public interface Content {
        InputStream open() throws IOException;
    }
}
