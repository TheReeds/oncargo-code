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
 * Resolver exige notas de resolución, y también cerrar una activa (descartarla, F27). CLOSED es terminal.
 * Al dejar de estar activa (RESOLVED o CLOSED desde OPEN/IN_REVIEW) el operador fija además el estado final del
 * envío ({@link IncidentOutcomePolicy}, RF55).
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
        if (deactivates(from, to) && (resolutionNotes == null || resolutionNotes.isBlank())) {
            throw BusinessException.badRequest("LOGYX-INC-003", "Indica el motivo o las notas de resolución",
                    "resolutionNotes");
        }
    }

    /** La transición libera el envío congelado (RN11): hay que fijar su estado final (RF55). */
    public static boolean deactivates(IncidentStatus from, IncidentStatus to) {
        return from.isActive() && !to.isActive();
    }
}
