package pe.logyx.shared.domain;

import java.util.List;
import java.util.function.Function;

/**
 * Página de un listado por cursor (p. ej. historial de chat): {@code nextCursor} es el valor a enviar como
 * {@code before} para pedir la página siguiente, o {@code null} si no hay más.
 */
public record Slice<T>(List<T> items, int size, String nextCursor) {

    public <R> Slice<R> map(Function<T, R> f) {
        return new Slice<>(items.stream().map(f).toList(), size, nextCursor);
    }
}
