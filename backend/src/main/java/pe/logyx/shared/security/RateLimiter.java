package pe.logyx.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos por clave con ventana fija, en memoria (una instancia del backend). Suficiente para frenar
 * fuerza bruta y registros masivos en login/registro; con varias réplicas, el límite efectivo se multiplica por
 * el número de réplicas (pasar a Redis/BD si se escala horizontalmente, ver docs/security.md).
 */
public class RateLimiter {

    private record Window(long start, int count) {}

    private static final int MAX_KEYS = 100_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Cuenta un intento; devuelve false si con él se supera {@code max} en la ventana. */
    public boolean tryAcquire(String key, int max, Duration window) {
        return record(key, window) <= max;
    }

    /** true si la clave ya alcanzó {@code max} en la ventana actual (no cuenta un intento). */
    public boolean exhausted(String key, int max, Duration window) {
        Window w = windows.get(key);
        return w != null && !expired(w, window) && w.count() >= max;
    }

    /** Suma un intento a la clave y devuelve el total en la ventana actual. */
    public int record(String key, Duration window) {
        if (windows.size() > MAX_KEYS) evictExpired(window);
        long now = clock.millis();
        return windows.compute(key, (k, w) ->
                w == null || expired(w, window) ? new Window(now, 1) : new Window(w.start(), w.count() + 1)).count();
    }

    public void reset(String key) {
        windows.remove(key);
    }

    /** Segundos hasta que la ventana de la clave se reinicia (para {@code Retry-After}). */
    public long retryAfterSeconds(String key, Duration window) {
        Window w = windows.get(key);
        if (w == null) return 0;
        long left = w.start() + window.toMillis() - clock.millis();
        return Math.max(1, (left + 999) / 1000);
    }

    private boolean expired(Window w, Duration window) {
        return clock.millis() - w.start() >= window.toMillis();
    }

    private void evictExpired(Duration window) {
        windows.entrySet().removeIf(e -> expired(e.getValue(), window));
    }
}
