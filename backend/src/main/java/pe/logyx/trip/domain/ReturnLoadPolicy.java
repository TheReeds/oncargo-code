package pe.logyx.trip.domain;

import pe.logyx.catalog.domain.CargoType;
import pe.logyx.catalog.domain.City;
import pe.logyx.fleet.domain.model.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Cargas de retorno (RF61–RF63). Para cada envío del transportista en curso o recién entregado ("tramo"), busca
 * solicitudes publicadas que salgan de la ciudad de destino del tramo (o de una cercana, ver {@link CityProximityPolicy}),
 * con fecha de recojo compatible y que quepan en el vehículo del tramo.
 *
 * <p>Fecha compatible: {@code disponibleDesde ≤ recojo ≤ disponibleDesde + 7 días}. {@code disponibleDesde} es la fecha de
 * entrega (envío entregado, solo si fue hace ≤ 3 días) o hoy (en tránsito: sugerencia anticipada).
 * <p>Puntaje 0–100: {@code 100 − 0.6·km − 4·díasDeEspera}, acotado. Si una solicitud encaja con varios tramos se queda con
 * el de mejor puntaje. Orden: puntaje desc, recojo asc, id.
 */
public final class ReturnLoadPolicy {

    public static final int WINDOW_DAYS = 7;
    public static final int DELIVERED_RECENCY_DAYS = 3;
    public static final int MAX_RESULTS = 20;

    private ReturnLoadPolicy() {}

    /** Envío del transportista que deja el vehículo libre en {@code destination}. */
    public record Leg(UUID shipmentId, UUID vehicleId, String vehiclePlate, Vehicle.Type vehicleType,
                      BigDecimal vehicleCapacityKg, City destination, boolean delivered, LocalDate availableFrom) {}

    /** Solicitud publicada candidata a retorno. */
    public record Candidate(UUID requestId, City origin, City destination, BigDecimal weightKg, LocalDate pickupDate,
                            CargoType cargoType) {}

    public record Match(Leg leg, Candidate candidate, double distanceKm, int waitDays, int score) {}

    public static List<Match> match(List<Leg> legs, List<Candidate> candidates, LocalDate today) {
        Map<UUID, Match> best = new HashMap<>();
        for (Leg leg : legs) {
            if (!relevant(leg, today)) continue;
            for (Candidate c : candidates) {
                Match m = evaluate(leg, c);
                if (m == null) continue;
                best.merge(c.requestId(), m, (a, b) -> b.score() > a.score() ? b : a);
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingInt(Match::score).reversed()
                        .thenComparing(m -> m.candidate().pickupDate())
                        .thenComparing(m -> m.candidate().requestId()))
                .limit(MAX_RESULTS)
                .toList();
    }

    /** Los entregados hace más de {@value #DELIVERED_RECENCY_DAYS} días ya no generan retorno. */
    static boolean relevant(Leg leg, LocalDate today) {
        return !leg.delivered() || !leg.availableFrom().isBefore(today.minusDays(DELIVERED_RECENCY_DAYS));
    }

    static Match evaluate(Leg leg, Candidate c) {
        if (c.destination() == leg.destination()) return null;                       // no es un retorno
        double km = CityProximityPolicy.distanceKm(leg.destination(), c.origin());
        if (km > CityProximityPolicy.RETURN_RADIUS_KM) return null;
        if (c.weightKg().compareTo(leg.vehicleCapacityKg()) > 0) return null;       // no cabe
        if (!VehicleCargoRules.canCarry(leg.vehicleType(), c.cargoType())) return null;
        long wait = ChronoUnit.DAYS.between(leg.availableFrom(), c.pickupDate());
        if (wait < 0 || wait > WINDOW_DAYS) return null;
        int score = (int) Math.max(0, Math.min(100, Math.round(100 - 0.6 * km - 4.0 * wait)));
        return new Match(leg, c, Math.round(km * 10) / 10.0, (int) wait, score);
    }
}
