package pe.logyx.routing.infrastructure.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.application.port.out.RouteDataset;
import pe.logyx.routing.domain.PolylineCodec;
import pe.logyx.routing.domain.RoadNetwork;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.routing.domain.model.RoadLeg;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dataset precalculado de rutas entre las ciudades del catálogo ({@code routing/routes.json}, generado por
 * {@code scripts/routing/build_routes.py}; ADR-0008). Se carga una vez (≈ 220 KB): matriz dirigida de km/min y
 * polylines que se decodifican a demanda y quedan en caché. Las ciudades del JSON que ya no estén en {@link City}
 * se ignoran; las que falten caen en la estimación en línea recta.
 */
@ApplicationScoped
public class ClasspathRouteDataset implements RouteDataset {

    public static final String DEFAULT_RESOURCE = "routing/routes.json";

    private final Map<City, Map<City, RoadLeg>> legs = new EnumMap<>(City.class);
    private final Map<String, String> polylines = new HashMap<>();
    private final Map<String, List<LatLng>> decoded = new ConcurrentHashMap<>();
    private final Map<City, LatLng> datasetCoordinates = new EnumMap<>(City.class);
    private final int precision;
    private final String attribution;
    private final String engine;

    @Inject
    public ClasspathRouteDataset(@ConfigProperty(name = "logyx.routing.dataset", defaultValue = DEFAULT_RESOURCE) String resource,
                                 ObjectMapper mapper) {
        this(read(resource, mapper));
    }

    ClasspathRouteDataset(JsonNode root) {
        List<City> order = new ArrayList<>();
        for (JsonNode c : root.path("cities")) {
            City city = City.parse(c.path("code").asText()).orElse(null);
            order.add(city);
            if (city != null) datasetCoordinates.put(city, new LatLng(c.path("lat").asDouble(), c.path("lng").asDouble()));
        }
        JsonNode km = root.path("distanceKm");
        JsonNode min = root.path("durationMin");
        for (int i = 0; i < order.size(); i++) {
            City from = order.get(i);
            if (from == null) continue;
            Map<City, RoadLeg> row = new EnumMap<>(City.class);
            for (int j = 0; j < order.size(); j++) {
                City to = order.get(j);
                if (to == null || to == from) continue;
                row.put(to, new RoadLeg(from, to, km.get(i).get(j).asDouble(), min.get(i).get(j).asDouble(), false));
            }
            legs.put(from, row);
        }
        root.path("geometries").properties().forEach(e -> polylines.put(e.getKey(), e.getValue().path("polyline").asText()));
        this.precision = root.path("polylinePrecision").asInt(5);
        this.attribution = root.path("attribution").asText("© OpenStreetMap contributors");
        this.engine = root.path("engine").asText("");
    }

    private static JsonNode read(String resource, ObjectMapper mapper) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = (cl != null ? cl : ClasspathRouteDataset.class.getClassLoader()).getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("No se encontró el dataset de rutas: " + resource);
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Dataset de rutas ilegible: " + resource, e);
        }
    }

    @Override
    public RoadNetwork network() {
        return (from, to) -> Optional.ofNullable(legs.getOrDefault(from, Map.of()).get(to));
    }

    @Override
    public Optional<List<LatLng>> geometry(City from, City to) {
        String direct = from.name() + "-" + to.name();
        if (polylines.containsKey(direct)) {
            return Optional.of(decoded.computeIfAbsent(direct, k -> PolylineCodec.decode(polylines.get(k), precision)));
        }
        String reverse = to.name() + "-" + from.name();
        if (polylines.containsKey(reverse)) {
            List<LatLng> pts = decoded.computeIfAbsent(reverse, k -> PolylineCodec.decode(polylines.get(k), precision));
            List<LatLng> copy = new ArrayList<>(pts);
            Collections.reverse(copy);
            return Optional.of(List.copyOf(copy));
        }
        return Optional.empty();
    }

    @Override
    public String attribution() {
        return attribution;
    }

    public String engine() {
        return engine;
    }

    /** Coordenadas con las que se generó el dataset (para detectar un dataset desactualizado respecto de {@link City}). */
    public Map<City, LatLng> datasetCoordinates() {
        return Collections.unmodifiableMap(datasetCoordinates);
    }
}
