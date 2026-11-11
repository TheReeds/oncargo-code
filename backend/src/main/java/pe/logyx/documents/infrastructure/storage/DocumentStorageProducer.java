package pe.logyx.documents.infrastructure.storage;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import pe.logyx.documents.application.port.out.DocumentStorage;


/**
 * Elige el adaptador de {@link DocumentStorage} por configuración (ADR-0005): {@code logyx.documents.storage} =
 * {@code local} (por omisión; dev/test) o {@code s3} (producción: S3/MinIO, {@code logyx.documents.s3.*}).
 */
@ApplicationScoped
public class DocumentStorageProducer {

    /** {@code @Startup}: falla al arrancar si S3 está mal configurado (y crea el bucket si se pidió). */
    @Produces
    @ApplicationScoped
    @Startup
    DocumentStorage documentStorage() {
        Config c = ConfigProvider.getConfig();
        String kind = c.getOptionalValue("logyx.documents.storage", String.class).orElse("local").trim().toLowerCase();
        return switch (kind) {
            case "local" -> new LocalFileSystemDocumentStorage(c.getValue("logyx.documents.storage-dir", String.class));
            case "s3" -> new S3DocumentStorage(new S3DocumentStorage.Settings(
                    c.getOptionalValue("logyx.documents.s3.endpoint", String.class),
                    c.getOptionalValue("logyx.documents.s3.region", String.class).orElse("us-east-1"),
                    c.getValue("logyx.documents.s3.bucket", String.class),
                    c.getValue("logyx.documents.s3.access-key", String.class),
                    c.getValue("logyx.documents.s3.secret-key", String.class),
                    c.getOptionalValue("logyx.documents.s3.path-style", Boolean.class).orElse(true),
                    c.getOptionalValue("logyx.documents.s3.create-bucket", Boolean.class).orElse(false),
                    c.getOptionalValue("logyx.documents.s3.prefix", String.class).orElse(""),
                    c.getOptionalValue("logyx.documents.s3.sse", Boolean.class).orElse(false)));
            default -> throw new IllegalStateException("logyx.documents.storage desconocido: " + kind + " (local|s3)");
        };
    }

    void close(@Disposes DocumentStorage storage) throws Exception {
        if (storage instanceof AutoCloseable closeable) closeable.close();
    }
}
