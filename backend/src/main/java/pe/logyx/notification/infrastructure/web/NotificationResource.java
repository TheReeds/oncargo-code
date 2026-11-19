package pe.logyx.notification.infrastructure.web;

import pe.logyx.notification.application.NotificationService.CategoryPreference;
import pe.logyx.notification.domain.model.EmailMode;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.notification.domain.model.NotificationTarget;
import pe.logyx.notification.domain.model.NotificationType;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import pe.logyx.notification.application.NotificationService;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.shared.api.ApiResponse;
import pe.logyx.shared.domain.PageRequest;
import pe.logyx.shared.tenancy.Roles;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Centro de avisos de la persona (V16: cada uno tiene su leído; los conductores no tienen centro propio en el MVP) y
 * sus preferencias de aviso.
 */
@Path("/api/v1/notifications")
@RolesAllowed({Roles.PYME, Roles.CARRIER, Roles.OPERATOR})
public class NotificationResource {

    /** {@code mine} (V16): el aviso es de algo que tú creaste (tu solicitud, tu oferta): marca «Tuyo». */
    public record NotificationView(UUID id, NotificationType type, String title, String body,
                                   NotificationTarget relatedType, UUID relatedId, boolean read,
                                   OffsetDateTime readAt, OffsetDateTime createdAt, boolean mine) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.id(), n.type(), n.title(), n.body(), n.relatedType(), n.relatedId(),
                    n.isRead(), n.readAt(), n.createdAt(), n.mine());
        }
    }

    public record UnreadCount(long unread) {}

    public record MarkedRead(int updated) {}

    /** Preferencia de una categoría de avisos ({@code inAppLocked}: el canal in-app no se puede apagar). */
    public record NotificationPreferenceView(NotificationCategory category, String label, String description,
                                             boolean inApp, boolean email, boolean inAppLocked) {
        static NotificationPreferenceView of(CategoryPreference p) {
            return new NotificationPreferenceView(p.category(), p.category().label(), p.category().description(),
                    p.inApp(), p.email(), p.inAppLocked());
        }
    }

    public record NotificationPreferenceChange(@NotNull NotificationCategory category, @NotNull Boolean inApp,
                                               @NotNull Boolean email) {}

    public record UpdateNotificationPreferences(@NotEmpty @Size(max = 20) List<@Valid @NotNull NotificationPreferenceChange> preferences) {}

    /**
     * V24 (2.2): cómo llegan tus correos de avisos. {@code quietFrom}/{@code quietTo} en hora de Lima (pueden cruzar la
     * medianoche; ambos nulos = sin silencio). {@code unsubscribedAt}: cuándo te diste de baja de todo con el enlace.
     */
    public record EmailSettingsView(boolean emailEnabled, EmailMode mode, int digestHour, LocalTime quietFrom,
                                    LocalTime quietTo, OffsetDateTime unsubscribedAt) {
        static EmailSettingsView of(EmailSettings s) {
            return new EmailSettingsView(s.emailEnabled(), s.mode(), s.digestHour(), s.quietFrom(), s.quietTo(),
                    s.unsubscribedAt());
        }
    }

    public record UpdateEmailSettings(
            @NotNull Boolean emailEnabled,
            @NotNull EmailMode mode,
            @Schema(description = "Hora del resumen diario (0–23, hora de Lima). Por omisión 7") @Min(0) @Max(23) Integer digestHour,
            @Schema(description = "Inicio del horario de silencio (HH:mm, hora de Lima). Nulo con quietTo nulo = sin silencio")
            LocalTime quietFrom,
            @Schema(description = "Fin del horario de silencio (HH:mm). Puede ser menor que quietFrom: cruza la medianoche")
            LocalTime quietTo) {}

    @Inject NotificationService service;

    @GET
    @Path("/email-settings")
    @Operation(summary = "Mis preferencias de correo (V24)", description = "Correo activo, inmediato o resumen diario "
            + "(hora de Lima) y horario de silencio. Se aplican después de la matriz categoría × canal.")
    public ApiResponse<EmailSettingsView> emailSettings() {
        return ApiResponse.of(EmailSettingsView.of(service.emailSettings()));
    }

    @PUT
    @Path("/email-settings")
    @Operation(summary = "Cambiar mis preferencias de correo (V24)", description = "Cualquier rol (también Lector). "
            + "Con emailEnabled=false no sale ningún correo de avisos (los de cuenta sí). DAILY = un resumen al día a "
            + "digestHour; durante el silencio los correos esperan hasta quietTo. 400 LOGYX-NTF-004 si el silencio está "
            + "incompleto o empieza y termina a la misma hora.")
    public ApiResponse<EmailSettingsView> updateEmailSettings(@Valid @NotNull UpdateEmailSettings req) {
        return ApiResponse.of(EmailSettingsView.of(service.updateEmailSettings(req.emailEnabled(), req.mode(),
                req.digestHour() == null ? 7 : req.digestHour(), req.quietFrom(), req.quietTo())));
    }

    @GET
    @Operation(summary = "Mis avisos, recientes primero (siempre paginado)",
            description = "V16: avisos por persona; marcar uno como leído no cambia el de tus compañeros.")
    public ApiResponse<List<NotificationView>> list(
            @Parameter(description = "true = solo no leídas.") @QueryParam("unread") Boolean unread,
            @Parameter(description = "true = solo los de algo que tú creaste («Tuyo»).") @QueryParam("mine") Boolean mine,
            @Parameter(description = "Página 0-based (por omisión 0).") @QueryParam("page") Integer page,
            @Parameter(description = "Tamaño de página (1-100, por omisión 20).") @QueryParam("size") Integer size) {
        return ApiResponse.of(service.list(Boolean.TRUE.equals(unread), Boolean.TRUE.equals(mine), PageRequest.of(page, size))
                .map(NotificationView::of));
    }

    @GET
    @Path("/unread-count")
    @Operation(summary = "Cuántos avisos tengo sin leer (solo los míos)")
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

    @GET
    @Path("/preferences")
    @Operation(summary = "Mis preferencias de aviso", description = "Categorías que aplican a tu tipo de cuenta, con "
            + "los canales in-app y correo (valores por defecto si nunca las cambiaste).")
    public ApiResponse<List<NotificationPreferenceView>> preferences() {
        return ApiResponse.of(service.preferences().stream().map(NotificationPreferenceView::of).toList());
    }

    @PUT
    @Path("/preferences")
    @Operation(summary = "Cambiar mis preferencias de aviso", description = "Cualquier rol (también Lector). Solo "
            + "cambian las categorías enviadas. 400 LOGYX-NTF-001 si se apaga el in-app de «Cuenta y accesos»; "
            + "400 LOGYX-NTF-002 si la categoría no aplica a tu tipo de cuenta.")
    public ApiResponse<List<NotificationPreferenceView>> updatePreferences(@Valid @NotNull UpdateNotificationPreferences req) {
        return ApiResponse.of(service.updatePreferences(req.preferences().stream()
                .map(c -> new NotificationPreference(c.category(), c.inApp(), c.email())).toList())
                .stream().map(NotificationPreferenceView::of).toList());
    }
}
