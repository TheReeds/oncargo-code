package pe.logyx.fleet.domain;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Orden de los conductores sugeridos para un envío (2.13), Java puro. Primero los que se pueden asignar (cuenta activa y
 * libres en las fechas), luego por cercanía al recojo, luego por carga de trabajo (días ocupados alrededor de la
 * ventana) y por nombre. La cercanía se agrupa de a 100 km para que una diferencia pequeña no pese más que la carga.
 */
public final class DriverRanking {

    public static final int PROXIMITY_BUCKET_KM = 100;

    /** {@code distanceKm} nulo = no hay dato (conductor sin envíos previos). */
    public record Candidate(UUID driverId, String name, boolean active, boolean free, int workloadDays,
                            Double distanceKm) {
        public boolean assignable() { return active && free; }
    }

    private DriverRanking() {}

    public static List<Candidate> rank(List<Candidate> candidates) {
        Comparator<Candidate> order = Comparator
                .comparing((Candidate c) -> !c.assignable())
                .thenComparingInt(DriverRanking::proximityBucket)
                .thenComparingInt(Candidate::workloadDays)
                .thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER);
        return candidates.stream().sorted(order).toList();
    }

    /** 0 = mismo lugar (menos de 100 km); sin dato queda al final de su grupo. */
    static int proximityBucket(Candidate c) {
        return c.distanceKm() == null ? Integer.MAX_VALUE : (int) (c.distanceKm() / PROXIMITY_BUCKET_KM);
    }
}
