package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Filtros y orden de "Mis solicitudes" de la PYME (servidor, no cliente). {@code pickupFrom/pickupTo} son fechas de
 * recojo, inclusive. {@code q}: código (con o sin guion/espacios), referencia propia, ciudad de la ruta o parte de la
 * descripción. {@code withBids}: publicadas con al menos una oferta por revisar (no retirada).
 */
public record MyRequestFilter(ShipmentRequest.Status status, City originCity, City destinationCity, LocalDate pickupFrom,
                              LocalDate pickupTo, String q, boolean withBids, Sort sort) {

    public enum Sort {
        RECENT("recent"), PICKUP("pickup"), BIDS("bids"), PRICE_DESC("price_desc"), PRICE_ASC("price_asc");

        public final String param;

        Sort(String param) {
            this.param = param;
        }

        static Sort parse(String value) {
            if (value == null || value.isBlank()) return RECENT;
            return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value.trim())).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                            "Orden inválido: use recent, pickup, bids, price_desc o price_asc", "sort"));
        }
    }

    public static MyRequestFilter none() {
        return new MyRequestFilter(null, null, null, null, null, null, false, Sort.RECENT);
    }

    public static MyRequestFilter of(String status, String originCity, String destinationCity, String fromDate,
                                     String toDate, String q, Boolean withBids, String sort) {
        LocalDate from = MarketplaceFilter.date(fromDate, "fromDate");
        LocalDate to = MarketplaceFilter.date(toDate, "toDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "fromDate no puede ser posterior a toDate", "fromDate");
        }
        String text = q == null || q.isBlank() ? null : q.trim();
        if (text != null && text.length() > 80) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La búsqueda admite hasta 80 caracteres", "q");
        }
        return new MyRequestFilter(status(status), city(originCity, "originCity"), city(destinationCity, "destinationCity"),
                from, to, text, Boolean.TRUE.equals(withBids), Sort.parse(sort));
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
