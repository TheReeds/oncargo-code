package pe.logyx.shared.domain;

/**
 * Violación de una regla de negocio. El código sigue el formato LOGYX-XXX-NNN
 * documentado en E3 para que los clientes puedan reaccionar sin parsear texto.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int status;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(404, "LOGYX-GEN-404", what + " no encontrado");
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, "LOGYX-GEN-403", message);
    }

    public String code() { return code; }
    public int status() { return status; }
}
