package pe.logyx.alerts.domain;

import pe.logyx.alerts.domain.model.SavedSearch;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ¿Una carga recién publicada coincide con los filtros de una búsqueda guardada? (2.3). Solo mira los filtros de la
 * búsqueda; que el vehículo pueda ofertar (RN02 por fecha) lo decide {@code LoadAlertMatcher} con la agenda.
 */
public final class SavedSearchMatcher {

    /** Lo que importa de la carga publicada. */
    public record Load(City origin, City destination, CargoType cargoType, BigDecimal weightKg, LocalDate pickupDate) {}

    private SavedSearchMatcher() {}

    public static boolean matches(SavedSearch s, Load load) {
        if (!s.active()) return false;
        if (s.originCity() != null && s.originCity() != load.origin()) return false;
        if (s.destinationCity() != null && s.destinationCity() != load.destination()) return false;
        if (s.cargoType() != null && s.cargoType() != load.cargoType()) return false;
        if (s.pickupFrom() != null && load.pickupDate().isBefore(s.pickupFrom())) return false;
        if (s.pickupTo() != null && load.pickupDate().isAfter(s.pickupTo())) return false;
        return s.maxWeightKg() == null || load.weightKg() == null || load.weightKg().compareTo(s.maxWeightKg()) <= 0;
    }

    /** Límite por hora: con {@code sentLastHour} avisos ya enviados por esa búsqueda, ¿cabe otro? */
    public static boolean withinHourlyLimit(long sentLastHour, int maxPerHour) {
        return sentLastHour < maxPerHour;
    }
}
