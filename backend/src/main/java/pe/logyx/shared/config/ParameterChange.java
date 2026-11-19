package pe.logyx.shared.config;

import pe.logyx.shared.config.domain.ParameterKey;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Un cambio del historial. {@code changedByName/Email} pueden ser nulos si el perfil ya no existe. */
public record ParameterChange(long id, ParameterKey key, int version, String previousValue, String newValue,
                              String reason, UUID changedBy, String changedByName, String changedByEmail,
                              OffsetDateTime changedAt) {}
