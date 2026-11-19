package pe.logyx.identity.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.identity.application.port.out.OrganizationPreferencesRepository;
import pe.logyx.identity.domain.model.OrganizationPreferences;
import pe.logyx.identity.domain.model.OrganizationPreferences.InterestRoute;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** {@code organization_preferences} (V24) con SQL nativo (arreglos {@code text[]} y {@code jsonb}). */
@ApplicationScoped
public class SqlOrganizationPreferencesRepository implements OrganizationPreferencesRepository {

    private static final String COLUMNS = "p.organization_id, array_to_string(p.cities, ','), p.routes::text, "
            + "array_to_string(p.cargo_types, ','), p.route_alerts, p.min_price_per_km, p.default_pickup_from, "
            + "p.default_pickup_to, p.payment_terms_days, p.updated_by, p.updated_at";

    @Inject EntityManager em;
    @Inject ObjectMapper json;

    @Override
    @SuppressWarnings("unchecked")
    public Optional<OrganizationPreferences> find(UUID organizationId) {
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLUMNS + " FROM organization_preferences p "
                + "WHERE p.organization_id = ?1").setParameter(1, organizationId).getResultList();
        return rows.stream().findFirst().map(this::map);
    }

    @Override
    public void upsert(OrganizationPreferences p) {
        em.createNativeQuery("INSERT INTO organization_preferences (organization_id, cities, routes, cargo_types, route_alerts, "
                        + "min_price_per_km, default_pickup_from, default_pickup_to, payment_terms_days, updated_by, updated_at) "
                        + "VALUES (?1, string_to_array(?2, ','), CAST(?3 AS jsonb), string_to_array(?4, ','), ?5, ?6, "
                        + "CAST(?7 AS time), CAST(?8 AS time), CAST(?9 AS smallint), ?10, now()) "
                        + "ON CONFLICT (organization_id) DO UPDATE SET cities = EXCLUDED.cities, routes = EXCLUDED.routes, "
                        + "cargo_types = EXCLUDED.cargo_types, route_alerts = EXCLUDED.route_alerts, "
                        + "min_price_per_km = EXCLUDED.min_price_per_km, default_pickup_from = EXCLUDED.default_pickup_from, "
                        + "default_pickup_to = EXCLUDED.default_pickup_to, payment_terms_days = EXCLUDED.payment_terms_days, "
                        + "updated_by = EXCLUDED.updated_by, updated_at = now()")
                .setParameter(1, p.organizationId())
                .setParameter(2, p.cities().stream().map(Enum::name).collect(Collectors.joining(",")))
                .setParameter(3, routesJson(p.routes()))
                .setParameter(4, p.cargoTypes().stream().map(Enum::name).collect(Collectors.joining(",")))
                .setParameter(5, p.routeAlerts())
                .setParameter(6, p.minPricePerKm())
                .setParameter(7, p.defaultPickupFrom() == null ? null : p.defaultPickupFrom().toString())
                .setParameter(8, p.defaultPickupTo() == null ? null : p.defaultPickupTo().toString())
                .setParameter(9, p.paymentTermsDays() == null ? null : p.paymentTermsDays().toString())
                .setParameter(10, p.updatedBy())
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OrganizationPreferences> carriersWithRouteAlert(City origin, City destination) {
        String route = "[{\"origin\":\"" + origin.name() + "\",\"destination\":\"" + destination.name() + "\"}]";
        List<Object[]> rows = em.createNativeQuery("SELECT " + COLUMNS + " FROM organization_preferences p JOIN organizations o ON o.id = p.organization_id "
                        + "WHERE o.type = 'CARRIER' AND p.route_alerts AND p.routes @> CAST(?1 AS jsonb)")
                .setParameter(1, route).getResultList();
        return rows.stream().map(this::map).toList();
    }

    private OrganizationPreferences map(Object[] r) {
        return new OrganizationPreferences((UUID) r[0], parse((String) r[1], City::valueOf), routes((String) r[2]),
                parse((String) r[3], CargoType::valueOf), (Boolean) r[4], (BigDecimal) r[5], time(r[6]), time(r[7]),
                r[8] == null ? null : ((Number) r[8]).intValue(), (UUID) r[9], offset(r[10]));
    }

    private static <E extends Enum<E>> List<E> parse(String csv, Function<String, E> f) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(s -> {
            try {
                return f.apply(s.trim());
            } catch (IllegalArgumentException e) {
                return null;                                   // valor retirado del catálogo: se ignora
            }
        }).filter(Objects::nonNull).toList();
    }

    private List<InterestRoute> routes(String raw) {
        List<InterestRoute> out = new ArrayList<>();
        try {
            for (JsonNode n : json.readTree(raw == null ? "[]" : raw)) {
                var o = City.parse(n.path("origin").asText());
                var d = City.parse(n.path("destination").asText());
                if (o.isPresent() && d.isPresent()) out.add(new InterestRoute(o.get(), d.get()));
            }
        } catch (JsonProcessingException e) {
            return List.of();
        }
        return out;
    }

    private String routesJson(List<InterestRoute> routes) {
        try {
            return json.writeValueAsString(routes.stream().map(r -> Map.of("origin", r.origin().name(),
                    "destination", r.destination().name())).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalTime time(Object o) {
        if (o == null) return null;
        if (o instanceof LocalTime t) return t;
        if (o instanceof Time t) return t.toLocalTime();
        return LocalTime.parse(o.toString());
    }

    private static OffsetDateTime offset(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime t) return t;
        if (o instanceof java.time.Instant i) return i.atOffset(java.time.ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp t) return t.toInstant().atOffset(java.time.ZoneOffset.UTC);
        return OffsetDateTime.parse(o.toString());
    }
}
