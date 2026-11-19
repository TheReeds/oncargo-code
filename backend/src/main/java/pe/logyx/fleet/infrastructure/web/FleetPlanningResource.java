package pe.logyx.fleet.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import pe.logyx.fleet.application.FleetPlanningService;
import pe.logyx.fleet.application.port.out.FleetPlanning;
import pe.logyx.fleet.domain.model.Vehicle;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.Roles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** Agenda de flota y asignación sugerida (roadmap 2.13). Solo transportista; solo lectura. */
@Path("/api/v1")
@RolesAllowed(Roles.CARRIER)
public class FleetPlanningResource {

    @Inject FleetPlanningService planning;

    /** {@code kind}: SHIPMENT, BID (oferta pendiente) o TRIP (viaje publicado). {@code driverId} solo en envíos. */
    public record SlotView(String kind, UUID refId, String label,
                           @Schema(type = SchemaType.STRING, format = "date") LocalDate from,
                           @Schema(type = SchemaType.STRING, format = "date") LocalDate to, UUID driverId) {
        static SlotView of(FleetPlanning.Slot s) {
            return new SlotView(s.kind().name(), s.refId(), s.label(), s.from(), s.to(), s.driverId());
        }
    }

    public record ExpiryView(String label, @Schema(type = SchemaType.STRING, format = "date") LocalDate on,
                             boolean expired) {}

    /** Un vehículo o conductor con sus compromisos y vencimientos del rango; {@code blocked}: mantenimiento o cuenta desactivada. */
    public record ScheduleRowView(UUID id, String label, String detail, boolean blocked, String blockedReason,
                                  List<SlotView> slots, List<ExpiryView> expiries) {
        static ScheduleRowView of(FleetPlanningService.Row r) {
            return new ScheduleRowView(r.id(), r.label(), r.detail(), r.blocked(), r.blockedReason(),
                    r.slots().stream().map(SlotView::of).toList(),
                    r.expiries().stream().map(e -> new ExpiryView(e.label(), e.on(), e.expired())).toList());
        }
    }

    public record ScheduleView(@Schema(type = SchemaType.STRING, format = "date") LocalDate from,
                               @Schema(type = SchemaType.STRING, format = "date") LocalDate to,
                               List<ScheduleRowView> vehicles, List<ScheduleRowView> drivers) {}

    public record DriverOptionView(UUID driverId, String name, boolean active, boolean free, boolean assignable,
                                   int workloadDays, Double distanceKm, boolean current) {}

    public record VehicleOptionView(UUID vehicleId, String plate, Vehicle.Type type, boolean eligible,
                                    String blockerCode, String blockerMessage, boolean current) {}

    public record ShipmentWindowView(UUID shipmentId, String routeLabel,
                                     @Schema(type = SchemaType.STRING, format = "date") LocalDate from,
                                     @Schema(type = SchemaType.STRING, format = "date") LocalDate to,
                                     BigDecimal weightKg, UUID vehicleId, UUID driverId, boolean assignable) {}

    public record SuggestionsView(ShipmentWindowView shipment, List<DriverOptionView> drivers,
                                  List<VehicleOptionView> vehicles) {}

    @GET
    @Path("/fleet/schedule")
    @Operation(summary = "2.13: calendario de la flota de 1 a 14 días: por vehículo y conductor, con envíos, ofertas "
            + "pendientes, viajes y vencimientos de documentos",
            description = "from y to inclusive (yyyy-MM-dd); más de 14 días da 400 LOGYX-FLT-011. Cada compromiso usa la "
                    + "ventana recojo + ⌈km/500⌉ días de ResourceSchedulePolicy. Solo mi organización (RLS).")
    public ApiResponse<ScheduleView> schedule(
            @QueryParam("from") @Schema(type = SchemaType.STRING, format = "date") String from,
            @QueryParam("to") @Schema(type = SchemaType.STRING, format = "date") String to) {
        var s = planning.schedule(date(from, "from"), date(to, "to"));
        return ApiResponse.of(new ScheduleView(s.from(), s.to(), s.vehicles().stream().map(ScheduleRowView::of).toList(),
                s.drivers().stream().map(ScheduleRowView::of).toList()));
    }

    @GET
    @Path("/fleet/assignment-suggestions")
    @Operation(summary = "2.13: conductores libres en las fechas del envío (por cercanía al recojo y carga de trabajo) y "
            + "vehículos aptos",
            description = "Para asignar con PUT /shipments/{id}/assignment. Un conductor es assignable si su cuenta está "
                    + "activa y no tiene otro envío solapado; un vehículo es eligible con las mismas reglas de ofertar "
                    + "(RN02 por fecha, agenda y documentos).")
    public ApiResponse<SuggestionsView> suggestions(@QueryParam("shipmentId") UUID shipmentId) {
        if (shipmentId == null) throw BusinessException.badRequest("LOGYX-VAL-001", "Indica shipmentId", "shipmentId");
        var s = planning.suggest(shipmentId);
        var p = s.shipment();
        return ApiResponse.of(new SuggestionsView(
                new ShipmentWindowView(p.shipmentId(), p.routeLabel(), p.from(), p.to(), p.weightKg(), p.vehicleId(),
                        p.driverId(), p.assignable()),
                s.drivers().stream().map(d -> new DriverOptionView(d.driverId(), d.name(), d.active(), d.free(),
                        d.assignable(), d.workloadDays(), d.distanceKm(), d.current())).toList(),
                s.vehicles().stream().map(v -> new VehicleOptionView(v.vehicleId(), v.plate(), v.type(), v.eligible(),
                        v.blockerCode(), v.blockerMessage(), v.current())).toList()));
    }

    private static LocalDate date(String v, String field) {
        if (v == null || v.isBlank()) throw BusinessException.badRequest("LOGYX-VAL-001", "Indica " + field, field);
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Fecha inválida (yyyy-MM-dd)", field);
        }
    }
}
