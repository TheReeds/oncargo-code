package pe.logyx.notification.infrastructure.web;

import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.notification.application.NotificationService;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Centro de notificaciones de la organización (los conductores no tienen centro propio en el MVP). */
@Path("/api/v1/notifications")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class NotificationResource {

    public record NotificationView(UUID id, NotificationType type, String title, String body,
                                   NotificationTarget relatedType, UUID relatedId, boolean read,
                                   OffsetDateTime readAt, OffsetDateTime createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.id(), n.type(), n.title(), n.body(), n.relatedType(), n.relatedId(),
                    n.isRead(), n.readAt(), n.createdAt());
        }
    }

    public record UnreadCount(long unread) {}

    public record MarkedRead(int updated) {}

    @Inject NotificationService service;

    @GET
    @Operation(summary = "Notificaciones de mi organización, recientes primero (siempre paginado)")
    public ApiResponse<List<NotificationView>> list(
            @Parameter(description = "true = solo no leídas.") @QueryParam("unread") Boolean unread,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        return ApiResponse.of(service.list(Boolean.TRUE.equals(unread), PageRequest.of(page, size))
                .map(NotificationView::of));
    }

    @GET
    @Path("/unread-count")
    public ApiResponse<UnreadCount> unreadCount() {
        return ApiResponse.of(new UnreadCount(service.unreadCount()));
    }

    @POST
    @Path("/{id}/read")
    public ApiResponse<NotificationView> markRead(@PathParam("id") UUID id) {
        return ApiResponse.of(NotificationView.of(service.markRead(id)));
    }

    @POST
    @Path("/read-all")
    public ApiResponse<MarkedRead> markAllRead() {
        return ApiResponse.of(new MarkedRead(service.markAllRead()));
    }
}
