package pe.logyx.trip.infrastructure.web;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.ResponseStatus;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;
import pe.logyx.trip.application.*;
import pe.logyx.trip.domain.model.BookingStatus;
import pe.logyx.trip.infrastructure.web.TripDtos.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Viajes publicados (supply-side) y reservas de espacio. */
@Path("/api/v1")
public class TripResource {

    @Inject TripService trips;
    @Inject BookingService bookings;
    @Inject TripQueries queries;

    // ----- Transportista: sus viajes
    @POST
    @Path("/trips")
    @RolesAllowed(Roles.CARRIER)
    @ResponseStatus(201)
    public ApiResponse<TripView> publish(@Valid CreateTripRequest req) {
        return ApiResponse.of(TripView.of(trips.publish(req.toCommand())));
    }

    @GET
    @Path("/trips/mine")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<TripView>> mine(
            @Parameter(description = "Página 0-based. Si se omite page y size, se devuelven todos.")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20 cuando se pagina).")
            @QueryParam("size") Integer size) {
        if (page == null && size == null) return ApiResponse.of(queries.myTrips().stream().map(TripView::of).toList());
        return ApiResponse.of(queries.myTrips(PageRequest.of(page, size)).map(TripView::of));
    }

    @POST
    @Path("/trips/{id}/cancel")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<TripView> cancel(@PathParam("id") UUID id) {
        return ApiResponse.of(TripView.of(trips.cancel(id)));
    }

    @POST
    @Path("/trips/{id}/close")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<TripView> close(@PathParam("id") UUID id) {
        return ApiResponse.of(TripView.of(trips.close(id)));
    }

    @GET
    @Path("/trips/{id}/bookings")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<List<TripBookingView>> tripBookings(@PathParam("id") UUID id) {
        return ApiResponse.of(queries.bookingsOfTrip(id).stream().map(TripBookingView::of).toList());
    }

    // ----- PYME: buscar y reservar
    @GET
    @Path("/trips")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<TripView>> search(
            @Parameter(description = "Código de ciudad de origen (p. ej. LIMA).")
            @QueryParam("originCity") String originCity,
            @Parameter(description = "Código de ciudad de destino (p. ej. JULIACA).")
            @QueryParam("destinationCity") String destinationCity,
            @Parameter(description = "Salida desde esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("fromDate") @Schema(type = SchemaType.STRING, format = "date") String fromDate,
            @Parameter(description = "Salida hasta esta fecha, ISO (yyyy-MM-dd), inclusive, hora de Lima.")
            @QueryParam("toDate") @Schema(type = SchemaType.STRING, format = "date") String toDate,
            @Parameter(description = "Capacidad restante mínima en kg.")
            @QueryParam("minWeightKg") BigDecimal minWeightKg,
            @Parameter(description = "Página 0-based (por omisión 0).")
            @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).")
            @QueryParam("size") Integer size) {
        var filter = TripFilter.of(originCity, destinationCity, fromDate, toDate, minWeightKg);
        return ApiResponse.of(queries.search(filter, PageRequest.of(page, size)).map(TripView::of));
    }

    @POST
    @Path("/trips/{id}/bookings")
    @RolesAllowed(Roles.PYME)
    @ResponseStatus(201)
    public ApiResponse<TripBookingView> book(@PathParam("id") UUID id, @Valid CreateTripBookingRequest req) {
        return ApiResponse.of(TripBookingView.of(bookings.book(id, req.toCommand())));
    }

    @GET
    @Path("/trip-bookings/mine")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<List<TripBookingView>> myBookings(
            @Parameter(description = "Filtra por estado (PENDING, CONFIRMED, REJECTED, CANCELLED).")
            @QueryParam("status") String status) {
        BookingStatus s = null;
        if (status != null && !status.isBlank()) {
            try {
                s = BookingStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BusinessException.badRequest("LOGYX-VAL-001", "Estado inválido: " + status, "status");
            }
        }
        return ApiResponse.of(queries.myBookings(s).stream().map(TripBookingView::of).toList());
    }

    @POST
    @Path("/trip-bookings/{id}/cancel")
    @RolesAllowed(Roles.PYME)
    public ApiResponse<TripBookingView> cancelBooking(@PathParam("id") UUID id) {
        return ApiResponse.of(TripBookingView.of(bookings.cancelByRequester(id)));
    }

    // ----- Transportista: resolver reservas
    @POST
    @Path("/trip-bookings/{id}/confirm")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<TripBookingView> confirm(@PathParam("id") UUID id) {
        return ApiResponse.of(TripBookingView.of(bookings.confirm(id)));
    }

    @POST
    @Path("/trip-bookings/{id}/reject")
    @RolesAllowed(Roles.CARRIER)
    public ApiResponse<TripBookingView> reject(@PathParam("id") UUID id, @Valid RejectTripBookingRequest req) {
        return ApiResponse.of(TripBookingView.of(bookings.reject(id, req == null ? null : req.reason())));
    }

    // ----- Común
    @GET
    @Path("/trips/{id}")
    @RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
    public ApiResponse<TripView> detail(@PathParam("id") UUID id) {
        return ApiResponse.of(TripView.of(queries.detail(id)));
    }
}
