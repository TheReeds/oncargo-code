package pe.logyx.catalog.domain;

import pe.logyx.catalog.domain.model.CalendarDay;
import pe.logyx.catalog.domain.model.CalendarDayKind;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Reglas del calendario de la plataforma (2.14), en Java puro:
 * <ul>
 *   <li>Una carga recurrente que salta feriados no se publica para un día marcado {@code skipRecurrence} que afecta a
 *       su ciudad de origen (o a todo el país).</li>
 *   <li>Si el año no tiene <b>ningún</b> feriado cargado, rige la lista nacional fija ({@link NationalHolidays}): así
 *       un año sin cargar no deja de saltar Navidad.</li>
 * </ul>
 */
public final class HolidayCalendar {

    private final List<CalendarDay> days;

    public HolidayCalendar(Collection<CalendarDay> days) {
        this.days = List.copyOf(days);
    }

    /** Días que caen en {@code date} y afectan a {@code city} (nulo = solo nacionales). */
    public List<CalendarDay> on(LocalDate date, City city) {
        return days.stream().filter(d -> d.day().equals(date) && d.affects(city)).toList();
    }

    public boolean skipsRecurrence(LocalDate date, City city) {
        if (days.stream().anyMatch(d -> d.day().equals(date) && d.skipRecurrence() && d.affects(city))) return true;
        boolean yearLoaded = days.stream()
                .anyMatch(d -> d.kind() == CalendarDayKind.HOLIDAY && d.day().getYear() == date.getYear());
        return !yearLoaded && NationalHolidays.isHoliday(date);
    }
}
