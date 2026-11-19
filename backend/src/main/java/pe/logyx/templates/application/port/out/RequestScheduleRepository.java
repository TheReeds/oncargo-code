package pe.logyx.templates.application.port.out;

import pe.logyx.templates.domain.model.RequestSchedule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestScheduleRepository {
    Optional<RequestSchedule> findById(UUID id);
    Optional<RequestSchedule> findByTemplate(UUID templateId);
    List<RequestSchedule> listByTemplates(Collection<UUID> templateIds);
    void save(RequestSchedule schedule);
    void delete(UUID id);
    /** Candado de transacción por programación: dos instancias del job no ejecutan la misma ocurrencia. */
    void lock(UUID scheduleId);
}
