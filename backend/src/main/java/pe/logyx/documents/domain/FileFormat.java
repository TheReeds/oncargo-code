package pe.logyx.documents.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos admitidos (jpg, png, pdf), reconocidos por su firma binaria ("magic bytes"), no por la extensión ni
 * por el Content-Type que declara el cliente.
 */
public enum FileFormat {
    JPEG("image/jpeg", "jpg", true, new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", "png", true, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", "pdf", false, new int[]{0x25, 0x50, 0x44, 0x46, 0x2D});   // %PDF-

    /** Bytes iniciales que hay que leer para reconocer cualquier formato. */
    public static final int HEADER_LENGTH = 8;

    public final String contentType;
    public final String extension;
    public final boolean image;
    private final int[] magic;

    FileFormat(String contentType, String extension, boolean image, int[] magic) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
        this.magic = magic;
    }

    public static Optional<FileFormat> detect(byte[] header) {
        return Arrays.stream(values()).filter(f -> f.matches(header)).findFirst();
    }

    private boolean matches(byte[] header) {
        if (header == null || header.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((header[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
