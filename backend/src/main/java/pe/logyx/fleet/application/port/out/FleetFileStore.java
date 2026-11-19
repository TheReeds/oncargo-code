package pe.logyx.fleet.application.port.out;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Archivos de los documentos de flota. Lo implementa {@code fleet/infrastructure/storage} sobre el almacenamiento
 * común ({@code documents.application.port.out.DocumentStorage}) y la validación de formato por firma binaria
 * (jpg, png, pdf), así {@code fleet} no depende de la capa de aplicación de {@code documents}.
 */
public interface FleetFileStore {

    @FunctionalInterface
    interface Content { InputStream open() throws IOException; }

    record Stored(String key, String contentType, String extension, String sha256) {}

    /** Valida tamaño y formato (LOGYX-DOC-001/002/003) y guarda; la clave la genera el servidor. */
    Stored put(UUID organizationId, UUID documentId, long sizeBytes, Content content);

    InputStream open(String key);

    void delete(String key);
}
