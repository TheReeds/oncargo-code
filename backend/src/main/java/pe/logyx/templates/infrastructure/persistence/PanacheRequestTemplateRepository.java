package pe.logyx.templates.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import pe.logyx.templates.application.port.out.RequestTemplateRepository;
import pe.logyx.templates.domain.model.RequestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheRequestTemplateRepository implements RequestTemplateRepository {

    @Override
    public Optional<RequestTemplate> findById(UUID id) {
        return RequestTemplateEntity.<RequestTemplateEntity>findByIdOptional(id).map(RequestTemplateEntity::toDomain);
    }

    @Override
    public List<RequestTemplate> list(UUID organizationId, boolean includeArchived, int max) {
        String where = includeArchived ? "organizationId = ?1" : "organizationId = ?1 and archivedAt is null";
        return RequestTemplateEntity.<RequestTemplateEntity>find(where
                        + " order by archivedAt nulls first, lastPublishedAt desc nulls last, name, id", organizationId)
                .page(0, max).list().stream().map(RequestTemplateEntity::toDomain).toList();
    }

    @Override
    public boolean activeNameTaken(UUID organizationId, String name, UUID exceptId) {
        return exceptId == null
                ? RequestTemplateEntity.count("organizationId = ?1 and archivedAt is null and lower(name) = ?2",
                        organizationId, name.toLowerCase()) > 0
                : RequestTemplateEntity.count("organizationId = ?1 and archivedAt is null and lower(name) = ?2 and id <> ?3",
                        organizationId, name.toLowerCase(), exceptId) > 0;
    }

    @Override
    public void save(RequestTemplate template) {
        RequestTemplateEntity e = RequestTemplateEntity.findById(template.id());
        if (e == null) {
            e = new RequestTemplateEntity();
            e.apply(template);
            e.persist();
        } else {
            e.apply(template);
        }
    }
}
