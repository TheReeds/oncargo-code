package pe.logyx.fleet.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.fleet.application.port.out.DriverRepository;
import pe.logyx.fleet.application.port.out.FleetDocumentRepository;
import pe.logyx.fleet.application.port.out.FleetPlanning;
import pe.logyx.fleet.application.port.out.FleetPlanning.Slot;
import pe.logyx.fleet.application.port.out.VehicleRepository;
import pe.logyx.fleet.domain.DriverRanking;
import pe.logyx.fleet.domain.model.Driver;
import pe.logyx.fleet.domain.model.FleetDocument;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Agenda de flota (calendario por vehículo y conductor) y asignación sugerida (roadmap 2.13). Solo lectura, bajo el RLS
 * del transportista. Reutiliza {@code ResourceSchedulePolicy} (ventanas) y {@code VehicleAgenda} (elegibilidad) por el
 * puerto {@link FleetPlanning}.
 */
@ApplicationScoped
public class FleetPlanningService {

    /** Rango máximo del calendario (7 o 14 días). */
    public static final int MAX_DAYS = 14;
    /** Alrededor de la ventana se mide la carga de trabajo de un conductor. */
    static final int WORKLOAD_MARGIN_DAYS = 7;

    /** Un día marcado en la agenda: vencimiento de un documento ({@code label} dice cuál). */
    public record Expiry(String label, LocalDate on, boolean expired) {}

    /** Una fila del calendario: un vehículo o un conductor con sus compromisos dentro del rango. */
    public record Row(UUID id, String label, String detail, boolean blocked, String blockedReason, List<Slot> slots,
                      List<Expiry> expiries) {}

    public record Schedule(LocalDate from, LocalDate to, List<Row> vehicles, List<Row> drivers) {}

    public record DriverOption(UUID driverId, String name, boolean active, boolean free, boolean assignable,
                               int workloadDays, Double distanceKm, boolean current) {}

    public record VehicleOption(UUID vehicleId, String plate, Vehicle.Type type, boolean eligible, String blockerCode,
                                String blockerMessage, boolean current) {}

    public record Suggestions(FleetPlanning.ShipmentPlan shipment, List<DriverOption> drivers,
                              List<VehicleOption> vehicles) {}

    @Inject VehicleRepository vehicles;
    @Inject DriverRepository drivers;
    @Inject FleetDocumentRepository documents;
    @Inject FleetPlanning planning;
    @Inject UserAccounts accounts;
    @Inject TenantContext tenant;

    @Transactional
    public Schedule schedule(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw BusinessException.badRequest("LOGYX-FLT-011",
                    "Elige un rango de 1 a " + MAX_DAYS + " días (from y to, inclusive)", "to");
        }
        List<Vehicle> fleet = vehicles.listByOrganization(tenant.orgId());
        List<Driver> crew = drivers.listByOrganization(tenant.orgId());
        Map<UUID, Profile> profiles = accounts.byIds(crew.stream().map(Driver::profileId).toList());
        List<Slot> slots = planning.slots(fleet.stream().map(Vehicle::id).toList()).stream()
                .filter(s -> !s.from().isAfter(to) && !s.to().isBefore(from)).toList();
        LocalDate today = FleetDocumentService.today();
        List<FleetDocument> docs = new ArrayList<>(documents.currentOfVehicles(fleet.stream().map(Vehicle::id).toList()));
        docs.addAll(documents.currentOfDrivers(crew.stream().map(Driver::id).toList()));

        List<Row> vehicleRows = fleet.stream().map(v -> new Row(v.id(), v.plate(),
                pe.logyx.fleet.domain.VehicleCargoCompatibility.label(v.type()),
                v.status() == Vehicle.Status.MAINTENANCE, v.status() == Vehicle.Status.MAINTENANCE ? "En mantenimiento" : null,
                slots.stream().filter(s -> v.id().equals(s.vehicleId())).sorted(byStart()).toList(),
                expiries(docs, d -> v.id().equals(d.vehicleId()), from, to, today))).toList();
        List<Row> driverRows = crew.stream().map(d -> {
            Profile p = profiles.get(d.profileId());
            boolean active = p != null && p.active();
            return new Row(d.id(), p == null ? d.licenseNumber() : p.fullName(),
                    d.licenseCategory() == null ? d.licenseNumber() : "Licencia " + d.licenseCategory(),
                    !active, active ? null : "Cuenta desactivada",
                    slots.stream().filter(s -> s.kind() == FleetPlanning.Kind.SHIPMENT && d.id().equals(s.driverId()))
                            .sorted(byStart()).toList(),
                    expiries(docs, x -> d.id().equals(x.driverId()), from, to, today));
        }).toList();
        return new Schedule(from, to, vehicleRows, driverRows);
    }

    /**
     * Conductores y vehículos para un envío de mi organización: «Conductores libres en esas fechas» primero, ordenados
     * por cercanía al recojo y carga de trabajo ({@link DriverRanking}); y los vehículos con su elegibilidad.
     */
    @Transactional
    public Suggestions suggest(UUID shipmentId) {
        var plan = planning.shipmentPlan(shipmentId).orElseThrow(() -> BusinessException.notFound("Envío"));
        List<Vehicle> fleet = vehicles.listByOrganization(tenant.orgId());
        List<Driver> crew = drivers.listByOrganization(tenant.orgId());
        Map<UUID, Profile> profiles = accounts.byIds(crew.stream().map(Driver::profileId).toList());
        List<Slot> slots = planning.slots(fleet.stream().map(Vehicle::id).toList());

        List<DriverRanking.Candidate> candidates = crew.stream().map(d -> {
            Profile p = profiles.get(d.profileId());
            List<Slot> mine = slots.stream().filter(s -> s.kind() == FleetPlanning.Kind.SHIPMENT
                    && d.id().equals(s.driverId()) && !s.refId().equals(shipmentId)).toList();
            boolean free = d.id().equals(plan.driverId())
                    || mine.stream().noneMatch(s -> !s.from().isAfter(plan.to()) && !s.to().isBefore(plan.from()));
            return new DriverRanking.Candidate(d.id(), p == null ? d.licenseNumber() : p.fullName(),
                    p != null && p.active(), free, workload(mine, plan.from(), plan.to()), distanceTo(mine, plan));
        }).toList();
        List<DriverOption> driverOptions = DriverRanking.rank(candidates).stream().map(c -> new DriverOption(
                c.driverId(), c.name(), c.active(), c.free(), c.assignable(), c.workloadDays(), c.distanceKm(),
                c.driverId().equals(plan.driverId()))).toList();

        List<VehicleOption> vehicleOptions = fleet.stream().map(v -> {
            boolean current = v.id().equals(plan.vehicleId());
            var fit = current ? new FleetPlanning.VehicleFit(true, null, null) : planning.vehicleFit(shipmentId, v.id());
            return new VehicleOption(v.id(), v.plate(), v.type(), fit.eligible(), fit.code(), fit.message(), current);
        }).sorted(Comparator.comparing((VehicleOption o) -> !o.eligible()).thenComparing(VehicleOption::plate)).toList();
        return new Suggestions(plan, driverOptions, vehicleOptions);
    }

    private Double distanceTo(List<Slot> driverSlots, FleetPlanning.ShipmentPlan plan) {
        return driverSlots.stream().filter(s -> s.destination() != null && !s.to().isAfter(plan.from()))
                .max(Comparator.comparing(Slot::to)).map(s -> planning.distanceKm(s.destination(), plan.origin()))
                .orElse(null);
    }

    /** Días de la ventana ampliada (± margen) en que el conductor ya trabaja. */
    static int workload(List<Slot> slots, LocalDate from, LocalDate to) {
        LocalDate lo = from.minusDays(WORKLOAD_MARGIN_DAYS), hi = to.plusDays(WORKLOAD_MARGIN_DAYS);
        Set<LocalDate> busy = new HashSet<>();
        for (Slot s : slots) {
            for (LocalDate d = s.from().isBefore(lo) ? lo : s.from(); !d.isAfter(s.to()) && !d.isAfter(hi); d = d.plusDays(1)) {
                busy.add(d);
            }
        }
        return busy.size();
    }

    private static List<Expiry> expiries(List<FleetDocument> docs, java.util.function.Predicate<FleetDocument> owner,
                                         LocalDate from, LocalDate to, LocalDate today) {
        Objects.requireNonNull(from);
        // Incluye los ya vencidos antes del rango: siguen siendo un problema de esa semana.
        return docs.stream().filter(owner).filter(d -> d.expiresAt() != null && !d.expiresAt().isAfter(to))
                .map(d -> new Expiry(d.type().label(), d.expiresAt(), d.expiresAt().isBefore(today)))
                .sorted(Comparator.comparing(Expiry::on)).toList();
    }

    private static Comparator<Slot> byStart() {
        return Comparator.comparing(Slot::from).thenComparing(Slot::kind);
    }
}
