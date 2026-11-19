package pe.logyx.activity.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una acción del rastro: qué ({@code action}, el código de la auditoría), sobre qué ({@code entity}/{@code entityId}),
 * cuándo y quién. {@code actorName}/{@code actorRole} solo vienen si quien mira puede ver a esa persona (RLS de
 * {@code profiles}: su propia organización, o el operador); si no, son null. {@code own} = la hizo mi organización.
 */
public record ActivityEntry(OffsetDateTime at, String action, String entity, UUID entityId, String actorName,
                            String actorRole, String organizationName, boolean own) {}
