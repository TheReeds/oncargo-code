package pe.logyx.identity.application.port.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Pedidos de acceso ya registrados (en la auditoría: no hay tabla propia). */
public interface AccessRequestLog {
    /** Pedidos de {@code profileId} desde {@code since} (lectura bajo RLS: la auditoría de su organización). */
    long countSince(UUID profileId, OffsetDateTime since);
}
