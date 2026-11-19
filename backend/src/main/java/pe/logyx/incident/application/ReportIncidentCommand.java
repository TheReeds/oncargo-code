package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.IncidentCategory;
import pe.logyx.incident.domain.model.IncidentSeverity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Alta de incidencia v2 (2.7). Opcionales: severidad (por omisión la de la categoría), monto reclamado, adjuntos
 * (documentos del mismo envío ya subidos), ubicación y hora del dispositivo (botón «Emergencia» encolado sin señal),
 * ajuste relacionado y clave de idempotencia (un reintento no crea otra).
 */
public record ReportIncidentCommand(IncidentCategory category, String description, IncidentSeverity severity,
                                    BigDecimal claimedAmount, List<UUID> attachmentIds, BigDecimal latitude,
                                    BigDecimal longitude, OffsetDateTime occurredAt, UUID adjustmentId,
                                    String idempotencyKey) {

    public static ReportIncidentCommand simple(IncidentCategory category, String description) {
        return new ReportIncidentCommand(category, description, null, null, List.of(), null, null, null, null, null);
    }
}
