package pe.logyx.documents.infrastructure;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import pe.logyx.documents.infrastructure.storage.S3DocumentStorage;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Adaptador S3 contra un MinIO real (Testcontainers, ~100 MB de RAM). Prueba unitaria sin {@code @QuarkusTest}:
 * no levanta la aplicación. Imagen {@code chainguard/minio} (MinIO dejó de publicar {@code minio/minio} en Docker
 * Hub); se puede cambiar con {@code -Dlogyx.test.minio-image=...}. Si no hay Docker, se omite.
 */
class S3DocumentStorageTest {

    private static final String USER = "logyxtest";
    private static final String PASSWORD = "logyxtest-secret";

    static GenericContainer<?> minio;
    static S3DocumentStorage storage;

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker no disponible");
        minio = new GenericContainer<>(System.getProperty("logyx.test.minio-image", "chainguard/minio:latest"))
                .withEnv("MINIO_ROOT_USER", USER)
                .withEnv("MINIO_ROOT_PASSWORD", PASSWORD)
                .withCommand("server", "/data")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
                .withStartupTimeout(Duration.ofMinutes(2));
        minio.start();
        storage = new S3DocumentStorage(new S3DocumentStorage.Settings(
                Optional.of("http://" + minio.getHost() + ":" + minio.getMappedPort(9000)), "us-east-1",
                "logyx-test", USER, PASSWORD, true, true, "docs", false));
    }

    @AfterAll
    static void stop() {
        if (storage != null) storage.close();
        if (minio != null) minio.stop();
    }

    @Test
    void guardaLeeReemplazaYBorra() throws Exception {
        String key = "shipments/abc/123.pdf";
        storage.put(key, new ByteArrayInputStream("%PDF-1.4 uno".getBytes(StandardCharsets.UTF_8)));
        try (InputStream in = storage.open(key)) {
            assertEquals("%PDF-1.4 uno", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        storage.put(key, new ByteArrayInputStream("%PDF-1.4 dos".getBytes(StandardCharsets.UTF_8)));
        try (InputStream in = storage.open(key)) {
            assertEquals("%PDF-1.4 dos", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        storage.delete(key);
        assertThrows(FileNotFoundException.class, () -> storage.open(key));
        storage.delete(key);   // idempotente
    }

    @Test
    void archivoGrandeYBinario() throws Exception {
        byte[] data = new byte[3 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
        storage.put("shipments/big/1.jpg", new ByteArrayInputStream(data));
        try (InputStream in = storage.open("shipments/big/1.jpg")) {
            assertArrayEquals(data, in.readAllBytes());
        }
    }

    @Test
    void rechazaClavesQueEscapan() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.put("../otro/x.pdf", new ByteArrayInputStream(new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () -> storage.open("/abs.pdf"));
    }
}
