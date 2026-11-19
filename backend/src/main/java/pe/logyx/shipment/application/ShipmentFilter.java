package pe.logyx.shipment.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.Shipment;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filtros de «Mis envíos» (PYME y transportista, roadmap 1.5), en el servidor. {@code statuses}: uno o varios
 * estados (vacío = todos). Ciudades y fecha de recojo ({@code pickupFrom/pickupTo}, inclusive) son las de la solicitud
 * de origen. {@code q}: código del envío (con o sin guion), referencia propia de la PYME, placa, conductor o
 * descripción de la carga.
 */
public record ShipmentFilter(Set<Shipment.Status> statuses, City originCity, City destinationCity,
                             LocalDate pickupFrom, LocalDate pickupTo, String q) {

    public static ShipmentFilter none() {
        return new ShipmentFilter(Set.of(), null, null, null, null, null);
    }

    public boolean isEmpty() {
        return statuses.isEmpty() && originCity == null && destinationCity == null && pickupFrom == null
                && pickupTo == null && q == null;
    }

    /** {@code status}: un estado o varios separados por coma ({@code PENDING,IN_TRANSIT} = en curso). */
    public static ShipmentFilter of(String status, String originCity, String destinationCity, String fromDate,
                                    String toDate, String q) {
        LocalDate from = date(fromDate, "fromDate");
        LocalDate to = date(toDate, "toDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "fromDate no puede ser posterior a toDate", "fromDate");
        }
        String text = q == null || q.isBlank() ? null : q.trim();
        if (text != null && text.length() > 80) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La búsqueda admite hasta 80 caracteres", "q");
        }
        return new ShipmentFilter(statuses(status), city(originCity, "originCity"),
                city(destinationCity, "destinationCity"), from, to, text);
    }

    private static Set<Shipment.Status> statuses(String value) {
        if (value == null || value.isBlank()) return Set.of();
        EnumSet<Shipment.Status> out = EnumSet.noneOf(Shipment.Status.class);
        for (String part : value.split(",")) {
            if (part.isBlank()) continue;
            out.add(Arrays.stream(Shipment.Status.values()).filter(s -> s.name().equalsIgnoreCase(part.trim()))
                    .findFirst().orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                            "Estado inválido: " + part.trim(), "status")));
        }
        return Set.copyOf(out);
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
