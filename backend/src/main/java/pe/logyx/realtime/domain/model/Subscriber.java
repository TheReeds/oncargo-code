package pe.logyx.realtime.domain.model;

import pe.logyx.shared.tenancy.OrgType;

import java.util.Objects;
import java.util.UUID;

/** Quién está conectado al stream: sale del JWT, nunca del cliente. */
public record Subscriber(UUID userId, UUID orgId, OrgType orgType, boolean driver) {
    public Subscriber {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(orgId);
        Objects.requireNonNull(orgType);
    }
}
