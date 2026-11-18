package pe.logyx.operator.application;

import pe.logyx.marketplace.application.MarketplaceFilter;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filtros del visor de auditoría del operador (F05). Todos opcionales. {@code from}/{@code to} son fechas de Lima,
 * inclusive. {@code actions}: coincidencia exacta con cualquiera de la lista.
 */
public record AuditFilter(UUID actorId, UUID organizationId, List<String> actions, String entity, UUID entityId,
                          LocalDate from, LocalDate to) {

    private static final int MAX_ACTIONS = 30;
    private static final int MAX_TEXT = 60;

    /** Arma el filtro desde los parámetros crudos; los inválidos dan 400 {@code LOGYX-VAL-001} con {@code field}. */
    public static AuditFilter of(String actorId, String organizationId, List<String> action, String entity,
                                 String entityId, String from, String to) {
        LocalDate f = MarketplaceFilter.date(from, "from");
        LocalDate t = MarketplaceFilter.date(to, "to");
        if (f != null && t != null && f.isAfter(t)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "from no puede ser posterior a to", "from");
        }
        List<String> actions = new ArrayList<>();
        if (action != null) {
            for (String raw : action) {
                if (raw == null) continue;
                for (String part : raw.split(",")) {
                    String a = part.trim();
                    if (a.isEmpty()) continue;
                    if (a.length() > MAX_TEXT) {
                        throw BusinessException.badRequest("LOGYX-VAL-001", "Acción inválida: " + a, "action");
                    }
                    actions.add(a);
                }
            }
        }
        if (actions.size() > MAX_ACTIONS) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Máximo " + MAX_ACTIONS + " acciones", "action");
        }
        String e = entity == null || entity.isBlank() ? null : entity.trim();
        if (e != null && e.length() > MAX_TEXT) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Tipo de entidad inválido: " + e, "entity");
        }
        return new AuditFilter(uuid(actorId, "actorId"), uuid(organizationId, "organizationId"),
                List.copyOf(actions), e, uuid(entityId, "entityId"), f, t);
    }

    private static UUID uuid(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Identificador inválido: " + raw, field);
        }
    }
}
