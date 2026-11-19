package pe.logyx.reports.domain;

import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Mes de una liquidación o resumen (2.11): {@code yyyy-MM}, no futuro. */
public record StatementPeriod(YearMonth month) {

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre"};

    public static StatementPeriod parse(String raw, LocalDate today) {
        YearMonth m;
        try {
            m = YearMonth.parse(raw == null ? "" : raw.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Indica el mes como yyyy-MM (p. ej. 2026-11)", "period");
        }
        if (m.isAfter(YearMonth.from(today))) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "El mes no puede ser futuro", "period");
        }
        return new StatementPeriod(m);
    }

    public LocalDate first() { return month.atDay(1); }

    public LocalDate last() { return month.atEndOfMonth(); }

    /** «noviembre de 2026». */
    public String label() {
        return MONTHS[month.getMonthValue() - 1] + " de " + month.getYear();
    }

    /** {@code 202611}. */
    public String compact() {
        return String.format(Locale.ROOT, "%04d%02d", month.getYear(), month.getMonthValue());
    }
}
