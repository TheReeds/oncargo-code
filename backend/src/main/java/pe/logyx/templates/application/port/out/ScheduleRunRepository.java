package pe.logyx.templates.application.port.out;

import pe.logyx.templates.domain.model.ScheduleRun;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleRunRepository {
    Optional<ScheduleRun> findById(UUID id);
    boolean exists(UUID scheduleId, LocalDate pickupDate);
    /** Borradores sin resolver de la organización, el recojo más próximo primero. */
    List<ScheduleRun> listDrafts(UUID organizationId);
    /** Las últimas ocurrencias de las plantillas dadas, más recientes primero (hasta {@code perTemplate} por plantilla). */
    List<ScheduleRun> listRecent(Collection<UUID> templateIds, int perTemplate);
    void save(ScheduleRun run);
}
