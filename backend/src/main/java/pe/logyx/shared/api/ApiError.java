package pe.logyx.shared.api;

import java.time.Instant;

/** Respuesta de error estándar: {@code { error: { code, message, field }, meta }}. */
public record ApiError(Body error, ApiResponse.Meta meta) {

    public record Body(String code, String message, String field) {}

    public static ApiError of(String code, String message, String field) {
        return new ApiError(new Body(code, message, field), new ApiResponse.Meta(Instant.now()));
    }
}
