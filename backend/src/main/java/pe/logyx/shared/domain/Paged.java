package pe.logyx.shared.domain;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados más el total sin paginar. */
public record Paged<T>(List<T> items, PageRequest request, long total) {

    /** Recorta una lista completa en memoria (colecciones pequeñas por organización). */
    public static <T> Paged<T> slice(List<T> all, PageRequest req) {
        int from = (int) Math.min((long) req.offset(), all.size());
        int to = Math.min(from + req.size(), all.size());
        return new Paged<>(all.subList(from, to), req, all.size());
    }

    public <R> Paged<R> map(Function<T, R> f) {
        return new Paged<>(items.stream().map(f).toList(), request, total);
    }
}
