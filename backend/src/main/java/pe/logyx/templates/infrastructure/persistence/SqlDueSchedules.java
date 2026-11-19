package pe.logyx.templates.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.templates.application.port.out.DueSchedules;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Se ejecuta dentro de {@code SystemTx} (sin RLS): ve las programaciones de todas las organizaciones. */
@ApplicationScoped
public class SqlDueSchedules implements DueSchedules {

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Due> find(OffsetDateTime now, int max) {
        List<Object[]> rows = em.createNativeQuery("SELECT s.id, s.organization_id, t.created_by "
                        + "FROM request_schedules s JOIN request_templates t ON t.id = s.template_id "
                        + "WHERE NOT s.paused AND s.finished_at IS NULL AND t.archived_at IS NULL AND s.next_run_at <= ?1 "
                        + "ORDER BY s.next_run_at LIMIT ?2").setParameter(1, now).setParameter(2, max).getResultList();
        return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1], (UUID) r[2])).toList();
    }
}
