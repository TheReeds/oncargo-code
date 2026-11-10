package pe.logyx.shared.api;

import java.time.Instant;

/** Contrato de respuesta base (E3, sección 5.2): {@code { data, meta }}. */
public record ApiResponse<T>(T data, Meta meta) {

    public record Meta(Instant timestamp) {}

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(Instant.now()));
    }
}
