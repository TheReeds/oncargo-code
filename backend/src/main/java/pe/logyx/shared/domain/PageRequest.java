package pe.logyx.shared.domain;

/** Paginación 0-based con tope de tamaño. */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) throw BusinessException.badRequest("LOGYX-VAL-001", "La página debe ser mayor o igual a 0", "page");
        if (size < 1 || size > MAX_SIZE) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "El tamaño debe estar entre 1 y " + MAX_SIZE, "size");
        }
    }

    /** Valores por omisión: página 0, tamaño {@value #DEFAULT_SIZE}. */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public int offset() { return page * size; }
}
