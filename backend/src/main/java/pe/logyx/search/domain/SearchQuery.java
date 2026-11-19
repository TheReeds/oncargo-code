package pe.logyx.search.domain;

import pe.logyx.catalog.domain.City;
import pe.logyx.marketplace.domain.RequestCode;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.LikePatterns;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consulta de la búsqueda global ya interpretada: texto (mínimo 2 caracteres, comodines escapados), código si parece
 * uno ({@code oc 7k2qx} → {@code OC-7K2QX}), ciudades nombradas en el texto ("lima juliaca" = la ruta) y los tipos
 * pedidos. Nunca arma SQL: el adaptador usa solo estos valores como parámetros.
 */
public record SearchQuery(String text, String code, Set<City> cities, Set<SearchHitType> types) {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 80;
    /** Máximo de resultados por tipo. */
    public static final int LIMIT_PER_TYPE = 8;

    public static SearchQuery parse(String q, String types) {
        String text = q == null ? "" : q.trim().replaceAll("\\s+", " ");
        if (text.length() < MIN_LENGTH) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Escribe al menos " + MIN_LENGTH + " caracteres", "q");
        }
        if (text.length() > MAX_LENGTH) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "La búsqueda admite hasta " + MAX_LENGTH + " caracteres", "q");
        }
        Set<City> cities = new LinkedHashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split(" ")) {
            if (token.length() < 3) continue;
            for (City c : City.values()) {
                if (c.label.toLowerCase(Locale.ROOT).startsWith(token)) cities.add(c);
            }
        }
        return new SearchQuery(text, RequestCode.parse(text), cities, types(types));
    }

    /** Patrón {@code %texto%} seguro para LIKE (en minúsculas, con {@code escape '\'}). */
    public String contains() {
        return LikePatterns.contains(text);
    }

    public String prefix() {
        return LikePatterns.prefix(text);
    }

    /** "lima juliaca": dos o más ciudades → la ruta debe empezar y terminar en ellas; una sola → origen o destino. */
    public boolean isRoute() {
        return cities.size() >= 2;
    }

    public boolean wants(SearchHitType type) {
        return types.contains(type);
    }

    public List<String> cityNames() {
        return cities.stream().map(Enum::name).toList();
    }

    private static Set<SearchHitType> types(String csv) {
        if (csv == null || csv.isBlank()) return EnumSet.allOf(SearchHitType.class);
        Set<SearchHitType> out = EnumSet.noneOf(SearchHitType.class);
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            out.add(Arrays.stream(SearchHitType.values()).filter(t -> t.name().equalsIgnoreCase(p)).findFirst()
                    .orElseThrow(() -> BusinessException.badRequest("LOGYX-VAL-001",
                            "Tipo inválido: " + p + " (use REQUEST, SHIPMENT o COUNTERPART)", "types")));
        }
        return out.isEmpty() ? EnumSet.allOf(SearchHitType.class) : out;
    }
}
