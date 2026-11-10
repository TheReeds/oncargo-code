package pe.logyx.documents.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/**
 * Almacenamiento de archivos. MVP: sistema de archivos local ({@code logyx.documents.storage-dir}); el contrato
 * está pensado para S3/MinIO (clave → objeto, sin rutas del cliente). Las claves las genera el servidor.
 */
public interface DocumentStorage {
    /** Guarda el contenido completo bajo {@code key} (lo reemplaza si existía). */
    void put(String key, InputStream content) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key);
}
