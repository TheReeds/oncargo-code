package pe.logyx.shared.domain;

/**
 * Teléfono peruano de 9 dígitos, con prefijo +51 / 51 opcional y separadores (espacio, punto, guion, paréntesis).
 * Se guarda normalizado: solo los 9 dígitos. {@code LOGYX-VAL-002} con {@code field} si no cumple.
 * No es RN07: es un dato estructurado de contacto (se muestra solo a las partes tras adjudicar).
 */
public final class PeruPhone {

    private PeruPhone() {}

    /** Los 9 dígitos, {@code null} si está vacío; lanza LOGYX-VAL-002 si no es válido. */
    public static String normalize(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("+51")) digits = digits.substring(3);
        else if (digits.length() == 11 && digits.startsWith("51")) digits = digits.substring(2);
        if (!digits.matches("\\d{9}")) {
            throw BusinessException.badRequest("LOGYX-VAL-002",
                    "Ingresa un teléfono de 9 dígitos (con +51 opcional)", field);
        }
        return digits;
    }
}
