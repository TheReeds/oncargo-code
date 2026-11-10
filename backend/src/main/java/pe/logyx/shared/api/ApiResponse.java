package pe.logyx.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import pe.logyx.shared.domain.Paged;

import java.time.Instant;
import java.util.List;

/** Contrato de respuesta base (E3, sección 5.2): {@code { data, meta }}. */
public record ApiResponse<T>(T data, Meta meta) {

    /** {@code page}, {@code size} y {@code total} solo aparecen en listados paginados. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(Instant timestamp, Integer page, Integer size, Long total) {
        public Meta(Instant timestamp) {
            this(timestamp, null, null, null);
        }
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(Instant.now()));
    }

    public static <T> ApiResponse<List<T>> of(Paged<T> page) {
        return new ApiResponse<>(page.items(), new Meta(Instant.now(), page.request().page(),
                page.request().size(), page.total()));
    }
}
