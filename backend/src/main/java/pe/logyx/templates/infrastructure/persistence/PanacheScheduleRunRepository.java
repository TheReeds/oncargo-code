package pe.logyx.templates.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.templates.application.port.out.ScheduleRunRepository;
import pe.logyx.templates.domain.model.ScheduleRun;
import pe.logyx.templates.domain.model.ScheduleRunStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheScheduleRunRepository implements ScheduleRunRepository {

    @Override
    public Optional<ScheduleRun> findById(UUID id) {
        return ScheduleRunEntity.<ScheduleRunEntity>findByIdOptional(id).map(ScheduleRunEntity::toDomain);
    }

    @Override
    public boolean exists(UUID scheduleId, LocalDate pickupDate) {
        return ScheduleRunEntity.count("scheduleId = ?1 and pickupDate = ?2", scheduleId, pickupDate) > 0;
    }

    @Override
    public List<ScheduleRun> listDrafts(UUID organizationId) {
        return ScheduleRunEntity.<ScheduleRunEntity>list("organizationId = ?1 and status = ?2 order by pickupDate",
                organizationId, ScheduleRunStatus.DRAFT).stream().map(ScheduleRunEntity::toDomain).toList();
    }

    @Override
    public List<ScheduleRun> listRecent(Collection<UUID> templateIds, int perTemplate) {
        if (templateIds.isEmpty()) return List.of();
        List<ScheduleRun> all = ScheduleRunEntity.<ScheduleRunEntity>list("templateId in ?1 order by createdAt desc, id",
                templateIds).stream().map(ScheduleRunEntity::toDomain).toList();
        java.util.Map<UUID, Integer> taken = new java.util.HashMap<>();
        List<ScheduleRun> out = new ArrayList<>();
        for (ScheduleRun r : all) {
            if (taken.merge(r.templateId(), 1, Integer::sum) <= perTemplate) out.add(r);
        }
        return out;
    }

    @Override
    public void save(ScheduleRun run) {
        ScheduleRunEntity e = ScheduleRunEntity.findById(run.id());
        if (e == null) {
            e = new ScheduleRunEntity();
            e.apply(run);
            e.persist();
        } else {
            e.apply(run);
        }
    }
}
