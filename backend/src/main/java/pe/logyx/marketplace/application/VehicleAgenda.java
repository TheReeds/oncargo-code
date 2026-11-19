package pe.logyx.marketplace.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import pe.logyx.fleet.application.FleetService;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.marketplace.application.port.out.BidRepository;
import pe.logyx.marketplace.application.port.out.ShipmentRequestRepository;
import pe.logyx.marketplace.domain.BidEligibilityPolicy;
import pe.logyx.marketplace.domain.model.Bid;
import pe.logyx.marketplace.domain.model.ShipmentRequest;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shipment.application.ResourceScheduler;
import pe.logyx.shipment.domain.ResourceSchedulePolicy;
import pe.logyx.shipment.domain.ResourceSchedulePolicy.Window;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RN02 por fecha de recojo (decisión del dueño, 2026-11-17): la agenda de un vehículo son sus <b>envíos activos</b> y
 * sus <b>ofertas pendientes</b>, cada uno con la ventana {@code recojo + ⌈km/500⌉ días} de {@link ResourceSchedulePolicy}.
 * Un vehículo puede ofertar a una carga si su agenda no se solapa con la ventana de esa carga:
 * <ul>
 *   <li>solape con un <b>envío activo</b> → bloquea (LOGYX-CAP-005, como siempre);</li>
 *   <li>solape con <b>ofertas pendientes</b> → se permite y se avisa (LOGYX-CAP-006); con
 *       {@code logyx.bids.max-overlapping-per-vehicle} ofertas solapadas (3) → LOGYX-CAP-007;</li>
 *   <li>mantenimiento o archivo → bloquea (LOGYX-CAP-002); "en ruta" no bloquea por sí solo.</li>
 * </ul>
 * La validación dura sigue al aceptar ({@code AcceptBidUseCase}, CAP-005). Esta clase es la única fuente de la regla:
 * la usan {@link PlaceBidUseCase} y el endpoint de elegibilidad.
 */
@ApplicationScoped
public class VehicleAgenda {

    public enum Kind { SHIPMENT, BID }

    /** Un compromiso de la agenda: envío activo u oferta pendiente. {@code label} = ruta o código, sin datos de otras partes. */
    public record Slot(Kind kind, UUID refId, String label, LocalDate from, LocalDate to) {}

    /** Resultado de evaluar un vehículo contra una carga. {@code blocker} nulo = puede ofertar. */
    public record Assessment(Vehicle vehicle, Window window, BusinessException blocker, List<Slot> shipmentConflicts,
                             List<Slot> bidOverlaps, List<BidWarning> warnings, LocalDate freeFrom) {
        public boolean eligible() { return blocker == null; }
    }

    @Inject FleetService fleet;
    @Inject ResourceScheduler schedule;
    @Inject BidRepository bids;
    @Inject ShipmentRequestRepository requests;
    @ConfigProperty(name = "logyx.bids.max-overlapping-per-vehicle", defaultValue = "3")
    int maxOverlapping;

    public int maxOverlapping() { return maxOverlapping; }

    /** Evalúa a todos los vehículos activos del transportista actual contra la solicitud. */
    @Transactional
    public List<Assessment> assessFleet(ShipmentRequest r) {
        return fleet.vehicles().stream().map(v -> assess(v, r, null)).toList();
    }

    /** Ventana de ocupación de la solicitud (recojo + ⌈km/500⌉ días). */
    public Window windowOf(ShipmentRequest r) {
        return schedule.window(r.cargo().pickupDate(), r.pricing().distanceKm());
    }

    /**
     * Evalúa un vehículo (propio) contra la solicitud sin lanzar: el primer impedimento queda en {@code blocker}
     * y los avisos en {@code warnings}. {@code loadGroupId}: la oferta es parte de un plan Smart Load o viaje.
     */
    @Transactional
    public Assessment assess(Vehicle v, ShipmentRequest r, UUID loadGroupId) {
        Window w = windowOf(r);
        BusinessException blocker = null;
        try {
            BidEligibilityPolicy.ensureVehicleEligibleOnDate(v, r.cargo());                       // RN02: mantenimiento, kg, tipo, m³
        } catch (BusinessException e) {
            blocker = e;
        }
        List<Slot> shipmentSlots = shipmentSlots(v);
        List<Slot> shipmentConflicts = shipmentSlots.stream().filter(s -> overlaps(s, w)).toList();
        List<Slot> bidOverlaps = bidSlots(v.id(), r.id(), loadGroupId).stream().filter(s -> overlaps(s, w)).toList();
        if (blocker == null && !shipmentConflicts.isEmpty()) {
            try {
                ResourceSchedulePolicy.ensureVehicleFree(schedule.activeOfVehicle(v.id()), v.id(), w, loadGroupId, null);
            } catch (BusinessException e) {
                blocker = e;                                                                         // CAP-005
            }
        }
        List<BidWarning> warnings = new ArrayList<>();
        if (blocker == null && !bidOverlaps.isEmpty()) {
            try {
                BidEligibilityPolicy.ensureBelowOverlapLimit(v.plate(), bidOverlaps.size(), maxOverlapping);
                warnings.add(new BidWarning("LOGYX-CAP-006",
                        BidEligibilityPolicy.overlapWarning(v.plate(), bidOverlaps.size())));
            } catch (BusinessException e) {
                blocker = e;                                                                         // CAP-007
            }
        }
        LocalDate freeFrom = shipmentConflicts.stream().map(Slot::to).max(Comparator.naturalOrder())
                .map(d -> d.plusDays(1)).orElse(null);
        return new Assessment(v, w, blocker, shipmentConflicts, bidOverlaps, List.copyOf(warnings), freeFrom);
    }

    /**
     * Ofertas pendientes del vehículo cuya ventana se solapa con {@code window}, sin contar la de {@code excludeRequestId}
     * ni las del mismo plan ({@code loadGroupId}). Bajo el RLS del transportista dueño.
     */
    @Transactional
    public List<Slot> overlappingBids(UUID vehicleId, Window window, UUID excludeRequestId, UUID loadGroupId) {
        return bidSlots(vehicleId, excludeRequestId, loadGroupId).stream().filter(s -> overlaps(s, window)).toList();
    }

    private List<Slot> shipmentSlots(Vehicle v) {
        return schedule.activeOfVehicle(v.id()).stream()
                .map(c -> new Slot(Kind.SHIPMENT, c.shipmentId(), "Envío en curso", c.window().from(), c.window().to()))
                .toList();
    }

    private List<Slot> bidSlots(UUID vehicleId, UUID excludeRequestId, UUID loadGroupId) {
        List<Slot> out = new ArrayList<>();
        for (Bid b : bids.listPendingByVehicle(vehicleId)) {
            if (b.requestId().equals(excludeRequestId)) continue;
            if (loadGroupId != null && loadGroupId.equals(b.loadGroupId())) continue;
            requests.findById(b.requestId()).filter(ShipmentRequest::isPublished).ifPresent(req -> {
                Window w = windowOf(req);
                out.add(new Slot(Kind.BID, b.id(), req.routeLabel(), w.from(), w.to()));
            });
        }
        return out;
    }

    private static boolean overlaps(Slot s, Window w) {
        return new Window(s.from(), s.to()).overlaps(w);
    }

    /** Lanza el impedimento si lo hay. */
    public static void requireEligible(Assessment a) {
        if (a.blocker() != null) throw a.blocker();
        Objects.requireNonNull(a.vehicle());
    }
}
