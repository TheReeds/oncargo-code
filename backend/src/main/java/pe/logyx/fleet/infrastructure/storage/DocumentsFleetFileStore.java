package pe.logyx.fleet.infrastructure.storage;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.documents.application.port.out.DocumentStorage;
import pe.logyx.documents.domain.DocumentUploadPolicy;
import pe.logyx.documents.domain.FileFormat;
import pe.logyx.documents.domain.model.DocumentType;
import pe.logyx.fleet.application.port.out.FleetFileStore;
import pe.logyx.shared.domain.BusinessException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Guarda los documentos de flota en el mismo almacenamiento que la evidencia (ADR-0005), bajo {@code fleet/}. */
@ApplicationScoped
public class DocumentsFleetFileStore implements FleetFileStore {

    @Inject DocumentStorage storage;

    @ConfigProperty(name = "logyx.documents.max-size-bytes", defaultValue = "10485760")
    long maxBytes;

    @Override
    public Stored put(UUID organizationId, UUID documentId, long sizeBytes, Content content) {
        byte[] header;
        try (InputStream in = content.open()) {
            header = in.readNBytes(FileFormat.HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        FileFormat format = DocumentUploadPolicy.ensureValidFile(DocumentType.OTHER, sizeBytes, maxBytes, header);
        String key = "fleet/" + organizationId + "/" + documentId + "." + format.extension;
        try (InputStream raw = content.open();
             DigestInputStream in = new DigestInputStream(raw, MessageDigest.getInstance("SHA-256"))) {
            storage.put(key, in);
            return new Stored(key, format.contentType, format.extension,
                    HexFormat.of().formatHex(in.getMessageDigest().digest()));
        } catch (IOException e) {
            Log.error("No se pudo guardar el documento de flota " + key, e);
            throw new BusinessException(500, "LOGYX-DOC-500", "No se pudo guardar el archivo");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return storage.open(key);
        } catch (IOException e) {
            throw BusinessException.notFound("Archivo");
        }
    }

    @Override
    public void delete(String key) {
        storage.delete(key);
    }
}
