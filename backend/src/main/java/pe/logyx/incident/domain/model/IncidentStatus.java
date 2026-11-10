package pe.logyx.incident.domain.model;

/** Estado de una incidencia. */
public enum IncidentStatus {
    OPEN, IN_REVIEW, RESOLVED, CLOSED;

    /** OPEN o IN_REVIEW: la incidencia retiene el envío (RN11). */
    public boolean isActive() { return this == OPEN || this == IN_REVIEW; }
}
