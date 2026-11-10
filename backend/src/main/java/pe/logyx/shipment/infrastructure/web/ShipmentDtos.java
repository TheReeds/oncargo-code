package pe.logyx.shipment.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import pe.logyx.catalog.domain.City;
import pe.logyx.shipment.application.ShipmentSummary;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record AssignDriver(@NotNull UUID driverId) {}

    public record StopView(UUID id, int sequence, ShipmentStop.Type type, City city, String address,
                           ShipmentStop.Status status, OffsetDateTime arrivedAt, OffsetDateTime completedAt) {
        static StopView of(ShipmentStop s) {
            return new StopView(s.id(), s.sequence(), s.type(), s.city(), s.address(), s.status(),
                    s.arrivedAt(), s.completedAt());
        }
    }

    public record ShipmentView(UUID id, UUID requestId, String requesterName, String carrierName,
                               City originCity, City destinationCity, String cargoDescription,
                               BigDecimal weightKg, BigDecimal agreedPrice, String vehiclePlate,
                               UUID driverId, String driverName, Shipment.Status status,
                               OffsetDateTime createdAt, OffsetDateTime deliveredAt,
                               List<StopView> stops) {
        static ShipmentView of(ShipmentSummary sum) {
            Shipment s = sum.shipment();
            var r = sum.request();
            return new ShipmentView(s.id(), s.requestId(), sum.requesterName(), sum.carrierName(),
                    r.originCity(), r.destinationCity(), r.cargoDescription(), r.weightKg(), s.agreedPrice(),
                    s.vehiclePlate(), s.driverId(), s.driverName(), s.status(), s.createdAt(), s.deliveredAt(),
                    sum.stops().stream().map(StopView::of).toList());
        }

        static List<ShipmentView> of(List<ShipmentSummary> list) {
            return list.stream().map(ShipmentView::of).toList();
        }
    }
}
