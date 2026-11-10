package pe.logyx.incident.domain;

import pe.logyx.incident.domain.model.IncidentStatus;
import pe.logyx.shared.domain.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * Ciclo de vida de una incidencia (lo mueve el operador, RN11):
 * <pre>
 *   OPEN ──► IN_REVIEW ──► RESOLVED ──► CLOSED
 *     └──────────┴──────────► RESOLVED / CLOSED (descartada)
 * </pre>
 * Resolver exige notas de resolución. CLOSED es terminal.
 */
public final class IncidentStatusPolicy {

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED = Map.of(
            IncidentStatus.OPEN, Set.of(IncidentStatus.IN_REVIEW, IncidentStatus.RESOLVED, IncidentStatus.CLOSED),
            IncidentStatus.IN_REVIEW, Set.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED),
            IncidentStatus.RESOLVED, Set.of(IncidentStatus.CLOSED),
            IncidentStatus.CLOSED, Set.of());

    private IncidentStatusPolicy() {}

    public static void ensureTransition(IncidentStatus from, IncidentStatus to, String resolutionNotes) {
        if (!ALLOWED.get(from).contains(to)) {
            throw BusinessException.conflict("LOGYX-INC-002",
                    "No se puede pasar una incidencia de " + from + " a " + to, "status");
        }
        if (to == IncidentStatus.RESOLVED && (resolutionNotes == null || resolutionNotes.isBlank())) {
            throw BusinessException.badRequest("LOGYX-INC-003", "Indica las notas de resolución", "resolutionNotes");
        }
    }
}
