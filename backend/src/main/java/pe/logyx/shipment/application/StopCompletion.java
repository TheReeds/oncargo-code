package pe.logyx.shipment.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Datos del cierre de una parada (roadmap 2.5): hora del dispositivo, peso medido en el recojo y quién recibe la
 * entrega. Todos opcionales; los valida {@code StopCompletionPolicy}.
 */
public record StopCompletion(OffsetDateTime occurredAt, BigDecimal weightKg, String receivedByName,
                             String receivedByDoc) {
    public static final StopCompletion NONE = new StopCompletion(null, null, null, null);
}
