package pe.logyx.templates.domain;

import pe.logyx.catalog.domain.NationalHolidays;

import java.time.LocalDate;

/**
 * Feriados nacionales fijos del Perú. Desde V24 (roadmap 2.14) la programación consulta el calendario editable del
 * operador ({@code catalog.application.CalendarService}); esto queda como alias de {@link NationalHolidays}.
 */
public final class PeruHolidays {

    private PeruHolidays() {}

    public static boolean isHoliday(LocalDate date) {
        return NationalHolidays.isHoliday(date);
    }

    static LocalDate easter(int year) {
        return NationalHolidays.easter(year);
    }
}
