package pe.logyx.shared.config;

import pe.logyx.shared.config.domain.ParameterKey;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Valor efectivo de un parámetro: el del operador si lo cambió ({@code version >= 1}) o el de la configuración
 * ({@code version = 0}, {@code updatedAt}/{@code updatedBy} nulos).
 */
public record ParameterValue(ParameterKey key, String value, String defaultValue, int version, String reason,
                             UUID updatedBy, OffsetDateTime updatedAt) {
    public boolean overridden() { return version > 0; }
}
