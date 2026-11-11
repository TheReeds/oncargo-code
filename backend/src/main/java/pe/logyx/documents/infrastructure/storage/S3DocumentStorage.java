package pe.logyx.documents.infrastructure.storage;

import io.quarkus.logging.Log;
import pe.logyx.documents.application.port.out.DocumentStorage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Almacenamiento compatible con S3 (AWS S3, MinIO, Cloudflare R2…), ADR-0005. Cliente síncrono del AWS SDK v2 con
 * el cliente HTTP de {@code java.net} (sin Netty ni Apache). En MinIO se usa {@code path-style}. Los objetos se
 * guardan con la misma clave que genera el servidor (bajo un prefijo opcional) y cifrado del lado del servidor
 * (SSE-S3) si {@code sse=true}. No se exponen URLs públicas: la descarga sigue pasando por la API (RLS).
 */
public class S3DocumentStorage implements DocumentStorage, AutoCloseable {

    /** Configuración {@code logyx.documents.s3.*}. */
    public record Settings(Optional<String> endpoint, String region, String bucket, String accessKey, String secretKey,
                           boolean pathStyle, boolean createBucket, String prefix, boolean sse) {}

    private final S3Client s3;
    private final Settings settings;

    public S3DocumentStorage(Settings settings) {
        this.settings = settings;
        var builder = S3Client.builder()
                .region(Region.of(settings.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(settings.pathStyle()).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(60)));
        settings.endpoint().filter(e -> !e.isBlank()).map(URI::create).ifPresent(builder::endpointOverride);
        this.s3 = builder.build();
        if (settings.createBucket()) ensureBucket();
    }

    private void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(settings.bucket()));
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(b -> b.bucket(settings.bucket()));
                Log.infof("Bucket de documentos creado: %s", settings.bucket());
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // otra instancia lo creó a la vez
            }
        }
    }

    private String objectKey(String key) {
        if (key.isBlank() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Clave de almacenamiento inválida");
        }
        return settings.prefix().isEmpty() ? key : settings.prefix() + "/" + key;
    }

    @Override
    public void put(String key, InputStream content) throws IOException {
        // Los archivos están acotados por logyx.documents.max-size-bytes (10 MB): se envían con longitud conocida.
        byte[] bytes = content.readAllBytes();
        try {
            s3.putObject(b -> {
                b.bucket(settings.bucket()).key(objectKey(key)).contentLength((long) bytes.length);
                if (settings.sse()) b.serverSideEncryption(ServerSideEncryption.AES256);
            }, RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new IOException("No se pudo guardar el objeto", e);
        }
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return s3.getObject(b -> b.bucket(settings.bucket()).key(objectKey(key)));
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException(key);
        } catch (SdkException e) {
            throw new IOException("No se pudo leer el objeto", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(settings.bucket()).key(objectKey(key)));
        } catch (SdkException e) {
            Log.warnf("No se pudo borrar el objeto %s: %s", key, e.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}
