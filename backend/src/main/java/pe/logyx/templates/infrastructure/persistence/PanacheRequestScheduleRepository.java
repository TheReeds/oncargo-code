package pe.logyx.templates.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.templates.application.port.out.RequestScheduleRepository;
import pe.logyx.templates.domain.model.RequestSchedule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheRequestScheduleRepository implements RequestScheduleRepository {

    @Override
    public Optional<RequestSchedule> findById(UUID id) {
        return RequestScheduleEntity.<RequestScheduleEntity>findByIdOptional(id).map(RequestScheduleEntity::toDomain);
    }

    @Override
    public Optional<RequestSchedule> findByTemplate(UUID templateId) {
        return RequestScheduleEntity.<RequestScheduleEntity>find("templateId", templateId).firstResultOptional()
                .map(RequestScheduleEntity::toDomain);
    }

    @Override
    public List<RequestSchedule> listByTemplates(Collection<UUID> templateIds) {
        if (templateIds.isEmpty()) return List.of();
        return RequestScheduleEntity.<RequestScheduleEntity>list("templateId in ?1", templateIds).stream()
                .map(RequestScheduleEntity::toDomain).toList();
    }

    @Override
    public void save(RequestSchedule schedule) {
        RequestScheduleEntity e = RequestScheduleEntity.findById(schedule.id());
        if (e == null) {
            e = new RequestScheduleEntity();
            e.apply(schedule);
            e.persist();
        } else {
            e.apply(schedule);
        }
    }

    @Override
    public void delete(UUID id) {
        RequestScheduleEntity.delete("id", id);
    }

    @Override
    public void lock(UUID scheduleId) {
        RequestScheduleEntity.getEntityManager().createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, "schedule:" + scheduleId).getSingleResult();
    }
}
