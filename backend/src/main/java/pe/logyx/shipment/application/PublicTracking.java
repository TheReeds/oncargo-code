package pe.logyx.shipment.application;

import pe.logyx.catalog.domain.City;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lo único que ve el cliente final por el enlace público (roadmap 1.6, ADR-0011): lista blanca. Sin precio, sin
 * contactos, sin direcciones, sin placa, sin conductor, sin nombres ni ids de organizaciones, sin ids internos.
 * Si un campo nuevo no está aquí, no sale.
 */
public record PublicTracking(String code, Shipment.Status status, City originCity, City destinationCity,
                             LocalDate pickupDate, OffsetDateTime deliveredAt, List<Stop> stops,
                             OffsetDateTime etaFrom, OffsetDateTime etaTo, List<LatLng> route, double distanceKm,
                             double durationMin, String routeAttribution, OffsetDateTime updatedAt,
                             OffsetDateTime linkExpiresAt) {

    /** Hito de una parada: tipo, ciudad, estado y horas (las del evento). */
    public record Stop(int sequence, ShipmentStop.Type type, City city, ShipmentStop.Status status,
                       OffsetDateTime arrivedAt, OffsetDateTime completedAt) {}
}
