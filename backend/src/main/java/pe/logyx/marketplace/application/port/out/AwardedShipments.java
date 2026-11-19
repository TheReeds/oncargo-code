package pe.logyx.marketplace.application.port.out;

import pe.logyx.marketplace.domain.BackupAwardPolicy;

import java.util.Optional;
import java.util.UUID;

/** Lo que marketplace necesita saber del envío de una solicitud (el módulo shipment no depende de marketplace). */
public interface AwardedShipments {
    /** El último envío de la solicitud (cancelado o no), si existe. Bajo la RLS de quien llama. */
    Optional<BackupAwardPolicy.AwardedShipment> latest(UUID requestId);
}
