package pe.logyx.catalog.domain;

import org.junit.jupiter.api.Test;
import pe.logyx.catalog.domain.model.CalendarDay;
import pe.logyx.catalog.domain.model.CalendarDayKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HolidayCalendarTest {

    private static CalendarDay day(String date, CalendarDayKind kind, Set<City> cities, boolean skip) {
        return new CalendarDay(UUID.randomUUID(), LocalDate.parse(date), kind, "x", cities, skip, null, null, null);
    }

    @Test
    void saltaLoNacionalYLoDeLaCiudadDeOrigen() {
        var cal = new HolidayCalendar(List.of(
                day("2026-12-25", CalendarDayKind.HOLIDAY, Set.of(), true),
                day("2026-12-10", CalendarDayKind.STRIKE, Set.of(City.PUNO), true),
                day("2026-12-11", CalendarDayKind.OTHER, Set.of(), false)));
        assertTrue(cal.skipsRecurrence(LocalDate.parse("2026-12-25"), City.LIMA));
        assertTrue(cal.skipsRecurrence(LocalDate.parse("2026-12-10"), City.PUNO));
        assertFalse(cal.skipsRecurrence(LocalDate.parse("2026-12-10"), City.LIMA));
        assertFalse(cal.skipsRecurrence(LocalDate.parse("2026-12-11"), City.LIMA), "aviso que no salta");
        assertFalse(cal.skipsRecurrence(LocalDate.parse("2026-07-28"), City.LIMA),
                "el año tiene feriados cargados: manda el calendario del operador");
        assertEquals(1, cal.on(LocalDate.parse("2026-12-10"), City.PUNO).size());
        assertEquals(0, cal.on(LocalDate.parse("2026-12-10"), null).size());
    }

    @Test
    void unAnoSinCargarUsaLaListaNacional() {
        var cal = new HolidayCalendar(List.of());
        assertTrue(cal.skipsRecurrence(LocalDate.parse("2031-07-28"), City.LIMA));
        assertTrue(cal.skipsRecurrence(LocalDate.of(2031, 4, 11), City.LIMA), "Viernes Santo 2031");
        assertFalse(cal.skipsRecurrence(LocalDate.parse("2031-07-27"), City.LIMA));
        assertEquals(LocalDate.of(2031, 4, 13), NationalHolidays.easter(2031));
    }
}
