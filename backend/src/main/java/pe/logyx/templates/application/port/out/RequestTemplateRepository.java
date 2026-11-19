package pe.logyx.templates.application.port.out;

import pe.logyx.templates.domain.model.RequestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestTemplateRepository {
    Optional<RequestTemplate> findById(UUID id);

    /** De la organización: la publicada más recientemente primero (las nunca publicadas, por nombre). */
    List<RequestTemplate> list(UUID organizationId, boolean includeArchived, int max);

    /** ¿Ya hay una plantilla activa con ese nombre (sin distinguir mayúsculas) en la organización? */
    boolean activeNameTaken(UUID organizationId, String name, UUID exceptId);

    void save(RequestTemplate template);
}
