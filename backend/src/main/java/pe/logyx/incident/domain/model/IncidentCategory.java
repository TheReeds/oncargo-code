package pe.logyx.incident.domain.model;

/**
 * Categoría de incidencia (modelo de datos E2; V23 suma las de la Ola 2, roadmap 2.7). Cada una declara si
 * <b>congela</b> el avance del envío mientras está activa (RN11) o es <b>informativa</b> (F21: un retraso o una vía
 * bloqueada se avisan, no detienen el viaje), su severidad por omisión y su texto en español. Los clientes deben
 * tolerar valores nuevos.
 */
public enum IncidentCategory {
    CARGO_DAMAGE(true, IncidentSeverity.HIGH, "carga dañada"),
    DELAY(false, IncidentSeverity.LOW, "retraso"),
    LOST_CARGO(true, IncidentSeverity.CRITICAL, "extravío"),
    WRONG_DELIVERY(true, IncidentSeverity.HIGH, "entrega incorrecta"),
    DRIVER_CONDUCT(true, IncidentSeverity.MEDIUM, "conducta del conductor"),
    OTHER(true, IncidentSeverity.MEDIUM, "otro"),
    /** V23: el transportista no se presentó al recojo. */
    PICKUP_NO_SHOW(true, IncidentSeverity.HIGH, "el transportista no llegó al recojo"),
    /** V23: en el recojo no había carga ni quien la entregue. */
    SHIPPER_NO_SHOW(true, IncidentSeverity.HIGH, "no había carga en el recojo"),
    BREAKDOWN(true, IncidentSeverity.HIGH, "avería del vehículo"),
    ACCIDENT(true, IncidentSeverity.CRITICAL, "accidente"),
    THEFT(true, IncidentSeverity.CRITICAL, "robo"),
    ROAD_BLOCKED(false, IncidentSeverity.MEDIUM, "vía bloqueada"),
    PARTIAL_DELIVERY(true, IncidentSeverity.HIGH, "entrega parcial"),
    /** Reclamo de cobro (p. ej. un ajuste rechazado): no detiene el viaje. */
    PAYMENT_DISPUTE(false, IncidentSeverity.MEDIUM, "disputa de pago");

    private final boolean blocking;
    private final IncidentSeverity defaultSeverity;
    private final String label;

    IncidentCategory(boolean blocking, IncidentSeverity defaultSeverity, String label) {
        this.blocking = blocking;
        this.defaultSeverity = defaultSeverity;
        this.label = label;
    }

    /** true = congela el avance de paradas mientras está activa (RN11). */
    public boolean blocking() { return blocking; }
    public IncidentSeverity defaultSeverity() { return defaultSeverity; }
    /** En minúsculas, para insertar en una frase ("Se reportó una incidencia (robo)…"). */
    public String label() { return label; }
}
