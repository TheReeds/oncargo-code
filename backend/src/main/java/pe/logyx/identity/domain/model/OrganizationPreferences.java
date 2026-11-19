package pe.logyx.identity.domain.model;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Preferencias de la organización (V24, roadmap 2.2; {@code organization_preferences}). Las escribe el ADMIN; las leen
 * todos los de la organización y el operador.
 * <ul>
 *   <li>Comunes: ciudades, rutas y tipos de carga de interés.</li>
 *   <li>Transportista: {@code routeAlerts} (alertas implícitas por sus rutas, 2.3) y {@code minPricePerKm}.</li>
 *   <li>PYME: horario de recojo por defecto y {@code paymentTermsDays} (plazo de pago a sus transportistas, lo lee
 *       {@code settlement}, 2.12).</li>
 * </ul>
 * Lo que no aplica al tipo de organización queda nulo (o {@code routeAlerts = true}, su valor por omisión).
 */
public record OrganizationPreferences(UUID organizationId, List<City> cities, List<InterestRoute> routes,
                                      List<CargoType> cargoTypes, boolean routeAlerts, BigDecimal minPricePerKm,
                                      LocalTime defaultPickupFrom, LocalTime defaultPickupTo, Integer paymentTermsDays,
                                      UUID updatedBy, OffsetDateTime updatedAt) {

    /** Ruta de interés origen → destino (ciudades del catálogo, distintas). */
    public record InterestRoute(City origin, City destination) {
        public InterestRoute {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(destination);
        }
    }

    public OrganizationPreferences {
        Objects.requireNonNull(organizationId);
        cities = List.copyOf(cities);
        routes = List.copyOf(routes);
        cargoTypes = List.copyOf(cargoTypes);
    }

    /** Sin fila: nada declarado, alertas por rutas activadas (no hay rutas, así que no avisan). */
    public static OrganizationPreferences empty(UUID organizationId) {
        return new OrganizationPreferences(organizationId, List.of(), List.of(), List.of(), true, null, null, null, null,
                null, null);
    }
}
