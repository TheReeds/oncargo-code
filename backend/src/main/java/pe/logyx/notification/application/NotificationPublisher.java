package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.notification.application.event.NotificationsPublished;
import pe.logyx.notification.application.port.out.EmailQueueRepository;
import pe.logyx.notification.application.port.out.EmailSettingsRepository;
import pe.logyx.notification.application.port.out.NotificationMailer;
import pe.logyx.notification.application.port.out.NotificationPreferenceRepository;
import pe.logyx.notification.application.port.out.NotificationRepository;
import pe.logyx.notification.application.port.out.ResourceAuthors;
import pe.logyx.notification.domain.EmailDeliveryPolicy;
import pe.logyx.notification.domain.NotificationPreferencePolicy;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.notification.domain.model.Notification;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.notification.domain.model.NotificationPreference;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Inserta notificaciones. SystemTx justificado (ADR-0004): casi siempre el destinatario es OTRA organización que
 * la que actúa, y logyx_app no tiene política de INSERT sobre notifications (V8). Se invoca desde los observadores
 * {@code @Outboxed}: se une a la transacción de sistema del despacho del outbox, así que las notificaciones y la
 * marca {@code DONE} del evento se confirman juntas. Un fallo se propaga para que el outbox reintente.
 *
 * <p><b>Por persona (V16).</b> Cada borrador va a toda una organización ({@link Notification#to}) o a una persona
 * ({@link Notification#toPerson}). El de organización se expande en una copia por persona que recibe avisos
 * ({@link UserAccounts#noticeRecipients}: activas, sin invitación pendiente ni conductores). Para cada persona se
 * respetan sus preferencias de la categoría ({@link NotificationPreferencePolicy}): in-app crea la fila; correo encola
 * el envío (tras el commit). Si el recurso tiene autor en esa organización ({@link ResourceAuthors}), su copia lleva
 * {@code mine = true} («Tuyo»).
 *
 * <p><b>Correo según {@code user_preferences} (V24, 2.2).</b> Si la categoría va por correo, {@link EmailDeliveryPolicy}
 * decide con las preferencias de correo de la persona: no sale (correo desactivado), sale ya, o queda en
 * {@code notification_email_queue} hasta el fin del silencio o la hora del resumen diario (lo envía
 * {@link EmailQueueService}).
 *
 * <p>Luego dispara {@link NotificationsPublished} (síncrono, misma transacción) con el contador de no leídas de cada
 * persona: el tiempo real (ADR-0007) lo proyecta como {@code notification.created} dirigido a esa persona.
 */
@ApplicationScoped
public class NotificationPublisher {

    @Inject NotificationRepository notifications;
    @Inject NotificationPreferenceRepository preferences;
    @Inject ResourceAuthors authors;
    @Inject NotificationMailer mailer;
    @Inject EmailSettingsRepository emailSettings;
    @Inject EmailQueueRepository emailQueue;
    @Inject UserAccounts accounts;
    @Inject SystemTx system;
    @Inject Event<NotificationsPublished> published;

    public void publish(List<Notification> list) {
        if (list.isEmpty()) return;
        system.run(() -> {
            ZonedDateTime now = ZonedDateTime.now(EmailDeliveryPolicy.LIMA);
            Map<UUID, List<Profile>> members = new HashMap<>();
            List<Notification> rows = new ArrayList<>();
            List<NotificationMailer.Email> emails = new ArrayList<>();
            for (Notification draft : list) {
                List<Profile> people = members.computeIfAbsent(draft.orgId(), accounts::noticeRecipients);
                List<Profile> targets = draft.isForWholeOrganization() ? people
                        : people.stream().filter(p -> p.id().equals(draft.profileId())).toList();
                if (targets.isEmpty()) continue;
                UUID author = draft.isForWholeOrganization() && draft.relatedType() != null && draft.relatedId() != null
                        ? authors.authorIn(draft.orgId(), draft.relatedType(), draft.relatedId()).orElse(null) : null;
                var prefs = preferences.forProfiles(targets.stream().map(Profile::id).toList());
                NotificationCategory category = draft.category();
                List<Profile> byEmail = new ArrayList<>();
                for (Profile p : targets) {
                    NotificationPreference pref = NotificationPreferencePolicy.effective(prefs.get(p.id())).get(category);
                    if (pref.inApp()) {
                        rows.add(draft.addressedTo(p.id(),
                                draft.isForWholeOrganization() ? Objects.equals(author, p.id()) : draft.mine()));
                    }
                    if (pref.email() && p.email() != null && !p.email().isBlank()) byEmail.add(p);
                }
                if (byEmail.isEmpty()) continue;
                Map<UUID, EmailSettings> settings = emailSettings.forProfiles(byEmail.stream().map(Profile::id).toList());
                for (Profile p : byEmail) {
                    var decision = EmailDeliveryPolicy.decide(settings.get(p.id()), now);
                    switch (decision.kind()) {
                        case NOW -> emails.add(NotificationMailer.Email.single(p.email(), p.fullName(), p.id(), category,
                                draft.title(), draft.body()));
                        case DEFER -> emailQueue.enqueue(new EmailQueueRepository.QueuedEmail(UUID.randomUUID(), p.id(),
                                draft.orgId(), category, draft.title(), draft.body(), decision.reason(),
                                decision.sendAfter().toOffsetDateTime(), OffsetDateTime.now()));
                        case SKIP -> { }
                    }
                }
            }
            rows.forEach(notifications::insert);
            if (!emails.isEmpty()) mailer.queue(emails);
            if (rows.isEmpty()) return;
            Map<UUID, Long> unread = new HashMap<>();
            List<NotificationsPublished.Item> items = rows.stream().map(n -> new NotificationsPublished.Item(n.id(),
                    n.orgId(), n.type().name(), n.relatedType() == null ? null : n.relatedType().name(), n.relatedId(),
                    unread.computeIfAbsent(n.profileId(), notifications::countUnread), n.profileId(), n.mine())).toList();
            published.fire(new NotificationsPublished(items));
        });
    }
}
