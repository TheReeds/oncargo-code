package pe.logyx.incident.application;

import pe.logyx.incident.domain.model.IncidentSeverity;
import pe.logyx.incident.domain.model.IncidentStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Filtros de la bandeja de incidencias del operador (2.7). {@code breachedAt}: solo con el SLA vencido a esa hora;
 * {@code byDueDate}: las que vencen antes, primero.
 */
public record OperatorIncidentFilter(Collection<IncidentStatus> statuses, Collection<IncidentSeverity> severities,
                                     UUID assigneeProfileId, boolean unassigned, OffsetDateTime breachedAt,
                                     UUID organizationId, boolean byDueDate) {}
