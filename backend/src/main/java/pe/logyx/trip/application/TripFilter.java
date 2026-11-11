package pe.logyx.trip.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Filtros opcionales de la búsqueda de viajes publicados (PYME). Las fechas son del día de salida (hora de Lima). */
public record TripFilter(City originCity, City destinationCity, LocalDate fromDate, LocalDate toDate,
                         BigDecimal minWeightKg) {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    public static TripFilter none() {
        return new TripFilter(null, null, null, null, null);
    }

    public static TripFilter of(String originCity, String destinationCity, String fromDate, String toDate,
                                BigDecimal minWeightKg) {
        LocalDate from = date(fromDate, "fromDate");
        LocalDate to = date(toDate, "toDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "fromDate no puede ser posterior a toDate", "fromDate");
        }
        if (minWeightKg != null && minWeightKg.signum() <= 0) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "minWeightKg debe ser mayor a 0", "minWeightKg");
        }
        return new TripFilter(city(originCity, "originCity"), city(destinationCity, "destinationCity"), from, to,
                minWeightKg);
    }

    /** Inicio (inclusive) del rango de salida, o null. */
    public OffsetDateTime departureFrom() {
        return fromDate == null ? null : fromDate.atStartOfDay(LIMA).toOffsetDateTime();
    }

    /** Fin (exclusivo) del rango de salida, o null. */
    public OffsetDateTime departureBefore() {
        return toDate == null ? null : toDate.plusDays(1).atStartOfDay(LIMA).toOffsetDateTime();
    }

    private static LocalDate date(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (use yyyy-MM-dd): " + value, field);
        }
    }

    private static City city(String code, String field) {
        if (code == null || code.isBlank()) return null;
        return City.parse(code).orElseThrow(() -> BusinessException.badRequest("LOGYX-PRC-001",
                "Ciudad fuera del corredor atendido: " + code, field));
    }
}
