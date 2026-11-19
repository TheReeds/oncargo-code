package pe.logyx.catalog.domain.model;

import pe.logyx.catalog.domain.City;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Un día del calendario de la plataforma (V24 {@code calendar_days}, roadmap 2.14): feriado nacional, paro o aviso.
 * {@code cities} vacío = todo el país. {@code skipRecurrence}: las cargas recurrentes que saltan feriados no se
 * publican para recoger ese día.
 */
public record CalendarDay(UUID id, LocalDate day, CalendarDayKind kind, String name, Set<City> cities,
                          boolean skipRecurrence, String note, UUID createdBy, OffsetDateTime createdAt) {

    public CalendarDay {
        cities = cities == null ? Set.of() : Set.copyOf(cities);
    }

    public boolean national() { return cities.isEmpty(); }

    /** Afecta a esa ciudad (o a todas si es nacional). {@code city} nulo = solo los nacionales. */
    public boolean affects(City city) {
        return national() || (city != null && cities.contains(city));
    }
}
