package pe.logyx.alerts.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Alertas implícitas (V24, 2.2 + 2.3): ¿una carga publicada interesa a un transportista por las rutas que declaró en
 * las preferencias de su organización? Coincide si (1) origen y destino son una de sus rutas, (2) el tipo de carga está
 * entre los que declaró (si declaró alguno) y (3) el precio sugerido por km llega a su mínimo (si lo declaró). Que algún
 * vehículo pueda ofertar, el tope por hora, el bloqueo de la PYME y no duplicar con una búsqueda guardada los decide
 * {@code LoadAlertMatcher}.
 */
public final class RouteInterestMatcher {

    public record Route(City origin, City destination) {}

    /** Lo declarado por la organización transportista. */
    public record Interest(Set<Route> routes, Set<CargoType> cargoTypes, BigDecimal minPricePerKm) {}

    /** Lo que importa de la carga. {@code suggestedPrice}/{@code distanceKm} pueden faltar en solicitudes viejas. */
    public record Load(City origin, City destination, CargoType cargoType, BigDecimal suggestedPrice, BigDecimal distanceKm) {}

    private RouteInterestMatcher() {}

    public static boolean matches(Interest i, Load load) {
        if (!i.routes().contains(new Route(load.origin(), load.destination()))) return false;
        if (!i.cargoTypes().isEmpty() && !i.cargoTypes().contains(load.cargoType())) return false;
        if (i.minPricePerKm() == null) return true;
        BigDecimal perKm = pricePerKm(load);
        return perKm != null && perKm.compareTo(i.minPricePerKm()) >= 0;
    }

    /** Precio sugerido por km (2 decimales), o null si no hay precio o km. */
    public static BigDecimal pricePerKm(Load load) {
        if (load.suggestedPrice() == null || load.distanceKm() == null || load.distanceKm().signum() <= 0) return null;
        return load.suggestedPrice().divide(load.distanceKm(), 2, RoundingMode.HALF_UP);
    }

    public static boolean withinHourlyLimit(long sentLastHour, int maxPerHour) {
        return sentLastHour < maxPerHour;
    }
}
