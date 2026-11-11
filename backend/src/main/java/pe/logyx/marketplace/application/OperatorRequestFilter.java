package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.util.Arrays;

/** Filtros del listado global de solicitudes del operador. Las fechas son de creación (hora de Lima), inclusive. */
public record OperatorRequestFilter(ShipmentRequest.Status status, City originCity, City destinationCity,
                                    LocalDate fromDate, LocalDate toDate) {

    public static OperatorRequestFilter of(String status, String originCity, String destinationCity,
                                           String fromDate, String toDate) {
        LocalDate from = MarketplaceFilter.date(fromDate, "fromDate");
        LocalDate to = MarketplaceFilter.date(toDate, "toDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "fromDate no puede ser posterior a toDate", "fromDate");
        }
        return new OperatorRequestFilter(status(status), city(originCity, "originCity"),
                city(destinationCity, "destinationCity"), from, to);
    }

    private static ShipmentRequest.Status status(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(ShipmentRequest.Status.values()).filter(s -> s.name().equalsIgnoreCase(value.trim()))
                .findFirst().orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                        "Estado inválido: " + value, "status"));
    }

    private static City city(String code, String field) {
        if (code == null || code.isBlank()) return null;
        return City.parse(code).orElseThrow(() -> BusinessException.badRequest("LOGYX-PRC-001",
                "Ciudad fuera del corredor atendido: " + code, field));
    }
}
