package pe.logyx.shared.domain;

/**
 * Violación de una regla de negocio. El código sigue el formato LOGYX-XXX-NNN
 * documentado en E3 para que los clientes puedan reaccionar sin parsear texto.
 * {@code field} (opcional) nombra la propiedad del request que causó el error,
 * igual que en los errores de Bean Validation.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int status;
    private final String field;

    public BusinessException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public BusinessException(int status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException badRequest(String code, String message, String field) {
        return new BusinessException(400, code, message, field);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    public static BusinessException conflict(String code, String message, String field) {
        return new BusinessException(409, code, message, field);
    }

    /**
     * 404 con el género correcto (F42): "Solicitud no encontrada", "Envío no encontrado". Son femeninos los sustantivos
     * terminados en -a, -ción, -dad y -tud (Solicitud, Oferta, Incidencia, Organización, Parada, Reserva…).
     */
    public static BusinessException notFound(String what) {
        String w = what.trim().toLowerCase(java.util.Locale.ROOT);
        boolean feminine = w.endsWith("a") || w.endsWith("ción") || w.endsWith("dad") || w.endsWith("tud");
        return new BusinessException(404, "LOGYX-GEN-404", what + (feminine ? " no encontrada" : " no encontrado"));
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, "LOGYX-GEN-403", message);
    }

    public String code() { return code; }
    public int status() { return status; }
    /** Propiedad del request asociada al error, o {@code null}. */
    public String field() { return field; }
}
