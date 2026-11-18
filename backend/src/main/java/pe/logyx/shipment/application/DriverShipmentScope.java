package pe.logyx.shipment.application;

import pe.logyx.shared.domain.BusinessException;

import java.util.Arrays;

/** Qué envíos del conductor lista {@code GET /driver/shipments} (F22). */
public enum DriverShipmentScope {
    /** Pendientes y en tránsito, el más antiguo primero (comportamiento histórico, por omisión). */
    ACTIVE,
    /** Entregados y cancelados, el más reciente primero. */
    HISTORY,
    /** Primero los activos y luego el historial. */
    ALL;

    public static DriverShipmentScope parse(String raw) {
        if (raw == null || raw.isBlank()) return ACTIVE;
        return Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(raw.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                        "Alcance inválido: use ACTIVE, HISTORY o ALL", "scope"));
    }
}
