package pe.logyx.trip.application;

import pe.logyx.shared.domain.BusinessException;

import java.util.Arrays;

/** Orden de "mis viajes" del transportista. */
public enum TripSort {
    /** Salida más próxima primero (por omisión). */
    DEPARTURE("departure"),
    /** Salida más lejana primero. */
    DEPARTURE_DESC("departure_desc"),
    /** Publicados más recientemente primero. */
    RECENT("recent");

    public final String param;

    TripSort(String param) {
        this.param = param;
    }

    public static TripSort parse(String value) {
        if (value == null || value.isBlank()) return DEPARTURE;
        return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value.trim())).findFirst()
                .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                        "Orden inválido: use departure, departure_desc o recent", "sort"));
    }
}
