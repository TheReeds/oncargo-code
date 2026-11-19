package pe.logyx.notification.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import pe.logyx.identity.application.UserAccounts;
import pe.logyx.identity.domain.model.Profile;
import pe.logyx.notification.application.port.out.EmailQueueRepository;
import pe.logyx.notification.application.port.out.EmailQueueRepository.QueuedEmail;
import pe.logyx.notification.application.port.out.EmailSettingsRepository;
import pe.logyx.notification.application.port.out.NotificationMailer;
import pe.logyx.notification.application.port.out.NotificationPreferenceRepository;
import pe.logyx.notification.domain.NotificationPreferencePolicy;
import pe.logyx.notification.domain.model.EmailSettings;
import pe.logyx.notification.domain.model.NotificationCategory;
import pe.logyx.shared.tenancy.SystemTx;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Envía los correos de aviso diferidos (V24, 2.2): los vencidos de {@code notification_email_queue} (fin del silencio o
 * hora del resumen) se agrupan en <b>un correo por persona</b> (uno solo = el aviso; varios = «Tienes N avisos» con la
 * lista). El destinatario se resuelve ahora: una persona desactivada, sin email, que apagó el correo o la categoría
 * después de encolarse, se marca como enviada y no recibe nada. Lo llama el job
 * {@code EmailQueueScheduler} ({@code logyx.notifications.email-queue.every}); en tests, a mano.
 */
@ApplicationScoped
public class EmailQueueService {

    static final int BATCH = 500;

    @Inject EmailQueueRepository queue;
    @Inject EmailSettingsRepository emailSettings;
    @Inject NotificationPreferenceRepository preferences;
    @Inject UserAccounts accounts;
    @Inject NotificationMailer mailer;
    @Inject SystemTx system;

    /** Envía lo vencido a {@code now}; devuelve cuántos correos salieron. */
    public int flushDue(OffsetDateTime now) {
        int total = 0;
        for (int i = 0; i < 20; i++) {
            int[] r = flushBatch(now);
            total += r[1];
            if (r[0] < BATCH) break;
        }
        return total;
    }

    /** [filas tomadas, correos enviados]. */
    // SystemTx: job sin usuario; la cola es de todas las organizaciones y logyx_app no tiene acceso (V24).
    private int[] flushBatch(OffsetDateTime now) {
        return system.call(() -> {
            List<QueuedEmail> due = queue.lockDue(now, BATCH);
            if (due.isEmpty()) return new int[]{0, 0};
            queue.markSent(due.stream().map(QueuedEmail::id).toList(), now);
            Map<UUID, List<QueuedEmail>> byPerson = new LinkedHashMap<>();
            due.forEach(q -> byPerson.computeIfAbsent(q.profileId(), k -> new ArrayList<>()).add(q));
            Map<UUID, Profile> people = accounts.byIds(byPerson.keySet());
            Map<UUID, EmailSettings> settings = emailSettings.forProfiles(byPerson.keySet());
            var prefs = preferences.forProfiles(byPerson.keySet());
            List<NotificationMailer.Email> emails = new ArrayList<>();
            byPerson.forEach((profileId, items) -> {
                Profile p = people.get(profileId);
                if (p == null || !p.canLogIn() || p.isDriver() || p.email() == null || p.email().isBlank()) return;
                EmailSettings s = settings.getOrDefault(profileId, EmailSettings.DEFAULT);
                if (!s.emailEnabled()) return;
                var effective = NotificationPreferencePolicy.effective(prefs.get(profileId));
                List<QueuedEmail> wanted = items.stream().filter(q -> effective.get(q.category()).email()).toList();
                if (wanted.isEmpty()) return;
                NotificationCategory first = wanted.getFirst().category();
                boolean oneCategory = wanted.stream().allMatch(q -> q.category() == first);
                emails.add(new NotificationMailer.Email(p.email(), p.fullName(), p.id(), oneCategory ? first : null,
                        wanted.stream().map(q -> new NotificationMailer.Item(q.title(), q.body(), q.category().label()))
                                .toList()));
            });
            mailer.queue(emails);
            return new int[]{due.size(), emails.size()};
        });
    }
}
