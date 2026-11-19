package pe.logyx.catalog.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.catalog.application.port.out.CalendarDayRepository;
import pe.logyx.catalog.domain.City;
import pe.logyx.catalog.domain.model.CalendarDay;
import pe.logyx.catalog.domain.model.CalendarDayKind;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** SQL nativo sobre {@code calendar_days} (el arreglo de ciudades se lee como texto separado por comas). */
@ApplicationScoped
public class NativeCalendarDayRepository implements CalendarDayRepository {

    private static final String SELECT = """
            SELECT id, day, kind, name, array_to_string(cities, ','), skip_recurrence, note, created_by, created_at
              FROM calendar_days
            """;

    @Inject EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<CalendarDay> all() {
        List<Object[]> rows = em.createNativeQuery(SELECT + " ORDER BY day, name").getResultList();
        return rows.stream().map(NativeCalendarDayRepository::map).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<CalendarDay> find(UUID id) {
        List<Object[]> rows = em.createNativeQuery(SELECT + " WHERE id = ?1").setParameter(1, id).getResultList();
        return rows.stream().findFirst().map(NativeCalendarDayRepository::map);
    }

    @Override
    public boolean exists(LocalDate day, String kind, String name) {
        return ((Number) em.createNativeQuery(
                        "SELECT count(*) FROM calendar_days WHERE day = ?1 AND kind = ?2 AND lower(name) = lower(?3)")
                .setParameter(1, day).setParameter(2, kind).setParameter(3, name).getSingleResult()).longValue() > 0;
    }

    @Override
    public void insert(CalendarDay d) {
        String cities = d.cities().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse(null);
        em.createNativeQuery("""
                INSERT INTO calendar_days (id, day, kind, name, cities, skip_recurrence, note, created_by)
                VALUES (?1, ?2, ?3, ?4, CASE WHEN CAST(?5 AS text) IS NULL THEN NULL
                                             ELSE string_to_array(CAST(?5 AS text), ',') END, ?6, ?7, ?8)
                """).setParameter(1, d.id()).setParameter(2, d.day()).setParameter(3, d.kind().name())
                .setParameter(4, d.name()).setParameter(5, cities).setParameter(6, d.skipRecurrence())
                .setParameter(7, d.note()).setParameter(8, d.createdBy()).executeUpdate();
    }

    @Override
    public boolean delete(UUID id) {
        return em.createNativeQuery("DELETE FROM calendar_days WHERE id = ?1").setParameter(1, id).executeUpdate() == 1;
    }

    private static CalendarDay map(Object[] r) {
        Set<City> cities = new HashSet<>();
        String raw = (String) r[4];
        if (raw != null && !raw.isBlank()) {
            for (String c : raw.split(",")) City.parse(c).ifPresent(cities::add);
        }
        return new CalendarDay(uuid(r[0]), date(r[1]), CalendarDayKind.valueOf((String) r[2]), (String) r[3], cities,
                (Boolean) r[5], (String) r[6], uuid(r[7]), odt(r[8]));
    }

    private static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static LocalDate date(Object o) {
        return o instanceof LocalDate l ? l : o instanceof Date d ? d.toLocalDate() : LocalDate.parse(o.toString());
    }

    private static OffsetDateTime odt(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof java.time.Instant i) return i.atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(v.toString());
    }
}
