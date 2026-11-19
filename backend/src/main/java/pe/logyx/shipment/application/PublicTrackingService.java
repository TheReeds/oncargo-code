package pe.logyx.shipment.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.catalog.domain.City;
import pe.logyx.routing.application.RoutePlanner;
import pe.logyx.routing.domain.model.LatLng;
import pe.logyx.shared.tenancy.SystemTx;
import pe.logyx.shipment.application.port.out.RequestInfoProvider;
import pe.logyx.shipment.application.port.out.ShareLinkRepository;
import pe.logyx.shipment.application.port.out.ShipmentRepository;
import pe.logyx.shipment.application.port.out.ShipmentStopRepository;
import pe.logyx.shipment.domain.EtaPolicy;
import pe.logyx.shipment.domain.ShareLinkPolicy;
import pe.logyx.shipment.domain.model.ShareLink;
import pe.logyx.shipment.domain.model.ShareToken;
import pe.logyx.shipment.domain.model.Shipment;
import pe.logyx.shipment.domain.model.ShipmentStop;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resuelve el enlace público de seguimiento (sin JWT). Único uso de {@code SystemTx} del envío público (ADR-0006,
 * ADR-0011): no hay usuario ni organización, así que la RLS no dejaría leer nada. Se busca por el hash del token, se
 * comprueba que siga vigente y solo se arma {@link PublicTracking} (lista blanca). Revocado, vencido, inexistente o mal
 * formado → vacío, sin distinguir (el recurso responde siempre lo mismo).
 */
@ApplicationScoped
public class PublicTrackingService {

    @Inject SystemTx system;
    @Inject ShareLinkRepository links;
    @Inject ShipmentRepository shipments;
    @Inject ShipmentStopRepository stops;
    @Inject RequestInfoProvider requests;
    @Inject RoutePlanner routes;

    public Optional<PublicTracking> resolve(String token) {
        if (!ShareToken.isWellFormed(token)) return Optional.empty();
        String hash = ShareToken.hash(token);
        // SystemTx: lectura sin usuario acotada a este enlace (por hash) y a la lista blanca de PublicTracking.
        return system.call(() -> links.findByHash(hash).flatMap(link -> view(link, OffsetDateTime.now())));
    }

    private Optional<PublicTracking> view(ShareLink link, OffsetDateTime now) {
        Shipment s = shipments.findById(link.shipmentId()).orElse(null);
        if (s == null || ShareLinkPolicy.status(link, s, now) != ShareLink.Status.ACTIVE) return Optional.empty();
        links.recordView(link.id(), now);
        List<ShipmentStop> all = stops.listByShipment(s.id()).stream()
                .sorted(Comparator.comparingInt(ShipmentStop::sequence)).toList();
        var info = requests.byIds(List.of(s.requestId())).get(s.requestId());
        List<City> cities = routeCities(all, info);
        List<LatLng> geometry = cities.size() >= 2 ? routes.geometry(cities) : List.of();
        var plan = cities.size() >= 2 ? routes.route(cities) : null;
        EtaPolicy.Window eta = eta(s, all, now);
        OffsetDateTime updated = Stream.concat(Stream.of(s.createdAt(), s.deliveredAt()),
                        all.stream().flatMap(x -> Stream.of(x.arrivedAt(), x.completedAt())))
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(s.createdAt());
        return Optional.of(new PublicTracking(info == null ? null : info.code(), s.status(),
                info == null ? null : info.originCity(), info == null ? null : info.destinationCity(),
                info == null ? null : info.pickupDate(), s.deliveredAt(),
                all.stream().map(x -> new PublicTracking.Stop(x.sequence(), x.type(), x.city(), x.status(),
                        x.arrivedAt(), x.completedAt())).toList(),
                eta == null ? null : eta.from(), eta == null ? null : eta.to(), geometry,
                plan == null ? 0 : plan.distanceKm(), plan == null ? 0 : plan.durationMin(),
                geometry.isEmpty() ? null : routes.attribution(), updated, ShareLinkPolicy.effectiveExpiry(link, s)));
    }

    /** Ciudades del recorrido en orden, sin repetir consecutivas (si no hay paradas, origen → destino). */
    private static List<City> routeCities(List<ShipmentStop> all, RequestInfoProvider.RequestInfo info) {
        List<City> raw = all.size() >= 2 ? all.stream().map(ShipmentStop::city).toList()
                : info == null ? List.of() : List.of(info.originCity(), info.destinationCity());
        List<City> out = new ArrayList<>();
        for (City c : raw) if (out.isEmpty() || out.getLast() != c) out.add(c);
        return out;
    }

    /** En tránsito: desde el último hito completado, el manejo que falta hasta la última parada. */
    private EtaPolicy.Window eta(Shipment s, List<ShipmentStop> all, OffsetDateTime now) {
        if (s.status() != Shipment.Status.IN_TRANSIT) return null;
        ShipmentStop next = all.stream().filter(x -> !x.isCompleted()).findFirst().orElse(null);
        ShipmentStop last = all.stream().filter(ShipmentStop::isCompleted).reduce((a, b) -> b).orElse(null);
        if (next == null || last == null || next.status() == ShipmentStop.Status.ARRIVED) return null;
        List<City> remaining = new ArrayList<>();
        remaining.add(last.city());
        all.stream().filter(x -> x.sequence() > last.sequence()).map(ShipmentStop::city)
                .forEach(c -> { if (remaining.getLast() != c) remaining.add(c); });
        if (remaining.size() < 2) return null;
        return EtaPolicy.estimate(last.completedAt(), routes.route(remaining).durationMin(), now);
    }
}
