package pe.logyx.operator.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.operator.application.port.out.AuditLogReader;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.domain.Paged;

import java.util.List;

/** Visor de auditoría del operador (F05). Corre bajo RLS: solo el tipo OPERATOR ve {@code audit_log} completo. */
@ApplicationScoped
@Transactional
public class AuditQueries {

    @Inject AuditLogReader reader;

    public Paged<AuditRecord> search(AuditFilter filter, PageRequest page) {
        return reader.search(filter, page);
    }

    public List<String> actions() {
        return reader.distinctActions();
    }
}
