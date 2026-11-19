package pe.logyx.notification.application.port.out;

import pe.logyx.notification.domain.model.NotificationTarget;

import java.util.Optional;
import java.util.UUID;

/**
 * Quién de {@code orgId} creó el recurso del aviso (la solicitud de la PYME, la oferta del transportista), para marcar
 * su copia como «Tuyo» (V16). Solo lectura; se consulta dentro del despacho del outbox (SystemTx).
 */
public interface ResourceAuthors {
    Optional<UUID> authorIn(UUID orgId, NotificationTarget type, UUID id);
}
