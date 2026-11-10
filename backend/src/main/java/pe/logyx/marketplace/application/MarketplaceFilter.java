package pe.logyx.marketplace.application;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.shared.domain.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

/** Filtros y orden opcionales del marketplace del transportista. */
public record MarketplaceFilter(City originCity, City destinationCity, LocalDate pickupFrom, LocalDate pickupTo,
                                BigDecimal maxWeightKg, CargoType cargoType, Sort sort) {

    public enum Sort {
        /** Fecha de recojo más próxima primero (por omisión). */
        PICKUP("pickup"),
        /** Precio sugerido de mayor a menor. */
        PRICE_DESC("price_desc"),
        /** Publicadas más recientemente primero. */
        RECENT("recent");

        public final String param;

        Sort(String param) {
            this.param = param;
        }

        static Sort parse(String value) {
            if (value == null || value.isBlank()) return PICKUP;
            return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value)).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                            "Orden inválido: use pickup, price_desc o recent", "sort"));
        }
    }

    public static MarketplaceFilter none() {
        return new MarketplaceFilter(null, null, null, null, null, null, Sort.PICKUP);
    }

    /** Arma el filtro desde los parámetros crudos del request; los inválidos dan 400 con {@code field}. */
    public static MarketplaceFilter of(String originCity, String destinationCity, LocalDate pickupFrom,
                                       LocalDate pickupTo, BigDecimal maxWeightKg, String cargoType, String sort) {
        if (pickupFrom != null && pickupTo != null && pickupFrom.isAfter(pickupTo)) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "pickupFrom no puede ser posterior a pickupTo",
                    "pickupFrom");
        }
        if (maxWeightKg != null && maxWeightKg.signum() <= 0) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "maxWeightKg debe ser mayor a 0", "maxWeightKg");
        }
        return new MarketplaceFilter(city(originCity, "originCity"), city(destinationCity, "destinationCity"),
                pickupFrom, pickupTo, maxWeightKg, cargo(cargoType), Sort.parse(sort));
    }

    /** Fecha ISO (yyyy-MM-dd) o 400 con el nombre del parámetro. */
    public static LocalDate date(String value, String field) {
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

    private static CargoType cargo(String code) {
        if (code == null || code.isBlank()) return null;
        return Arrays.stream(CargoType.values()).filter(c -> c.name().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001", "Tipo de carga inválido: " + code,
                        "cargoType"));
    }
}
