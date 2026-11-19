package pe.logyx.places.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import pe.logyx.catalog.domain.City;
import pe.logyx.places.application.port.out.SavedPlaceRepository;
import pe.logyx.places.domain.model.PlaceKind;
import pe.logyx.places.domain.model.SavedPlace;
import pe.logyx.shared.audit.AuditService;
import pe.logyx.shared.domain.BusinessException;
import pe.logyx.shared.tenancy.TenantContext;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Libreta de direcciones de la organización: alta, edición, archivo y consulta. La organización sale del JWT; la RLS
 * ({@code saved_places}) y el filtro explícito por organización la protegen por duplicado. VIEWER solo lee (lo aplica
 * {@code AccountAccessFilter}: denegación por defecto de escrituras).
 */
@ApplicationScoped
@Transactional
public class PlaceService {

    public static final int MAX_LIST = 500;

    @Inject SavedPlaceRepository places;
    @Inject AuditService audit;
    @Inject TenantContext tenant;

    public List<SavedPlace> list(String city, String kind, String q, boolean includeArchived) {
        City c = city == null || city.isBlank() ? null : City.require(city);
        PlaceKind k = kind == null || kind.isBlank() ? null : parseKind(kind);
        return places.search(tenant.orgId(), c, k, q == null || q.isBlank() ? null : q.trim(), includeArchived, MAX_LIST);
    }

    public SavedPlace get(UUID id) {
        return owned(id);
    }

    public SavedPlace create(SavedPlace.Data data) {
        SavedPlace p = SavedPlace.create(tenant.orgId(), tenant.profileId(), data);
        places.save(p);
        audit.record(tenant.profileId(), tenant.orgId(), "PLACE_CREATED", "saved_place", p.id(), null);
        return p;
    }

    public SavedPlace update(UUID id, SavedPlace.Data data) {
        SavedPlace p = owned(id);
        p.update(data);
        places.save(p);
        audit.record(tenant.profileId(), tenant.orgId(), "PLACE_UPDATED", "saved_place", p.id(), null);
        return p;
    }

    public SavedPlace archive(UUID id) {
        SavedPlace p = owned(id);
        p.archive();
        places.save(p);
        audit.record(tenant.profileId(), tenant.orgId(), "PLACE_ARCHIVED", "saved_place", p.id(), null);
        return p;
    }

    public SavedPlace restore(UUID id) {
        SavedPlace p = owned(id);
        p.restore();
        places.save(p);
        audit.record(tenant.profileId(), tenant.orgId(), "PLACE_RESTORED", "saved_place", p.id(), null);
        return p;
    }

    /**
     * Para otros módulos (marketplace): el usuario publicó una solicitud desde estas direcciones, así que pasan al
     * principio de la lista. Ignora ids nulos y ajenos (la RLS y el filtro por organización los descartan).
     */
    public void markUsed(Collection<UUID> ids) {
        List<UUID> clean = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!clean.isEmpty()) places.touch(tenant.orgId(), clean, OffsetDateTime.now());
    }

    private SavedPlace owned(UUID id) {
        return places.findById(id).filter(p -> p.organizationId().equals(tenant.orgId()))
                .orElseThrow(() -> BusinessException.notFound("Dirección"));
    }

    private static PlaceKind parseKind(String kind) {
        try {
            return PlaceKind.valueOf(kind.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("LOGYX-VAL-001", "Tipo inválido: use PICKUP, DELIVERY o BOTH", "kind");
        }
    }
}
