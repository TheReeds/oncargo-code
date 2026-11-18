package pe.logyx.operator.application.port.out;

import pe.logyx.operator.application.AuditFilter;
import pe.logyx.operator.application.AuditRecord;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;

/** Lectura del rastro de auditoría para el operador (RLS audit_operator; perfiles y organizaciones por sus políticas). */
public interface AuditLogReader {
    /** Más recientes primero. */
    Paged<AuditRecord> search(AuditFilter filter, PageRequest page);

    /** Acciones distintas registradas, en orden alfabético. */
    List<String> distinctActions();
}
