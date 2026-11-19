package pe.logyx.templates.domain;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

/**
 * Feriados no laborables de alcance nacional en el Perú: los de fecha fija y Jueves/Viernes Santo. Base hasta que el
 * operador administre los feriados (roadmap 2.14), que reemplazará esta lista. No incluye feriados regionales.
 */
public final class PeruHolidays {

    private static final Set<MonthDay> FIXED = Set.of(
            MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(6, 29), MonthDay.of(7, 23), MonthDay.of(7, 28),
            MonthDay.of(7, 29), MonthDay.of(8, 6), MonthDay.of(8, 30), MonthDay.of(10, 8), MonthDay.of(11, 1),
            MonthDay.of(12, 8), MonthDay.of(12, 9), MonthDay.of(12, 25));

    private PeruHolidays() {}

    public static boolean isHoliday(LocalDate date) {
        if (FIXED.contains(MonthDay.from(date))) return true;
        LocalDate easter = easter(date.getYear());
        return date.equals(easter.minusDays(3)) || date.equals(easter.minusDays(2));   // Jueves y Viernes Santo
    }

    /** Domingo de Pascua (algoritmo anónimo gregoriano). */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25;
        int g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
