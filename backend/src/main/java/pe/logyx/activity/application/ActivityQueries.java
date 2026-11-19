package pe.logyx.activity.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.activity.application.port.out.ActivityReader;
import pe.logyx.activity.domain.model.ActivityEntry;
import pe.logyx.activity.domain.model.ActivitySubject;
import pe.logyx.activity.domain.model.Author;
import pe.logyx.shared.domain.BusinessException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autoría y rastro de acciones de un recurso (roadmap v2 1.8: «Publicada por Ana R.», quién aceptó, quién canceló).
 * Solo lectura de {@code created_by} y de {@code audit_log}, siempre bajo RLS ({@code @Transactional} con el JWT): cada
 * organización ve <b>sus</b> acciones y a <b>sus</b> personas; el operador ve todo. Sin SystemTx.
 */
@ApplicationScoped
@Transactional
public class ActivityQueries {

    static final int LIMIT = 100;

    @Inject ActivityReader reader;

    public record Activity(Optional<Author> createdBy, List<ActivityEntry> entries) {}

    public Activity of(ActivitySubject subject, UUID id) {
        if (!reader.visible(subject, id)) throw BusinessException.notFound(switch (subject) {
            case REQUEST -> "Solicitud";
            case BID -> "Oferta";
            case SHIPMENT -> "Envío";
            case TRIP -> "Viaje";
            case INCIDENT -> "Incidencia";
        });
        return new Activity(reader.author(subject, id), reader.trail(subject, id, LIMIT));
    }
}
