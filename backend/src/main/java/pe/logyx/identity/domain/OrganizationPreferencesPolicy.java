package pe.logyx.identity.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.domain.model.OrganizationPreferences;
import pe.logyx.identity.domain.model.OrganizationPreferences.InterestRoute;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.OrgType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Reglas de las preferencias de la organización (V24, 2.2), en Java puro. Las ciudades y tipos de carga son los del
 * catálogo ({@link City}, {@link CargoType}: la API no admite otros). Normaliza (sin repetidos, orden de llegada) y
 * descarta lo que no aplica al tipo de organización.
 * <ul>
 *   <li>{@code LOGYX-ORG-010} ruta con origen = destino ({@code routes});</li>
 *   <li>{@code LOGYX-ORG-011} más de {@value #MAX_ROUTES} rutas ({@code routes});</li>
 *   <li>{@code LOGYX-ORG-012} horario de recojo incompleto o invertido ({@code defaultPickupTo});</li>
 *   <li>{@code LOGYX-ORG-013} plazo de pago fuera de 0–120 días ({@code paymentTermsDays});</li>
 *   <li>{@code LOGYX-ORG-014} precio mínimo por km ≤ 0 o mayor a S/ {@value #MAX_PRICE_PER_KM} ({@code minPricePerKm}).</li>
 * </ul>
 */
public final class OrganizationPreferencesPolicy {

    public static final int MAX_ROUTES = 20;
    public static final int MAX_PAYMENT_TERMS_DAYS = 120;
    public static final int MAX_PRICE_PER_KM = 100;

    private OrganizationPreferencesPolicy() {}

    public static OrganizationPreferences normalize(UUID orgId, OrgType type, List<City> cities, List<InterestRoute> routes,
                                                    List<CargoType> cargoTypes, Boolean routeAlerts, BigDecimal minPricePerKm,
                                                    java.time.LocalTime pickupFrom, java.time.LocalTime pickupTo,
                                                    Integer paymentTermsDays, UUID by, OffsetDateTime now) {
        List<InterestRoute> rs = new ArrayList<>(new LinkedHashSet<>(routes == null ? List.of() : routes));
        for (InterestRoute r : rs) {
            if (r.origin() == r.destination()) {
                throw BusinessException.badRequest("LOGYX-ORG-010", "Una ruta de interés necesita origen y destino distintos ("
                        + r.origin().label + ").", "routes");
            }
        }
        if (rs.size() > MAX_ROUTES) {
            throw BusinessException.badRequest("LOGYX-ORG-011", "Puedes declarar hasta " + MAX_ROUTES + " rutas de interés.",
                    "routes");
        }
        boolean carrier = type == OrgType.CARRIER;
        boolean pyme = type == OrgType.PYME;
        BigDecimal price = null;
        if (carrier && minPricePerKm != null) {
            if (minPricePerKm.signum() <= 0 || minPricePerKm.compareTo(BigDecimal.valueOf(MAX_PRICE_PER_KM)) > 0) {
                throw BusinessException.badRequest("LOGYX-ORG-014", "El precio mínimo por km va de S/ 0.01 a S/ "
                        + MAX_PRICE_PER_KM + ".", "minPricePerKm");
            }
            price = minPricePerKm.setScale(2, RoundingMode.HALF_UP);
        }
        java.time.LocalTime from = null;
        java.time.LocalTime to = null;
        Integer terms = null;
        if (pyme) {
            if ((pickupFrom == null) != (pickupTo == null)) {
                throw BusinessException.badRequest("LOGYX-ORG-012", "Indica el inicio y el fin del horario de recojo.",
                        pickupFrom == null ? "defaultPickupFrom" : "defaultPickupTo");
            }
            if (pickupFrom != null && !pickupFrom.isBefore(pickupTo)) {
                throw BusinessException.badRequest("LOGYX-ORG-012", "El horario de recojo debe terminar después de empezar.",
                        "defaultPickupTo");
            }
            from = pickupFrom;
            to = pickupTo;
            if (paymentTermsDays != null && (paymentTermsDays < 0 || paymentTermsDays > MAX_PAYMENT_TERMS_DAYS)) {
                throw BusinessException.badRequest("LOGYX-ORG-013", "El plazo de pago va de 0 a " + MAX_PAYMENT_TERMS_DAYS
                        + " días.", "paymentTermsDays");
            }
            terms = paymentTermsDays;
        }
        return new OrganizationPreferences(orgId, dedupe(cities), rs, dedupe(cargoTypes),
                !carrier || routeAlerts == null || routeAlerts, price, from, to, terms, by, now);
    }

    private static <T> List<T> dedupe(List<T> l) {
        return l == null ? List.of() : List.copyOf(new LinkedHashSet<>(l));
    }
}
