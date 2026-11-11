package pe.logyx.shipment.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Filtros del listado global de envíos del operador. Las ciudades son las de la solicitud de origen; las fechas son
 * de creación del envío (hora de Lima), inclusive.
 */
public record OperatorShipmentFilter(Shipment.Status status, City originCity, City destinationCity,
                                     LocalDate fromDate, LocalDate toDate) {

    public static OperatorShipmentFilter of(String status, String originCity, String destinationCity,
                                            String fromDate, String toDate) {
        LocalDate from = date(fromDate, "fromDate");
        LocalDate to = date(toDate, "toDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "fromDate no puede ser posterior a toDate", "fromDate");
        }
        return new OperatorShipmentFilter(status(status), city(originCity, "originCity"),
                city(destinationCity, "destinationCity"), from, to);
    }

    private static Shipment.Status status(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(Shipment.Status.values()).filter(s -> s.name().equalsIgnoreCase(value.trim()))
                .findFirst().orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                        "Estado inválido: " + value, "status"));
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
