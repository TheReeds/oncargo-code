package pe.logyx.catalog.application.port.out;

import pe.logyx.catalog.domain.model.CalendarDay;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Días del calendario (V24 {@code calendar_days}). */
public interface CalendarDayRepository {
    List<CalendarDay> all();

    Optional<CalendarDay> find(UUID id);

    boolean exists(java.time.LocalDate day, String kind, String name);

    void insert(CalendarDay day);

    boolean delete(UUID id);
}
