package pe.logyx.realtime.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.http.HttpServerResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import pe.logyx.realtime.application.RealtimeHub;
import pe.logyx.realtime.application.RealtimeStreamService;
import pe.logyx.realtime.domain.model.Subscriber;
import pe.logyx.shared.tenancy.Roles;

import java.time.Instant;
import java.util.Map;

/** Stream de eventos en tiempo real por SSE (ADR-0007). */
@Path("/api/v1")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.DRIVER, Roles.OPERATOR})
public class RealtimeResource {

    /** Envoltura de cada mensaje SSE ({@code data:}); el {@code id:} SSE es {@code seq}. */
    @Schema(name = "RealtimeStreamEvent", description = "Mensaje del stream SSE (campo data:, JSON). El id SSE es seq.")
    public record RealtimeStreamEvent(
            @Schema(description = "Tipo de evento: bid.placed, auction.updated, bid.accepted, bid.rejected, "
                    + "request.matched, counter_offer.updated, message.posted, shipment.status_changed, "
                    + "shipment.stop_updated, shipment.driver_assigned, incident.reported, incident.updated, "
                    + "notification.created, review.submitted, trip_booking.updated, trip.cancelled, "
                    + "smart_load.suggested, smart_load.accepted; control: stream.ready, stream.resync, stream.expired. "
                    + "Ver docs/api/CHANGELOG.md. Ignora los tipos desconocidos.") String type,
            @Schema(description = "Secuencia del evento (= id SSE, úsalo como Last-Event-ID); null en stream.expired")
            Long seq,
            @Schema(description = "Cuándo ocurrió (ISO-8601)") String occurredAt,
            @Schema(description = "Payload mínimo: ids, estados y contadores (nunca montos, nombres ni textos)")
            Map<String, Object> data) {}

    @Inject RealtimeStreamService service;
    @Inject RealtimeHub hub;
    @Inject JsonWebToken jwt;
    @Inject ObjectMapper json;

    @GET
    @Path("/events/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Blocking
    @Operation(summary = "Eventos en tiempo real (SSE)", description = """
            Stream text/event-stream autenticado. Cada conexión recibe solo los eventos dirigidos a su organización \
            (el conductor, solo los de sus envíos; el operador, los suyos y las incidencias). Payload mínimo (ids + \
            tipo + datos no sensibles): el cliente vuelve a pedir por la API lo que necesite. \
            Primero llega stream.ready (o stream.resync si no se pudo reanudar: recarga todo). Latido: comentario \
            ': ping' cada ~20 s. Reanudación: cabecera Last-Event-ID (o ?lastEventId=) con el último id recibido. \
            Al vencer el JWT llega stream.expired y se cierra: reconecta con un token nuevo. \
            Máximo 5 conexiones por usuario (429 LOGYX-RTM-001).""")
    @APIResponse(responseCode = "200", description = "Stream de eventos",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS,
                    schema = @Schema(implementation = RealtimeStreamEvent.class)))
    @APIResponse(responseCode = "401", description = "Sin token o token vencido")
    @APIResponse(responseCode = "429", description = "Demasiadas conexiones del usuario (LOGYX-RTM-001)")
    public void stream(@Context SseEventSink sink, @Context Sse sse, @Context HttpServerResponse response,
                       @Parameter(description = "Último id recibido (lo envía EventSource al reconectar)")
                       @HeaderParam("Last-Event-ID") String lastEventIdHeader,
                       @Parameter(description = "Alternativa a Last-Event-ID para clientes que reconectan a mano")
                       @QueryParam("lastEventId") String lastEventIdQuery) {
        Subscriber subscriber = service.currentSubscriber();
        Instant expires = Instant.ofEpochSecond(jwt.getExpirationTime());
        SseRealtimeConnection conn = new SseRealtimeConnection(sink, sse, json, subscriber, expires);
        hub.register(conn);   // 429 si excede el límite (antes de escribir nada)
        try {
            response.putHeader("X-Accel-Buffering", "no");
            Long after = parse(lastEventIdHeader != null && !lastEventIdHeader.isBlank() ? lastEventIdHeader : lastEventIdQuery);
            RealtimeStreamService.Replay replay = service.replay(subscriber, after);
            conn.start(replay.events(), replay.overflow(), replay.latestSeq());
        } catch (RuntimeException e) {
            hub.unregister(conn);
            conn.close("error");
            throw e;
        }
    }

    private static Long parse(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
