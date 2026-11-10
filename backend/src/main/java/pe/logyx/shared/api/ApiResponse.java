package pe.logyx.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.logyx.shared.domain.Paged;
import pe.logyx.shared.domain.Slice;

import java.time.Instant;
import java.util.List;

/** Contrato de respuesta base (E3, sección 5.2): {@code { data, meta }}. */
public record ApiResponse<T>(T data, Meta meta) {

    /** {@code page}, {@code size}, {@code total} y {@code nextCursor} solo aparecen en listados paginados. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(Instant timestamp, Integer page, Integer size, Long total,
                       /** Listados por cursor: valor a enviar como {@code before} para la página siguiente (más antigua). */
                       String nextCursor) {
        public Meta(Instant timestamp) {
            this(timestamp, null, null, null, null);
        }
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(Instant.now()));
    }

    public static <T> ApiResponse<List<T>> of(Paged<T> page) {
        return new ApiResponse<>(page.items(), new Meta(Instant.now(), page.request().page(),
                page.request().size(), page.total(), null));
    }

    /** Listado por cursor: {@code meta.nextCursor} es null cuando no hay más resultados. */
    public static <T> ApiResponse<List<T>> of(Slice<T> slice) {
        return new ApiResponse<>(slice.items(), new Meta(Instant.now(), null, slice.size(), null, slice.nextCursor()));
    }
}
