package pe.logyx.activity.application.port.out;

import pe.logyx.activity.domain.model.ActivityEntry;
import pe.logyx.activity.domain.model.ActivitySubject;
import pe.logyx.activity.domain.model.Author;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lectura bajo RLS de la auditoría y de las columnas de autoría (nunca escribe). */
public interface ActivityReader {
    /** false si quien mira no ve el recurso (RLS). */
    boolean visible(ActivitySubject subject, UUID id);
    Optional<Author> author(ActivitySubject subject, UUID id);
    /** Más recientes primero. */
    List<ActivityEntry> trail(ActivitySubject subject, UUID id, int limit);
}
