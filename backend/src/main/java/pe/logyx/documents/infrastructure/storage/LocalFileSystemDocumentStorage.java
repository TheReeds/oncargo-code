package pe.logyx.documents.infrastructure.storage;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.documents.application.port.out.DocumentStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Almacenamiento en disco local bajo {@code logyx.documents.storage-dir} (por omisión {@code ~/.logyx/documents},
 * fuera del repo; en tests un directorio temporal). Escritura atómica: archivo temporal + move. Las claves las
 * genera el servidor; aun así se rechaza cualquier clave que escape del directorio raíz.
 * Para producción se reemplaza por un adaptador S3/MinIO que implemente {@link DocumentStorage}.
 */
@ApplicationScoped
public class LocalFileSystemDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalFileSystemDocumentStorage(
            @ConfigProperty(name = "logyx.documents.storage-dir") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            Log.warnf("No se pudo borrar %s: %s", key, e.getMessage());
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Clave de almacenamiento inválida");
        return p;
    }
}
