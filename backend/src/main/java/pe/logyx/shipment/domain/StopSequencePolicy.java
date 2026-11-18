package pe.logyx.shipment.domain;

import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** RN08: las paradas se actualizan en orden; solo se actúa sobre la primera no completada. */
public final class StopSequencePolicy {

    private StopSequencePolicy() {}

    /**
     * La parada sobre la que se puede actuar, si es {@code stopId}; si ya estaba completada, LOGYX-TRK-007 (F49);
     * si no es la siguiente, LOGYX-TRK-001.
     */
    public static ShipmentStop requireNext(List<ShipmentStop> stops, UUID stopId) {
        stops.stream().filter(s -> s.id().equals(stopId) && s.isCompleted()).findFirst().ifPresent(s -> {
            throw BusinessException.conflict("LOGYX-TRK-007", "Esta parada ya está completada");   // F49
        });
        ShipmentStop next = stops.stream()
                .filter(s -> !s.isCompleted())
                .min(Comparator.comparingInt(ShipmentStop::sequence))
                .orElse(null);
        if (next == null || !next.id().equals(stopId)) {
            throw BusinessException.conflict("LOGYX-TRK-001", "Las paradas deben actualizarse en orden (RN08)");
        }
        return next;
    }

    public static boolean allCompleted(List<ShipmentStop> stops) {
        return stops.stream().allMatch(ShipmentStop::isCompleted);
    }
}
