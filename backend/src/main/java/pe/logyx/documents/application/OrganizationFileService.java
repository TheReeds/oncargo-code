package pe.logyx.documents.application;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.documents.application.port.out.DocumentStorage;
import pe.logyx.documents.domain.FileFormat;
import pe.logyx.shared.domain.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Archivos de una organización que no pertenecen a un envío (roadmap 2.15: sustento de un cambio de razón social o RUC).
 * Mismas reglas de formato y tamaño que los documentos (jpg/png/pdf por firma binaria); clave generada por el servidor
 * ({@code organizations/<id>/legal/<uuid>.<ext>}). Los metadatos los guarda quien lo usa.
 */
@ApplicationScoped
public class OrganizationFileService {

    @Inject DocumentStorage storage;

    @ConfigProperty(name = "logyx.documents.max-size-bytes", defaultValue = "10485760")
    long maxBytes;

    public record StoredFile(String key, String fileName, String contentType, long sizeBytes, String sha256) {}

    public StoredFile storeLegal(UUID organizationId, String originalName, long sizeBytes,
                                 UploadDocumentCommand.Content content) {
        if (sizeBytes <= 0) throw BusinessException.badRequest("LOGYX-DOC-003", "El archivo está vacío", "file");
        if (sizeBytes > maxBytes) {
            throw BusinessException.badRequest("LOGYX-DOC-002",
                    "El archivo supera el tamaño máximo de " + (maxBytes / 1024) + " KB", "file");
        }
        byte[] header;
        try (InputStream in = content.open()) {
            header = in.readNBytes(FileFormat.HEADER_LENGTH);
        } catch (IOException e) {
            throw new BusinessException(500, "LOGYX-DOC-500", "No se pudo leer el archivo");
        }
        FileFormat f = FileFormat.detect(header).orElseThrow(() -> BusinessException.badRequest("LOGYX-DOC-001",
                "Tipo de archivo no permitido: solo JPG, PNG o PDF", "file"));
        String key = "organizations/" + organizationId + "/legal/" + UUID.randomUUID() + "." + f.extension;
        try (InputStream raw = content.open();
             DigestInputStream in = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
            storage.put(key, in);
            String sha = HexFormat.of().formatHex(in.getMessageDigest().digest());
            return new StoredFile(key, DocumentService.cleanName(originalName, f), f.contentType, sizeBytes, sha);
        } catch (IOException e) {
            Log.error("No se pudo guardar el archivo " + key, e);
            throw new BusinessException(500, "LOGYX-DOC-500", "No se pudo guardar el archivo");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Borra un archivo guardado si la operación que lo usaba falló (sin fila no debe quedar huérfano). */
    public void discard(String key) {
        storage.delete(key);
    }

    public InputStream open(String key) throws IOException {
        return storage.open(key);
    }
}
